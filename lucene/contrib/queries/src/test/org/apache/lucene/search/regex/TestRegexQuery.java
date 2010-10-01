package org.apache.lucene.search.regex;

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

import org.apache.lucene.store.Directory;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.index.TermsEnum;

import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.util.LuceneTestCase;

public class TestRegexQuery extends LuceneTestCase {
  private IndexSearcher searcher;
  private IndexReader reader;
  private Directory directory;
  private final String FN = "field";


  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Random random = newRandom();
    directory = newDirectory(random);
    RandomIndexWriter writer = new RandomIndexWriter(random, directory);
    Document doc = new Document();
    doc.add(new Field(FN, "the quick brown fox jumps over the lazy dog", Field.Store.NO, Field.Index.ANALYZED));
    writer.addDocument(doc);
    reader = writer.getReader();
    writer.close();
    searcher = new IndexSearcher(reader);
  }

  @Override
  protected void tearDown() throws Exception {
    searcher.close();
    reader.close();
    directory.close();
    super.tearDown();
  }

  private Term newTerm(String value) { return new Term(FN, value); }

  private int  regexQueryNrHits(String regex, RegexCapabilities capability) throws Exception {
    RegexQuery query = new RegexQuery( newTerm(regex));
    
    if ( capability != null )
      query.setRegexImplementation(capability);
    
    return searcher.search(query, null, 1000).totalHits;
  }

  private int  spanRegexQueryNrHits(String regex1, String regex2, int slop, boolean ordered) throws Exception {
    SpanRegexQuery srq1 = new SpanRegexQuery( newTerm(regex1));
    SpanRegexQuery srq2 = new SpanRegexQuery( newTerm(regex2));
    SpanNearQuery query = new SpanNearQuery( new SpanQuery[]{srq1, srq2}, slop, ordered);
    
    return searcher.search(query, null, 1000).totalHits;
  }

  public void testMatchAll() throws Exception {
    TermsEnum terms = new RegexQuery(new Term(FN, "jum.")).getTermsEnum(searcher.getIndexReader());
    // no term should match
    assertNull(terms.next());
  }

  public void testRegex1() throws Exception {
    assertEquals(1, regexQueryNrHits("^q.[aeiou]c.*$", null));
  }

  public void testRegex2() throws Exception {
    assertEquals(0, regexQueryNrHits("^.[aeiou]c.*$", null));
  }

  public void testRegex3() throws Exception {
    assertEquals(0, regexQueryNrHits("^q.[aeiou]c$", null));
  }

  public void testSpanRegex1() throws Exception {
    assertEquals(1, spanRegexQueryNrHits("^q.[aeiou]c.*$", "dog", 6, true));
  }

  public void testSpanRegex2() throws Exception {
    assertEquals(0, spanRegexQueryNrHits("^q.[aeiou]c.*$", "dog", 5, true));
  }

  public void testEquals() throws Exception {
    RegexQuery query1 = new RegexQuery( newTerm("foo.*"));
    query1.setRegexImplementation(new JakartaRegexpCapabilities());

    RegexQuery query2 = new RegexQuery( newTerm("foo.*"));
    assertFalse(query1.equals(query2));
  }
  
  public void testJakartaCaseSensativeFail() throws Exception {
    assertEquals(0, regexQueryNrHits("^.*DOG.*$", null));
  }

  public void testJavaUtilCaseSensativeFail() throws Exception {
    assertEquals(0, regexQueryNrHits("^.*DOG.*$", null));
  }
  
  public void testJakartaCaseInsensative() throws Exception {
    assertEquals(1, regexQueryNrHits("^.*DOG.*$", new JakartaRegexpCapabilities(JakartaRegexpCapabilities.FLAG_MATCH_CASEINDEPENDENT)));
  }
  
  public void testJavaUtilCaseInsensative() throws Exception {
    assertEquals(1, regexQueryNrHits("^.*DOG.*$", new JavaUtilRegexCapabilities(JavaUtilRegexCapabilities.FLAG_CASE_INSENSITIVE)));
  }

}

