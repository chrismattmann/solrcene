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

import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.store.BufferedIndexInput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.codecs.CodecProvider;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.Collection;
import java.util.Random;

public class TestSegmentMerger extends LuceneTestCase {
  //The variables for the new merged segment
  private Directory mergedDir;
  private String mergedSegment = "test";
  //First segment to be merged
  private Directory merge1Dir;
  private Document doc1 = new Document();
  private SegmentReader reader1 = null;
  //Second Segment to be merged
  private Directory merge2Dir;
  private Document doc2 = new Document();
  private SegmentReader reader2 = null;
  

  public TestSegmentMerger(String s) {
    super(s);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Random random = newRandom();
    mergedDir = newDirectory(random);
    merge1Dir = newDirectory(random);
    merge2Dir = newDirectory(random);
    DocHelper.setupDoc(doc1);
    SegmentInfo info1 = DocHelper.writeDoc(merge1Dir, doc1);
    DocHelper.setupDoc(doc2);
    SegmentInfo info2 = DocHelper.writeDoc(merge2Dir, doc2);
    reader1 = SegmentReader.get(true, info1, IndexReader.DEFAULT_TERMS_INDEX_DIVISOR);
    reader2 = SegmentReader.get(true, info2, IndexReader.DEFAULT_TERMS_INDEX_DIVISOR);
  }
  
  @Override
  protected void tearDown() throws Exception {
    reader1.close();
    reader2.close();
    mergedDir.close();
    merge1Dir.close();
    merge2Dir.close();
    super.tearDown();
  }

  public void test() {
    assertTrue(mergedDir != null);
    assertTrue(merge1Dir != null);
    assertTrue(merge2Dir != null);
    assertTrue(reader1 != null);
    assertTrue(reader2 != null);
  }
  
  public void testMerge() throws IOException {                             
    SegmentMerger merger = new SegmentMerger(mergedDir, IndexWriter.DEFAULT_TERM_INDEX_INTERVAL, mergedSegment, null, CodecProvider.getDefault(), null);
    merger.add(reader1);
    merger.add(reader2);
    int docsMerged = merger.merge();
    merger.closeReaders();
    assertTrue(docsMerged == 2);
    //Should be able to open a new SegmentReader against the new directory
    SegmentReader mergedReader = SegmentReader.get(false, mergedDir, new SegmentInfo(mergedSegment, docsMerged, mergedDir, false, -1,
        null, false, merger.hasProx(), merger.getCodec()), BufferedIndexInput.BUFFER_SIZE, true, IndexReader.DEFAULT_TERMS_INDEX_DIVISOR, null);

    assertTrue(mergedReader != null);
    assertTrue(mergedReader.numDocs() == 2);
    Document newDoc1 = mergedReader.document(0);
    assertTrue(newDoc1 != null);
    //There are 2 unstored fields on the document
    assertTrue(DocHelper.numFields(newDoc1) == DocHelper.numFields(doc1) - DocHelper.unstored.size());
    Document newDoc2 = mergedReader.document(1);
    assertTrue(newDoc2 != null);
    assertTrue(DocHelper.numFields(newDoc2) == DocHelper.numFields(doc2) - DocHelper.unstored.size());
    
    DocsEnum termDocs = MultiFields.getTermDocsEnum(mergedReader,
                                                    MultiFields.getDeletedDocs(mergedReader),
                                                    DocHelper.TEXT_FIELD_2_KEY,
                                                    new BytesRef("field"));
    assertTrue(termDocs != null);
    assertTrue(termDocs.nextDoc() != DocsEnum.NO_MORE_DOCS);
    
    Collection<String> stored = mergedReader.getFieldNames(IndexReader.FieldOption.INDEXED_WITH_TERMVECTOR);
    assertTrue(stored != null);
    //System.out.println("stored size: " + stored.size());
    assertTrue("We do not have 3 fields that were indexed with term vector",stored.size() == 3);
    
    TermFreqVector vector = mergedReader.getTermFreqVector(0, DocHelper.TEXT_FIELD_2_KEY);
    assertTrue(vector != null);
    BytesRef [] terms = vector.getTerms();
    assertTrue(terms != null);
    //System.out.println("Terms size: " + terms.length);
    assertTrue(terms.length == 3);
    int [] freqs = vector.getTermFrequencies();
    assertTrue(freqs != null);
    //System.out.println("Freqs size: " + freqs.length);
    assertTrue(vector instanceof TermPositionVector == true);
    
    for (int i = 0; i < terms.length; i++) {
      String term = terms[i].utf8ToString();
      int freq = freqs[i];
      //System.out.println("Term: " + term + " Freq: " + freq);
      assertTrue(DocHelper.FIELD_2_TEXT.indexOf(term) != -1);
      assertTrue(DocHelper.FIELD_2_FREQS[i] == freq);
    }

    TestSegmentReader.checkNorms(mergedReader);
    mergedReader.close();
  }    
}
