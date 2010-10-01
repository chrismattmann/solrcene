package org.apache.lucene.index;

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
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import junit.framework.TestSuite;
import junit.textui.TestRunner;

import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.index.codecs.CodecProvider;


/** JUnit adaptation of an older test case DocTest. */
public class TestDoc extends LuceneTestCase {

    /** Main for running test case by itself. */
    public static void main(String args[]) {
        TestRunner.run (new TestSuite(TestDoc.class));
    }


    private File workDir;
    private File indexDir;
    private LinkedList<File> files;


    /** Set the test case. This test case needs
     *  a few text files created in the current working directory.
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        workDir = new File(TEMP_DIR,"TestDoc");
        workDir.mkdirs();

        indexDir = new File(workDir, "testIndex");
        indexDir.mkdirs();

        Directory directory = FSDirectory.open(indexDir);
        directory.close();

        files = new LinkedList<File>();
        files.add(createOutput("test.txt",
            "This is the first test file"
        ));

        files.add(createOutput("test2.txt",
            "This is the second test file"
        ));
    }

    private File createOutput(String name, String text) throws IOException {
        FileWriter fw = null;
        PrintWriter pw = null;

        try {
            File f = new File(workDir, name);
            if (f.exists()) f.delete();

            fw = new FileWriter(f);
            pw = new PrintWriter(fw);
            pw.println(text);
            return f;

        } finally {
            if (pw != null) pw.close();
            if (fw != null) fw.close();
        }
    }


    /** This test executes a number of merges and compares the contents of
     *  the segments created when using compound file or not using one.
     *
     *  TODO: the original test used to print the segment contents to System.out
     *        for visual validation. To have the same effect, a new method
     *        checkSegment(String name, ...) should be created that would
     *        assert various things about the segment.
     */
    public void testIndexAndMerge() throws Exception {
      StringWriter sw = new StringWriter();
      PrintWriter out = new PrintWriter(sw, true);
      Random random = newRandom();
      
      Directory directory = FSDirectory.open(indexDir);
      IndexWriter writer = new IndexWriter(directory, newIndexWriterConfig(random,
        TEST_VERSION_CURRENT, new MockAnalyzer())
                                           .setOpenMode(OpenMode.CREATE).setMaxBufferedDocs(-1));
      ((LogMergePolicy) writer.getMergePolicy()).setMergeFactor(10);

      SegmentInfo si1 = indexDoc(writer, "test.txt");
      printSegment(out, si1);

      SegmentInfo si2 = indexDoc(writer, "test2.txt");
      printSegment(out, si2);
      writer.close();

      SegmentInfo siMerge = merge(si1, si2, "merge", false);
      printSegment(out, siMerge);

      SegmentInfo siMerge2 = merge(si1, si2, "merge2", false);
      printSegment(out, siMerge2);

      SegmentInfo siMerge3 = merge(siMerge, siMerge2, "merge3", false);
      printSegment(out, siMerge3);
      
      directory.close();
      out.close();
      sw.close();
      String multiFileOutput = sw.getBuffer().toString();
      //System.out.println(multiFileOutput);

      sw = new StringWriter();
      out = new PrintWriter(sw, true);

      directory = FSDirectory.open(indexDir);
      writer = new IndexWriter(directory, newIndexWriterConfig(random,
        TEST_VERSION_CURRENT, new MockAnalyzer())
                               .setOpenMode(OpenMode.CREATE).setMaxBufferedDocs(-1));
      ((LogMergePolicy) writer.getMergePolicy()).setMergeFactor(10);

      si1 = indexDoc(writer, "test.txt");
      printSegment(out, si1);

      si2 = indexDoc(writer, "test2.txt");
      printSegment(out, si2);
      writer.close();

      siMerge = merge(si1, si2, "merge", true);
      printSegment(out, siMerge);

      siMerge2 = merge(si1, si2, "merge2", true);
      printSegment(out, siMerge2);

      siMerge3 = merge(siMerge, siMerge2, "merge3", true);
      printSegment(out, siMerge3);
      
      directory.close();
      out.close();
      sw.close();
      String singleFileOutput = sw.getBuffer().toString();

      assertEquals(multiFileOutput, singleFileOutput);
   }

   private SegmentInfo indexDoc(IndexWriter writer, String fileName)
   throws Exception
   {
      File file = new File(workDir, fileName);
      Document doc = new Document();
      doc.add(new Field("contents", new FileReader(file)));
      writer.addDocument(doc);
      writer.commit();
      return writer.newestSegment();
   }


   private SegmentInfo merge(SegmentInfo si1, SegmentInfo si2, String merged, boolean useCompoundFile)
   throws Exception {
      SegmentReader r1 = SegmentReader.get(true, si1, IndexReader.DEFAULT_TERMS_INDEX_DIVISOR);
      SegmentReader r2 = SegmentReader.get(true, si2, IndexReader.DEFAULT_TERMS_INDEX_DIVISOR);

      SegmentMerger merger = new SegmentMerger(si1.dir, IndexWriter.DEFAULT_TERM_INDEX_INTERVAL, merged, null, CodecProvider.getDefault(), null);

      merger.add(r1);
      merger.add(r2);
      merger.merge();
      merger.closeReaders();
      
      final SegmentInfo info = new SegmentInfo(merged, si1.docCount + si2.docCount, si1.dir,
                                               useCompoundFile, -1, null, false, merger.hasProx(), merger.getCodec());
      
      if (useCompoundFile) {
        List<String> filesToDelete = merger.createCompoundFile(merged + ".cfs", info);
        for (final String fileToDelete : filesToDelete) 
          si1.dir.deleteFile(fileToDelete);
      }

      return info;
   }


   private void printSegment(PrintWriter out, SegmentInfo si)
   throws Exception {
      SegmentReader reader = SegmentReader.get(true, si, IndexReader.DEFAULT_TERMS_INDEX_DIVISOR);

      for (int i = 0; i < reader.numDocs(); i++)
        out.println(reader.document(i));

      FieldsEnum fis = reader.fields().iterator();
      String field = fis.next();
      while(field != null)  {
        TermsEnum tis = fis.terms();
        while(tis.next() != null) {

          out.print("  term=" + field + ":" + tis.term());
          out.println("    DF=" + tis.docFreq());

          DocsAndPositionsEnum positions = tis.docsAndPositions(reader.getDeletedDocs(), null);

          while (positions.nextDoc() != positions.NO_MORE_DOCS) {
            out.print(" doc=" + positions.docID());
            out.print(" TF=" + positions.freq());
            out.print(" pos=");
            out.print(positions.nextPosition());
            for (int j = 1; j < positions.freq(); j++)
              out.print("," + positions.nextPosition());
            out.println("");
          }
        }
        field = fis.next();
      }
      reader.close();
    }
}
