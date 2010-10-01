package org.apache.lucene.store;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.ByteArrayOutputStream;

import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.util.English;

/**
 * JUnit testcase to test RAMDirectory. RAMDirectory itself is used in many testcases,
 * but not one of them uses an different constructor other than the default constructor.
 */
public class TestRAMDirectory extends LuceneTestCase {
  
  private File indexDir = null;
  
  // add enough document so that the index will be larger than RAMDirectory.READ_BUFFER_SIZE
  private final int docsToAdd = 500;
  
  // setup the index
  @Override
  protected void setUp () throws Exception {
    super.setUp();
    indexDir = new File(TEMP_DIR, "RAMDirIndex");
    
    Directory dir = FSDirectory.open(indexDir);
    IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(
        TEST_VERSION_CURRENT, new MockAnalyzer()).setOpenMode(OpenMode.CREATE));
    // add some documents
    Document doc = null;
    for (int i = 0; i < docsToAdd; i++) {
      doc = new Document();
      doc.add(new Field("content", English.intToEnglish(i).trim(), Field.Store.YES, Field.Index.NOT_ANALYZED));
      writer.addDocument(doc);
    }
    assertEquals(docsToAdd, writer.maxDoc());
    writer.close();
    dir.close();
  }
  
  public void testRAMDirectory () throws IOException {
    
    Directory dir = FSDirectory.open(indexDir);
    MockDirectoryWrapper ramDir = new MockDirectoryWrapper(new RAMDirectory(dir));
    
    // close the underlaying directory
    dir.close();
    
    // Check size
    assertEquals(ramDir.sizeInBytes(), ramDir.getRecomputedSizeInBytes());
    
    // open reader to test document count
    IndexReader reader = IndexReader.open(ramDir, true);
    assertEquals(docsToAdd, reader.numDocs());
    
    // open search zo check if all doc's are there
    IndexSearcher searcher = new IndexSearcher(reader);
    
    // search for all documents
    for (int i = 0; i < docsToAdd; i++) {
      Document doc = searcher.doc(i);
      assertTrue(doc.getField("content") != null);
    }

    // cleanup
    reader.close();
    searcher.close();
  }
  
  private final int numThreads = 10;
  private final int docsPerThread = 40;
  
  public void testRAMDirectorySize() throws IOException, InterruptedException {
      
    Directory dir = FSDirectory.open(indexDir);
    final MockDirectoryWrapper ramDir = new MockDirectoryWrapper(new RAMDirectory(dir));
    dir.close();
    
    final IndexWriter writer = new IndexWriter(ramDir, new IndexWriterConfig(
        TEST_VERSION_CURRENT, new MockAnalyzer()).setOpenMode(OpenMode.APPEND));
    writer.optimize();
    
    assertEquals(ramDir.sizeInBytes(), ramDir.getRecomputedSizeInBytes());
    
    Thread[] threads = new Thread[numThreads];
    for (int i=0; i<numThreads; i++) {
      final int num = i;
      threads[i] = new Thread(){
        @Override
        public void run() {
          for (int j=1; j<docsPerThread; j++) {
            Document doc = new Document();
            doc.add(new Field("sizeContent", English.intToEnglish(num*docsPerThread+j).trim(), Field.Store.YES, Field.Index.NOT_ANALYZED));
            try {
              writer.addDocument(doc);
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        }
      };
    }
    for (int i=0; i<numThreads; i++)
      threads[i].start();
    for (int i=0; i<numThreads; i++)
      threads[i].join();

    writer.optimize();
    assertEquals(ramDir.sizeInBytes(), ramDir.getRecomputedSizeInBytes());
    
    writer.close();
  }


  public void testSerializable() throws IOException {
    Directory dir = new RAMDirectory();
    ByteArrayOutputStream bos = new ByteArrayOutputStream(1024);
    assertEquals("initially empty", 0, bos.size());
    ObjectOutput out = new ObjectOutputStream(bos);
    int headerSize = bos.size();
    out.writeObject(dir);
    out.close();
    assertTrue("contains more then just header", headerSize < bos.size());
  } 

  @Override
  protected void tearDown() throws Exception {
    // cleanup 
    if (indexDir != null && indexDir.exists()) {
      rmDir (indexDir);
    }
    super.tearDown();
  }

  // LUCENE-1196
  public void testIllegalEOF() throws Exception {
    RAMDirectory dir = new RAMDirectory();
    IndexOutput o = dir.createOutput("out");
    byte[] b = new byte[1024];
    o.writeBytes(b, 0, 1024);
    o.close();
    IndexInput i = dir.openInput("out");
    i.seek(1024);
    i.close();
    dir.close();
  }
  
  private void rmDir(File dir) {
    File[] files = dir.listFiles();
    for (int i = 0; i < files.length; i++) {
      files[i].delete();
    }
    dir.delete();
  }
}
