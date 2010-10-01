package org.apache.lucene.search;

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
import org.apache.lucene.document.NumericField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.LuceneTestCaseJ4;
import org.apache.lucene.util.NumericUtils;

import org.junit.Test;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import static org.junit.Assert.*;

public class TestNumericRangeQuery32 extends LuceneTestCaseJ4 {
  // distance of entries
  private static final int distance = 6666;
  // shift the starting of the values to the left, to also have negative values:
  private static final int startOffset = - 1 << 15;
  // number of docs to generate for testing
  private static final int noDocs = 10000 * RANDOM_MULTIPLIER;
  
  private static Directory directory = null;
  private static IndexReader reader = null;
  private static IndexSearcher searcher = null;
  private static Random random = null;
  
  @BeforeClass
  public static void beforeClass() throws Exception {
    random = newStaticRandom(TestNumericRangeQuery32.class);
    directory = newDirectory(random);
    RandomIndexWriter writer = new RandomIndexWriter(random, directory);
    
    NumericField
      field8 = new NumericField("field8", 8, Field.Store.YES, true),
      field4 = new NumericField("field4", 4, Field.Store.YES, true),
      field2 = new NumericField("field2", 2, Field.Store.YES, true),
      fieldNoTrie = new NumericField("field"+Integer.MAX_VALUE, Integer.MAX_VALUE, Field.Store.YES, true),
      ascfield8 = new NumericField("ascfield8", 8, Field.Store.NO, true),
      ascfield4 = new NumericField("ascfield4", 4, Field.Store.NO, true),
      ascfield2 = new NumericField("ascfield2", 2, Field.Store.NO, true);
    
    Document doc = new Document();
    // add fields, that have a distance to test general functionality
    doc.add(field8); doc.add(field4); doc.add(field2); doc.add(fieldNoTrie);
    // add ascending fields with a distance of 1, beginning at -noDocs/2 to test the correct splitting of range and inclusive/exclusive
    doc.add(ascfield8); doc.add(ascfield4); doc.add(ascfield2);
    
    // Add a series of noDocs docs with increasing int values
    for (int l=0; l<noDocs; l++) {
      int val=distance*l+startOffset;
      field8.setIntValue(val);
      field4.setIntValue(val);
      field2.setIntValue(val);
      fieldNoTrie.setIntValue(val);

      val=l-(noDocs/2);
      ascfield8.setIntValue(val);
      ascfield4.setIntValue(val);
      ascfield2.setIntValue(val);
      writer.addDocument(doc);
    }
  
    reader = writer.getReader();
    searcher=new IndexSearcher(reader);
    writer.close();
  }
  
  @AfterClass
  public static void afterClass() throws Exception {
    searcher.close();
    searcher = null;
    reader.close();
    reader = null;
    directory.close();
    directory = null;
  }
  
  @Override
  public void setUp() throws Exception {
    super.setUp();
    // set the theoretical maximum term count for 8bit (see docs for the number)
    // super.tearDown will restore the default
    BooleanQuery.setMaxClauseCount(3*255*2 + 255);
  }
  
