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
import org.apache.lucene.store.Directory;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.Random;

public class TestSegmentTermDocs extends LuceneTestCase {
  private Document testDoc = new Document();
  private Directory dir;
  private SegmentInfo info;
  private Random random;

  public TestSegmentTermDocs(String s) {
    super(s);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    random = newRandom();
    dir = newDirectory(random);
    DocHelper.setupDoc(testDoc);
    info = DocHelper.writeDoc(dir, testDoc);
  }
  
  @Override
  protected void tearDown() throws Exception {
    dir.close();
    super.tearDown();
  }

  public void test() {
    assertTrue(dir != null);
  }
  
  public void testTermDocs() throws IOException {
    testTermDocs(1);
  }

  public void testTermDocs(int indexDivisor) throws IOException {
    //After adding the document, we should be able to read it back in
    SegmentReader reader = SegmentReader.get(true, info, indexDivisor);
    assertTrue(reader != null);
    assertEquals(indexDivisor, reader.getTermInfosIndexDivisor());

    TermsEnum terms = reader.fields().terms(DocHelper.TEXT_FIELD_2_KEY).iterator();
    terms.seek(new BytesRef("field"));
    DocsEnum termDocs = terms.docs(reader.getDeletedDocs(), null);
    if (termDocs.nextDoc() != DocsEnum.NO_MORE_DOCS)    {
      int docId = termDocs.docID();
      assertTrue(docId == 0);
      int freq = termDocs.freq();
      assertTrue(freq == 3);  
    }
    reader.close();
  }  
  
  public void testBadSeek() throws IOException {
    testBadSeek(1);
  }

  public void testBadSeek(int indexDivisor) throws IOException {
    {
      //After adding the document, we should be able to read it back in
      SegmentReader reader = SegmentReader.get(true, info, indexDivisor);
      assertTrue(reader != null);
      DocsEnum termDocs = reader.termDocsEnum(reader.getDeletedDocs(),
                                              "textField2",
                                              new BytesRef("bad"));

      assertNull(termDocs);
      reader.close();
    }
    {
      //After adding the document, we should be able to read it back in
      SegmentReader reader = SegmentReader.get(true, info, indexDivisor);
      assertTrue(reader != null);
      DocsEnum termDocs = reader.termDocsEnum(reader.getDeletedDocs(),
                                              "junk",
                                              new BytesRef("bad"));
      assertNull(termDocs);
      reader.close();
    }
  }
  
  public void testSkipTo() throws IOException {
    testSkipTo(1);
  }

  public void testSkipTo(int indexDivisor) throws IOException {
    Directory dir = newDirectory(random);
    IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(random, TEST_VERSION_CURRENT, new MockAnalyzer()));
    
    Term ta = new Term("content","aaa");
    for(int i = 0; i < 10; i++)
      addDoc(writer, "aaa aaa aaa aaa");
      
    Term tb = new Term("content","bbb");
    for(int i = 0; i < 16; i++)
      addDoc(writer, "bbb bbb bbb bbb");
      
    Term tc = new Term("content","ccc");
    for(int i = 0; i < 50; i++)
      addDoc(writer, "ccc ccc ccc ccc");
      
    // assure that we deal with a single segment  
    writer.optimize();
    writer.close();
    
    IndexReader reader = IndexReader.open(dir, null, true, indexDivisor);

    DocsEnum tdocs = MultiFields.getTermDocsEnum(reader,
                                                 MultiFields.getDeletedDocs(reader),
                                                 ta.field(),
                                                 new BytesRef(ta.text()));
    
    // without optimization (assumption skipInterval == 16)
    
    // with next
    assertTrue(tdocs.nextDoc() != DocsEnum.NO_MORE_DOCS);
    assertEquals(0, tdocs.docID());
    assertEquals(4, tdocs.freq());
    assertTrue(tdocs.nextDoc() != DocsEnum.NO_MORE_DOCS);
    assertEquals(1, tdocs.docID());
    assertEquals(4, tdocs.freq());
    assertTrue(tdocs.advance(0) != DocsEnum.NO_MORE_DOCS);
    assertEquals(2, tdocs.docID());
    assertTrue(tdocs.advance(4) != DocsEnum.NO_MORE_DOCS);
    assertEquals(4, tdocs.docID());
    assertTrue(tdocs.advance(9) != DocsEnum.NO_MORE_DOCS);
    assertEquals(9, tdocs.docID());
    assertFalse(tdocs.advance(10) != DocsEnum.NO_MORE_DOCS);
    
    // without next
    tdocs = MultiFields.getTermDocsEnum(reader,
                                        MultiFields.getDeletedDocs(reader),
                                        ta.field(),
                                        new BytesRef(ta.text()));
    
    assertTrue(tdocs.advance(0) != DocsEnum.NO_MORE_DOCS);
    assertEquals(0, tdocs.docID());
    assertTrue(tdocs.advance(4) != DocsEnum.NO_MORE_DOCS);
    assertEquals(4, tdocs.docID());
    assertTrue(tdocs.advance(9) != DocsEnum.NO_MORE_DOCS);
    assertEquals(9, tdocs.docID());
    assertFalse(tdocs.advance(10) != DocsEnum.NO_MORE_DOCS);
    
