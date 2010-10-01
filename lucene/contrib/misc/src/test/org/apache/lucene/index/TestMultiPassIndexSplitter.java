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

import java.util.Random;

import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestMultiPassIndexSplitter extends LuceneTestCase {
  IndexReader input;
  int NUM_DOCS = 11;
  private Random random;
  Directory dir;
  
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    random = newRandom();
    dir = newDirectory(random);
    IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(random, TEST_VERSION_CURRENT, new MockAnalyzer()));
    Document doc;
    for (int i = 0; i < NUM_DOCS; i++) {
      doc = new Document();
      doc.add(new Field("id", i + "", Field.Store.YES, Field.Index.NOT_ANALYZED));
      doc.add(new Field("f", i + " " + i, Field.Store.YES, Field.Index.ANALYZED));
      w.addDocument(doc);
    }
    w.close();
    input = IndexReader.open(dir, false);
    // delete the last doc
    input.deleteDocument(input.maxDoc() - 1);
    IndexReader inputOld = input;
    input = input.reopen(true);
    inputOld.close();
  }
  
  @Override
  protected void tearDown() throws Exception {
    input.close();
    dir.close();
    super.tearDown();
  }
  
  /**
   * Test round-robin splitting.
   */
  public void testSplitRR() throws Exception {
    MultiPassIndexSplitter splitter = new MultiPassIndexSplitter();
    Directory[] dirs = new Directory[]{
            newDirectory(random),
            newDirectory(random),
            newDirectory(random)
    };
    splitter.split(input, dirs, false);
    IndexReader ir;
    ir = IndexReader.open(dirs[0], true);
    assertTrue(ir.numDocs() - NUM_DOCS / 3 <= 1); // rounding error
    Document doc = ir.document(0);
    assertEquals("0", doc.get("id"));
    TermsEnum te = MultiFields.getTerms(ir, "id").iterator();
    assertEquals(TermsEnum.SeekStatus.NOT_FOUND, te.seek(new BytesRef("1")));
    assertNotSame("1", te.term().utf8ToString());
    ir.close();
    ir = IndexReader.open(dirs[1], true);
    assertTrue(ir.numDocs() - NUM_DOCS / 3 <= 1);
    doc = ir.document(0);
    assertEquals("1", doc.get("id"));
    te = MultiFields.getTerms(ir, "id").iterator();
    assertEquals(TermsEnum.SeekStatus.NOT_FOUND, te.seek(new BytesRef("0")));

    assertNotSame("0", te.term().utf8ToString());
    ir.close();
    ir = IndexReader.open(dirs[2], true);
    assertTrue(ir.numDocs() - NUM_DOCS / 3 <= 1);
    doc = ir.document(0);
    assertEquals("2", doc.get("id"));

    te = MultiFields.getTerms(ir, "id").iterator();
    assertEquals(TermsEnum.SeekStatus.NOT_FOUND, te.seek(new BytesRef("1")));
    assertNotSame("1", te.term());

    assertEquals(TermsEnum.SeekStatus.NOT_FOUND, te.seek(new BytesRef("0")));
    assertNotSame("0", te.term().utf8ToString());
    ir.close();
    for (Directory d : dirs)
      d.close();
  }
  
  /**
   * Test sequential splitting.
   */
  public void testSplitSeq() throws Exception {
    MultiPassIndexSplitter splitter = new MultiPassIndexSplitter();
    Directory[] dirs = new Directory[]{
            newDirectory(random),
            newDirectory(random),
            newDirectory(random)
    };
    splitter.split(input, dirs, true);
    IndexReader ir;
    ir = IndexReader.open(dirs[0], true);
    assertTrue(ir.numDocs() - NUM_DOCS / 3 <= 1);
    Document doc = ir.document(0);
    assertEquals("0", doc.get("id"));
    int start = ir.numDocs();
    ir.close();
    ir = IndexReader.open(dirs[1], true);
    assertTrue(ir.numDocs() - NUM_DOCS / 3 <= 1);
    doc = ir.document(0);
    assertEquals(start + "", doc.get("id"));
    start += ir.numDocs();
    ir.close();
    ir = IndexReader.open(dirs[2], true);
    assertTrue(ir.numDocs() - NUM_DOCS / 3 <= 1);
    doc = ir.document(0);
    assertEquals(start + "", doc.get("id"));
    // make sure the deleted doc is not here
    TermsEnum te = MultiFields.getTerms(ir, "id").iterator();
    Term t = new Term("id", (NUM_DOCS - 1) + "");
    assertEquals(TermsEnum.SeekStatus.NOT_FOUND, te.seek(new BytesRef(t.text())));
    assertNotSame(t.text(), te.term().utf8ToString());
    ir.close();
    for (Directory d : dirs)
      d.close();
  }
}