  /** test for both constant score and boolean query, the other tests only use the constant score mode */
  private void testRange(int precisionStep) throws Exception {
    String field="field"+precisionStep;
    int count=3000;
    int lower=(distance*3/2)+startOffset, upper=lower + count*distance + (distance/3);
    NumericRangeQuery<Integer> q = NumericRangeQuery.newIntRange(field, precisionStep, lower, upper, true, true);
    NumericRangeFilter<Integer> f = NumericRangeFilter.newIntRange(field, precisionStep, lower, upper, true, true);
    int lastTerms = 0;
    for (byte i=0; i<3; i++) {
      TopDocs topDocs;
      int terms;
      String type;
      q.clearTotalNumberOfTerms();
      f.clearTotalNumberOfTerms();
      switch (i) {
        case 0:
          type = " (constant score filter rewrite)";
          q.setRewriteMethod(MultiTermQuery.CONSTANT_SCORE_FILTER_REWRITE);
          topDocs = searcher.search(q, null, noDocs, Sort.INDEXORDER);
          terms = q.getTotalNumberOfTerms();
          break;
        case 1:
          type = " (constant score boolean rewrite)";
          q.setRewriteMethod(MultiTermQuery.CONSTANT_SCORE_BOOLEAN_QUERY_REWRITE);
          topDocs = searcher.search(q, null, noDocs, Sort.INDEXORDER);
          terms = q.getTotalNumberOfTerms();
          break;
        case 2:
          type = " (filter)";
          topDocs = searcher.search(new MatchAllDocsQuery(), f, noDocs, Sort.INDEXORDER);
          terms = f.getTotalNumberOfTerms();
          break;
        default:
          return;
      }
      if (VERBOSE) System.out.println("Found "+terms+" distinct terms in range for field '"+field+"'"+type+".");
      ScoreDoc[] sd = topDocs.scoreDocs;
      assertNotNull(sd);
      assertEquals("Score doc count"+type, count, sd.length );
      Document doc=searcher.doc(sd[0].doc);
      assertEquals("First doc"+type, 2*distance+startOffset, Integer.parseInt(doc.get(field)) );
      doc=searcher.doc(sd[sd.length-1].doc);
      assertEquals("Last doc"+type, (1+count)*distance+startOffset, Integer.parseInt(doc.get(field)) );
      if (i>0 && searcher.getIndexReader().getSequentialSubReaders().length == 1) {
        assertEquals("Distinct term number is equal for all query types", lastTerms, terms);
      }
      lastTerms = terms;
    }
  }

  @Test
  public void testRange_8bit() throws Exception {
    testRange(8);
  }
  
  @Test
  public void testRange_4bit() throws Exception {
    testRange(4);
  }
  
  @Test
  public void testRange_2bit() throws Exception {
    testRange(2);
  }
  
  @Test
  public void testInverseRange() throws Exception {
    NumericRangeFilter<Integer> f = NumericRangeFilter.newIntRange("field8", 8, 1000, -1000, true, true);
    assertSame("A inverse range should return the EMPTY_DOCIDSET instance", DocIdSet.EMPTY_DOCIDSET, f.getDocIdSet(searcher.getIndexReader()));
    f = NumericRangeFilter.newIntRange("field8", 8, Integer.MAX_VALUE, null, false, false);
    assertSame("A exclusive range starting with Integer.MAX_VALUE should return the EMPTY_DOCIDSET instance",
      DocIdSet.EMPTY_DOCIDSET, f.getDocIdSet(searcher.getIndexReader()));
    f = NumericRangeFilter.newIntRange("field8", 8, null, Integer.MIN_VALUE, false, false);
    assertSame("A exclusive range ending with Integer.MIN_VALUE should return the EMPTY_DOCIDSET instance",
      DocIdSet.EMPTY_DOCIDSET, f.getDocIdSet(searcher.getIndexReader()));
  }
  
  @Test
  public void testOneMatchQuery() throws Exception {
    NumericRangeQuery<Integer> q = NumericRangeQuery.newIntRange("ascfield8", 8, 1000, 1000, true, true);
    assertSame(MultiTermQuery.CONSTANT_SCORE_BOOLEAN_QUERY_REWRITE, q.getRewriteMethod());
    TopDocs topDocs = searcher.search(q, noDocs);
    ScoreDoc[] sd = topDocs.scoreDocs;
    assertNotNull(sd);
    assertEquals("Score doc count", 1, sd.length );
  }
  