    // exactly skipInterval documents and therefore with optimization
    
    // with next
    tdocs = MultiFields.getTermDocsEnum(reader,
                                        MultiFields.getDeletedDocs(reader),
                                        tb.field(),
                                        new BytesRef(tb.text()));

    assertTrue(tdocs.nextDoc() != DocsEnum.NO_MORE_DOCS);
    assertEquals(10, tdocs.docID());
    assertEquals(4, tdocs.freq());
    assertTrue(tdocs.nextDoc() != DocsEnum.NO_MORE_DOCS);
    assertEquals(11, tdocs.docID());
    assertEquals(4, tdocs.freq());
    assertTrue(tdocs.advance(5) != DocsEnum.NO_MORE_DOCS);
    assertEquals(12, tdocs.docID());
    assertTrue(tdocs.advance(15) != DocsEnum.NO_MORE_DOCS);
    assertEquals(15, tdocs.docID());
    assertTrue(tdocs.advance(24) != DocsEnum.NO_MORE_DOCS);
    assertEquals(24, tdocs.docID());
    assertTrue(tdocs.advance(25) != DocsEnum.NO_MORE_DOCS);
    assertEquals(25, tdocs.docID());
    assertFalse(tdocs.advance(26) != DocsEnum.NO_MORE_DOCS);
    
    // without next
    tdocs = MultiFields.getTermDocsEnum(reader,
                                        MultiFields.getDeletedDocs(reader),
                                        tb.field(),
                                        new BytesRef(tb.text()));
    
    assertTrue(tdocs.advance(5) != DocsEnum.NO_MORE_DOCS);
    assertEquals(10, tdocs.docID());
    assertTrue(tdocs.advance(15) != DocsEnum.NO_MORE_DOCS);
    assertEquals(15, tdocs.docID());
    assertTrue(tdocs.advance(24) != DocsEnum.NO_MORE_DOCS);
    assertEquals(24, tdocs.docID());
    assertTrue(tdocs.advance(25) != DocsEnum.NO_MORE_DOCS);
    assertEquals(25, tdocs.docID());
    assertFalse(tdocs.advance(26) != DocsEnum.NO_MORE_DOCS);
    
    // much more than skipInterval documents and therefore with optimization
    
    // with next
    tdocs = MultiFields.getTermDocsEnum(reader,
                                        MultiFields.getDeletedDocs(reader),
                                        tc.field(),
                                        new BytesRef(tc.text()));

    assertTrue(tdocs.nextDoc() != DocsEnum.NO_MORE_DOCS);
    assertEquals(26, tdocs.docID());
    assertEquals(4, tdocs.freq());
    assertTrue(tdocs.nextDoc() != DocsEnum.NO_MORE_DOCS);
    assertEquals(27, tdocs.docID());
    assertEquals(4, tdocs.freq());
    assertTrue(tdocs.advance(5) != DocsEnum.NO_MORE_DOCS);
    assertEquals(28, tdocs.docID());
    assertTrue(tdocs.advance(40) != DocsEnum.NO_MORE_DOCS);
    assertEquals(40, tdocs.docID());
    assertTrue(tdocs.advance(57) != DocsEnum.NO_MORE_DOCS);
    assertEquals(57, tdocs.docID());
    assertTrue(tdocs.advance(74) != DocsEnum.NO_MORE_DOCS);
    assertEquals(74, tdocs.docID());
    assertTrue(tdocs.advance(75) != DocsEnum.NO_MORE_DOCS);
    assertEquals(75, tdocs.docID());
    assertFalse(tdocs.advance(76) != DocsEnum.NO_MORE_DOCS);
    
    //without next
    tdocs = MultiFields.getTermDocsEnum(reader,
                                        MultiFields.getDeletedDocs(reader),
                                        tc.field(),
                                        new BytesRef(tc.text()));
    assertTrue(tdocs.advance(5) != DocsEnum.NO_MORE_DOCS);
    assertEquals(26, tdocs.docID());
    assertTrue(tdocs.advance(40) != DocsEnum.NO_MORE_DOCS);
    assertEquals(40, tdocs.docID());
    assertTrue(tdocs.advance(57) != DocsEnum.NO_MORE_DOCS);
    assertEquals(57, tdocs.docID());
    assertTrue(tdocs.advance(74) != DocsEnum.NO_MORE_DOCS);
    assertEquals(74, tdocs.docID());
    assertTrue(tdocs.advance(75) != DocsEnum.NO_MORE_DOCS);
    assertEquals(75, tdocs.docID());
    assertFalse(tdocs.advance(76) != DocsEnum.NO_MORE_DOCS);
    
    reader.close();
    dir.close();
  }
  
  public void testIndexDivisor() throws IOException {
    testDoc = new Document();
    DocHelper.setupDoc(testDoc);
    DocHelper.writeDoc(dir, testDoc);
    testTermDocs(2);
    testBadSeek(2);
    testSkipTo(2);
  }

  private void addDoc(IndexWriter writer, String value) throws IOException
  {
      Document doc = new Document();
      doc.add(new Field("content", value, Field.Store.NO, Field.Index.ANALYZED));
      writer.addDocument(doc);
  }
}