  private void testLeftOpenRange(int precisionStep) throws Exception {
    String field="field"+precisionStep;
    int count=3000;
    int upper=(count-1)*distance + (distance/3) + startOffset;
    NumericRangeQuery<Integer> q=NumericRangeQuery.newIntRange(field, precisionStep, null, upper, true, true);
    TopDocs topDocs = searcher.search(q, null, noDocs, Sort.INDEXORDER);
    if (VERBOSE) System.out.println("Found "+q.getTotalNumberOfTerms()+" distinct terms in left open range for field '"+field+"'.");
    ScoreDoc[] sd = topDocs.scoreDocs;
    assertNotNull(sd);
    assertEquals("Score doc count", count, sd.length );
    Document doc=searcher.doc(sd[0].doc);
    assertEquals("First doc", startOffset, Integer.parseInt(doc.get(field)) );
    doc=searcher.doc(sd[sd.length-1].doc);
    assertEquals("Last doc", (count-1)*distance+startOffset, Integer.parseInt(doc.get(field)) );
    
    q=NumericRangeQuery.newIntRange(field, precisionStep, null, upper, false, true);
    topDocs = searcher.search(q, null, noDocs, Sort.INDEXORDER);
    sd = topDocs.scoreDocs;
    assertNotNull(sd);
    assertEquals("Score doc count", count, sd.length );
    doc=searcher.doc(sd[0].doc);
    assertEquals("First doc", startOffset, Integer.parseInt(doc.get(field)) );
    doc=searcher.doc(sd[sd.length-1].doc);
    assertEquals("Last doc", (count-1)*distance+startOffset, Integer.parseInt(doc.get(field)) );
  }
  
  @Test
  public void testLeftOpenRange_8bit() throws Exception {
    testLeftOpenRange(8);
  }
  
  @Test
  public void testLeftOpenRange_4bit() throws Exception {
    testLeftOpenRange(4);
  }
  
  @Test
  public void testLeftOpenRange_2bit() throws Exception {
    testLeftOpenRange(2);
  }
  
  private void testRightOpenRange(int precisionStep) throws Exception {
    String field="field"+precisionStep;
    int count=3000;
    int lower=(count-1)*distance + (distance/3) +startOffset;
    NumericRangeQuery<Integer> q=NumericRangeQuery.newIntRange(field, precisionStep, lower, null, true, true);
    TopDocs topDocs = searcher.search(q, null, noDocs, Sort.INDEXORDER);
    if (VERBOSE) System.out.println("Found "+q.getTotalNumberOfTerms()+" distinct terms in right open range for field '"+field+"'.");
    ScoreDoc[] sd = topDocs.scoreDocs;
    assertNotNull(sd);
    assertEquals("Score doc count", noDocs-count, sd.length );
    Document doc=searcher.doc(sd[0].doc);
    assertEquals("First doc", count*distance+startOffset, Integer.parseInt(doc.get(field)) );
    doc=searcher.doc(sd[sd.length-1].doc);
    assertEquals("Last doc", (noDocs-1)*distance+startOffset, Integer.parseInt(doc.get(field)) );

    q=NumericRangeQuery.newIntRange(field, precisionStep, lower, null, true, false);
    topDocs = searcher.search(q, null, noDocs, Sort.INDEXORDER);
    sd = topDocs.scoreDocs;
    assertNotNull(sd);
    assertEquals("Score doc count", noDocs-count, sd.length );
    doc=searcher.doc(sd[0].doc);
    assertEquals("First doc", count*distance+startOffset, Integer.parseInt(doc.get(field)) );
    doc=searcher.doc(sd[sd.length-1].doc);
    assertEquals("Last doc", (noDocs-1)*distance+startOffset, Integer.parseInt(doc.get(field)) );
  }
  
  @Test
  public void testRightOpenRange_8bit() throws Exception {
    testRightOpenRange(8);
  }
  
  @Test
  public void testRightOpenRange_4bit() throws Exception {
    testRightOpenRange(4);
  }
  
  @Test
  public void testRightOpenRange_2bit() throws Exception {
    testRightOpenRange(2);
  }
  
  @Test
  public void testInfiniteValues() throws Exception {
    Directory dir = newDirectory(random);
    IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(random, TEST_VERSION_CURRENT, new MockAnalyzer()));
    Document doc = new Document();
    doc.add(new NumericField("float").setFloatValue(Float.NEGATIVE_INFINITY));
    doc.add(new NumericField("int").setIntValue(Integer.MIN_VALUE));
    writer.addDocument(doc);
    
    doc = new Document();
    doc.add(new NumericField("float").setFloatValue(Float.POSITIVE_INFINITY));
    doc.add(new NumericField("int").setIntValue(Integer.MAX_VALUE));
    writer.addDocument(doc);
    
    doc = new Document();
    doc.add(new NumericField("float").setFloatValue(0.0f));
    doc.add(new NumericField("int").setIntValue(0));
    writer.addDocument(doc);
    writer.close();
    
    IndexSearcher s = new IndexSearcher(dir);
    
    Query q=NumericRangeQuery.newIntRange("int", null, null, true, true);
    TopDocs topDocs = s.search(q, 10);
    assertEquals("Score doc count", 3,  topDocs.scoreDocs.length );
    
    q=NumericRangeQuery.newIntRange("int", null, null, false, false);
    topDocs = s.search(q, 10);
    assertEquals("Score doc count", 3,  topDocs.scoreDocs.length );

    q=NumericRangeQuery.newIntRange("int", Integer.MIN_VALUE, Integer.MAX_VALUE, true, true);
    topDocs = s.search(q, 10);
    assertEquals("Score doc count", 3,  topDocs.scoreDocs.length );
    
    q=NumericRangeQuery.newIntRange("int", Integer.MIN_VALUE, Integer.MAX_VALUE, false, false);
    topDocs = s.search(q, 10);
    assertEquals("Score doc count", 1,  topDocs.scoreDocs.length );

    q=NumericRangeQuery.newFloatRange("float", null, null, true, true);
    topDocs = s.search(q, 10);
    assertEquals("Score doc count", 3,  topDocs.scoreDocs.length );

    q=NumericRangeQuery.newFloatRange("float", null, null, false, false);
    topDocs = s.search(q, 10);
    assertEquals("Score doc count", 3,  topDocs.scoreDocs.length );

    s.close();
    dir.close();
  }
  
  private void testRandomTrieAndClassicRangeQuery(int precisionStep) throws Exception {
    final Random rnd=newRandom();
    String field="field"+precisionStep;
    int termCountT=0,termCountC=0;
    int num = 10 * RANDOM_MULTIPLIER;
    for (int i = 0; i < num; i++) {
      int lower=(int)(rnd.nextDouble()*noDocs*distance)+startOffset;
      int upper=(int)(rnd.nextDouble()*noDocs*distance)+startOffset;
      if (lower>upper) {
        int a=lower; lower=upper; upper=a;
      }
      final BytesRef lowerBytes = new BytesRef(NumericUtils.BUF_SIZE_INT), upperBytes = new BytesRef(NumericUtils.BUF_SIZE_INT);
      NumericUtils.intToPrefixCoded(lower, 0, lowerBytes);
      NumericUtils.intToPrefixCoded(upper, 0, upperBytes);
      // TODO: when new TermRange ctors with BytesRef available, use them and do not convert to string!
      final String lowerString = lowerBytes.utf8ToString(), upperString = upperBytes.utf8ToString();

      // test inclusive range
      NumericRangeQuery<Integer> tq=NumericRangeQuery.newIntRange(field, precisionStep, lower, upper, true, true);
      TermRangeQuery cq=new TermRangeQuery(field, lowerString, upperString, true, true);
      TopDocs tTopDocs = searcher.search(tq, 1);
      TopDocs cTopDocs = searcher.search(cq, 1);
      assertEquals("Returned count for NumericRangeQuery and TermRangeQuery must be equal", cTopDocs.totalHits, tTopDocs.totalHits );
      termCountT += tq.getTotalNumberOfTerms();
      termCountC += cq.getTotalNumberOfTerms();
      // test exclusive range
      tq=NumericRangeQuery.newIntRange(field, precisionStep, lower, upper, false, false);
      cq=new TermRangeQuery(field, lowerString, upperString, false, false);
      tTopDocs = searcher.search(tq, 1);
      cTopDocs = searcher.search(cq, 1);
      assertEquals("Returned count for NumericRangeQuery and TermRangeQuery must be equal", cTopDocs.totalHits, tTopDocs.totalHits );
      termCountT += tq.getTotalNumberOfTerms();
      termCountC += cq.getTotalNumberOfTerms();
      // test left exclusive range
      tq=NumericRangeQuery.newIntRange(field, precisionStep, lower, upper, false, true);
      cq=new TermRangeQuery(field, lowerString, upperString, false, true);
      tTopDocs = searcher.search(tq, 1);
      cTopDocs = searcher.search(cq, 1);
      assertEquals("Returned count for NumericRangeQuery and TermRangeQuery must be equal", cTopDocs.totalHits, tTopDocs.totalHits );
      termCountT += tq.getTotalNumberOfTerms();
      termCountC += cq.getTotalNumberOfTerms();
      // test right exclusive range
      tq=NumericRangeQuery.newIntRange(field, precisionStep, lower, upper, true, false);
      cq=new TermRangeQuery(field, lowerString, upperString, true, false);
      tTopDocs = searcher.search(tq, 1);
      cTopDocs = searcher.search(cq, 1);
      assertEquals("Returned count for NumericRangeQuery and TermRangeQuery must be equal", cTopDocs.totalHits, tTopDocs.totalHits );
      termCountT += tq.getTotalNumberOfTerms();
      termCountC += cq.getTotalNumberOfTerms();
    }
    if (precisionStep == Integer.MAX_VALUE && searcher.getIndexReader().getSequentialSubReaders().length == 1) {
      assertEquals("Total number of terms should be equal for unlimited precStep", termCountT, termCountC);
    } else if (VERBOSE) {
      System.out.println("Average number of terms during random search on '" + field + "':");
      System.out.println(" Trie query: " + (((double)termCountT)/(num * 4)));
      System.out.println(" Classical query: " + (((double)termCountC)/(num * 4)));
    }
  }
  
  @Test
  public void testRandomTrieAndClassicRangeQuery_8bit() throws Exception {
    testRandomTrieAndClassicRangeQuery(8);
  }
  
  @Test
  public void testRandomTrieAndClassicRangeQuery_4bit() throws Exception {
    testRandomTrieAndClassicRangeQuery(4);
  }
  
  @Test
  public void testRandomTrieAndClassicRangeQuery_2bit() throws Exception {
    testRandomTrieAndClassicRangeQuery(2);
  }
  
  @Test
  public void testRandomTrieAndClassicRangeQuery_NoTrie() throws Exception {
    testRandomTrieAndClassicRangeQuery(Integer.MAX_VALUE);
  }
  
  private void testRangeSplit(int precisionStep) throws Exception {
    final Random rnd=newRandom();
    String field="ascfield"+precisionStep;
    // 10 random tests
    int  num = 10 * RANDOM_MULTIPLIER;
    for (int  i =0;  i< num; i++) {
      int lower=(int)(rnd.nextDouble()*noDocs - noDocs/2);
      int upper=(int)(rnd.nextDouble()*noDocs - noDocs/2);
      if (lower>upper) {
        int a=lower; lower=upper; upper=a;
      }
      // test inclusive range
      Query tq=NumericRangeQuery.newIntRange(field, precisionStep, lower, upper, true, true);
      TopDocs tTopDocs = searcher.search(tq, 1);
      assertEquals("Returned count of range query must be equal to inclusive range length", upper-lower+1, tTopDocs.totalHits );
      // test exclusive range
      tq=NumericRangeQuery.newIntRange(field, precisionStep, lower, upper, false, false);
      tTopDocs = searcher.search(tq, 1);
      assertEquals("Returned count of range query must be equal to exclusive range length", Math.max(upper-lower-1, 0), tTopDocs.totalHits );
      // test left exclusive range
      tq=NumericRangeQuery.newIntRange(field, precisionStep, lower, upper, false, true);
      tTopDocs = searcher.search(tq, 1);
      assertEquals("Returned count of range query must be equal to half exclusive range length", upper-lower, tTopDocs.totalHits );
      // test right exclusive range
      tq=NumericRangeQuery.newIntRange(field, precisionStep, lower, upper, true, false);
      tTopDocs = searcher.search(tq, 1);
      assertEquals("Returned count of range query must be equal to half exclusive range length", upper-lower, tTopDocs.totalHits );
    }
  }

  @Test
  public void testRangeSplit_8bit() throws Exception {
    testRangeSplit(8);
  }
  
  @Test
  public void testRangeSplit_4bit() throws Exception {
    testRangeSplit(4);
  }
  
  @Test
  public void testRangeSplit_2bit() throws Exception {
    testRangeSplit(2);
  }
  
  /** we fake a float test using int2float conversion of NumericUtils */
  private void testFloatRange(int precisionStep) throws Exception {
    final String field="ascfield"+precisionStep;
    final int lower=-1000, upper=+2000;
    
    Query tq=NumericRangeQuery.newFloatRange(field, precisionStep,
      NumericUtils.sortableIntToFloat(lower), NumericUtils.sortableIntToFloat(upper), true, true);
    TopDocs tTopDocs = searcher.search(tq, 1);
    assertEquals("Returned count of range query must be equal to inclusive range length", upper-lower+1, tTopDocs.totalHits );
    
    Filter tf=NumericRangeFilter.newFloatRange(field, precisionStep,
      NumericUtils.sortableIntToFloat(lower), NumericUtils.sortableIntToFloat(upper), true, true);
    tTopDocs = searcher.search(new MatchAllDocsQuery(), tf, 1);
    assertEquals("Returned count of range filter must be equal to inclusive range length", upper-lower+1, tTopDocs.totalHits );
  }

  @Test
  public void testFloatRange_8bit() throws Exception {
    testFloatRange(8);
  }
  
  @Test
  public void testFloatRange_4bit() throws Exception {
    testFloatRange(4);
  }
  
  @Test
  public void testFloatRange_2bit() throws Exception {
    testFloatRange(2);
  }
  
  private void testSorting(int precisionStep) throws Exception {
    final Random rnd=newRandom();
    String field="field"+precisionStep;
    // 10 random tests, the index order is ascending,
    // so using a reverse sort field should retun descending documents
    int num = 10 * RANDOM_MULTIPLIER;
    for (int i = 0; i < num; i++) {
      int lower=(int)(rnd.nextDouble()*noDocs*distance)+startOffset;
      int upper=(int)(rnd.nextDouble()*noDocs*distance)+startOffset;
      if (lower>upper) {
        int a=lower; lower=upper; upper=a;
      }
      Query tq=NumericRangeQuery.newIntRange(field, precisionStep, lower, upper, true, true);
      TopDocs topDocs = searcher.search(tq, null, noDocs, new Sort(new SortField(field, SortField.INT, true)));
      if (topDocs.totalHits==0) continue;
      ScoreDoc[] sd = topDocs.scoreDocs;
      assertNotNull(sd);
      int last=Integer.parseInt(searcher.doc(sd[0].doc).get(field));
      for (int j=1; j<sd.length; j++) {
        int act=Integer.parseInt(searcher.doc(sd[j].doc).get(field));
        assertTrue("Docs should be sorted backwards", last>act );
        last=act;
      }
    }
  }

  @Test
  public void testSorting_8bit() throws Exception {
    testSorting(8);
  }
  
  @Test
  public void testSorting_4bit() throws Exception {
    testSorting(4);
  }
  
  @Test
  public void testSorting_2bit() throws Exception {
    testSorting(2);
  }
  
  @Test
  public void testEqualsAndHash() throws Exception {
    QueryUtils.checkHashEquals(NumericRangeQuery.newIntRange("test1", 4, 10, 20, true, true));
    QueryUtils.checkHashEquals(NumericRangeQuery.newIntRange("test2", 4, 10, 20, false, true));
    QueryUtils.checkHashEquals(NumericRangeQuery.newIntRange("test3", 4, 10, 20, true, false));
    QueryUtils.checkHashEquals(NumericRangeQuery.newIntRange("test4", 4, 10, 20, false, false));
    QueryUtils.checkHashEquals(NumericRangeQuery.newIntRange("test5", 4, 10, null, true, true));
    QueryUtils.checkHashEquals(NumericRangeQuery.newIntRange("test6", 4, null, 20, true, true));
    QueryUtils.checkHashEquals(NumericRangeQuery.newIntRange("test7", 4, null, null, true, true));
    QueryUtils.checkEqual(
      NumericRangeQuery.newIntRange("test8", 4, 10, 20, true, true), 
      NumericRangeQuery.newIntRange("test8", 4, 10, 20, true, true)
    );
    QueryUtils.checkUnequal(
      NumericRangeQuery.newIntRange("test9", 4, 10, 20, true, true), 
      NumericRangeQuery.newIntRange("test9", 8, 10, 20, true, true)
    );
    QueryUtils.checkUnequal(
      NumericRangeQuery.newIntRange("test10a", 4, 10, 20, true, true), 
      NumericRangeQuery.newIntRange("test10b", 4, 10, 20, true, true)
    );
    QueryUtils.checkUnequal(
      NumericRangeQuery.newIntRange("test11", 4, 10, 20, true, true), 
      NumericRangeQuery.newIntRange("test11", 4, 20, 10, true, true)
    );
    QueryUtils.checkUnequal(
      NumericRangeQuery.newIntRange("test12", 4, 10, 20, true, true), 
      NumericRangeQuery.newIntRange("test12", 4, 10, 20, false, true)
    );
    QueryUtils.checkUnequal(
      NumericRangeQuery.newIntRange("test13", 4, 10, 20, true, true), 
      NumericRangeQuery.newFloatRange("test13", 4, 10f, 20f, true, true)
    );
    // the following produces a hash collision, because Long and Integer have the same hashcode, so only test equality:
    Query q1 = NumericRangeQuery.newIntRange("test14", 4, 10, 20, true, true);
    Query q2 = NumericRangeQuery.newLongRange("test14", 4, 10L, 20L, true, true);
    assertFalse(q1.equals(q2));
    assertFalse(q2.equals(q1));
  }
  
  private void testEnum(int lower, int upper) throws Exception {
    NumericRangeQuery<Integer> q = NumericRangeQuery.newIntRange("field4", 4,
        lower, upper, true, true);
    TermsEnum termEnum = q.getTermsEnum(searcher.getIndexReader());
    int count = 0;
    while (termEnum.next() != null) {
      final BytesRef t = termEnum.term();
      if (t != null) {
        final int val = NumericUtils.prefixCodedToInt(t);
        assertTrue("value not in bounds " + val + " >= " + lower + " && "
            + val + " <= " + upper, val >= lower && val <= upper);
        count++;
      } else
        break;
    } 
    assertNull(termEnum.next());
    if (VERBOSE) System.out.println("TermEnum on 'field4' for range [" + lower + "," + upper
        + "] contained " + count + " terms.");

  }
  
  @Test
  public void testEnum() throws Exception {
    int count=3000;
    int lower=(distance*3/2)+startOffset, upper=lower + count*distance + (distance/3);
    // test enum with values
    testEnum(lower, upper);
    // test empty enum
    testEnum(upper, lower);
    // test empty enum outside of bounds
    lower = distance*noDocs+startOffset;
    upper = 2 * lower;
    testEnum(lower, upper);
  }
  
}
