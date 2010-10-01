package org.apache.lucene.queryParser.precedence;

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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.analysis.MockTokenFilter;
import org.apache.lucene.analysis.MockTokenizer;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.util.LocalizedTestCase;
import org.apache.lucene.util.automaton.BasicAutomata;
import org.apache.lucene.util.automaton.CharacterRunAutomaton;

import java.io.IOException;
import java.io.Reader;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashSet;

public class TestPrecedenceQueryParser extends LocalizedTestCase {
  
  public TestPrecedenceQueryParser(String name) {
    super(name, new HashSet<String>(Arrays.asList(new String[]{
      "testDateRange", "testNumber"
    })));
  }

  public static Analyzer qpAnalyzer = new QPTestAnalyzer();

  public static final class QPTestFilter extends TokenFilter {
    /**
     * Filter which discards the token 'stop' and which expands the
     * token 'phrase' into 'phrase1 phrase2'
     */
    public QPTestFilter(TokenStream in) {
      super(in);
    }

    boolean inPhrase = false;
    int savedStart = 0, savedEnd = 0;

    CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);
    
    @Override
    public boolean incrementToken() throws IOException {
      clearAttributes();
      if (inPhrase) {
        inPhrase = false;
        termAtt.setEmpty().append("phrase2");
        offsetAtt.setOffset(savedStart, savedEnd);
        return true;
      } else
        while(input.incrementToken())
          if (termAtt.toString().equals("phrase")) {
            inPhrase = true;
            savedStart = offsetAtt.startOffset();
            savedEnd = offsetAtt.endOffset();
            termAtt.setEmpty().append("phrase1");
            offsetAtt.setOffset(savedStart, savedEnd);
            return true;
          } else if (!termAtt.toString().equals("stop"))
            return true;
      return false;
    }
  }

  public static final class QPTestAnalyzer extends Analyzer {

    /** Filters MockTokenizer with StopFilter. */
    @Override
    public final TokenStream tokenStream(String fieldName, Reader reader) {
      return new QPTestFilter(new MockTokenizer(reader, MockTokenizer.SIMPLE, true));
    }
  }

  public static class QPTestParser extends PrecedenceQueryParser {
    public QPTestParser(String f, Analyzer a) {
      super(f, a);
    }

    @Override
    protected Query getFuzzyQuery(String field, String termStr, float minSimilarity) throws ParseException {
      throw new ParseException("Fuzzy queries not allowed");
    }

    @Override
    protected Query getWildcardQuery(String field, String termStr) throws ParseException {
      throw new ParseException("Wildcard queries not allowed");
    }
  }

  private int originalMaxClauses;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    originalMaxClauses = BooleanQuery.getMaxClauseCount();
  }

  public PrecedenceQueryParser getParser(Analyzer a) throws Exception {
    if (a == null)
      a = new MockAnalyzer(MockTokenizer.SIMPLE, true);
    PrecedenceQueryParser qp = new PrecedenceQueryParser("field", a);
    qp.setDefaultOperator(PrecedenceQueryParser.OR_OPERATOR);
    return qp;
  }

  public Query getQuery(String query, Analyzer a) throws Exception {
    return getParser(a).parse(query);
  }

  public void assertQueryEquals(String query, Analyzer a, String result)
    throws Exception {
    Query q = getQuery(query, a);
    String s = q.toString("field");
    if (!s.equals(result)) {
      fail("Query /" + query + "/ yielded /" + s
           + "/, expecting /" + result + "/");
    }
  }

  public void assertWildcardQueryEquals(String query, boolean lowercase, String result)
    throws Exception {
    PrecedenceQueryParser qp = getParser(null);
    qp.setLowercaseExpandedTerms(lowercase);
    Query q = qp.parse(query);
    String s = q.toString("field");
    if (!s.equals(result)) {
      fail("WildcardQuery /" + query + "/ yielded /" + s
           + "/, expecting /" + result + "/");
    }
  }

  public void assertWildcardQueryEquals(String query, String result) throws Exception {
    PrecedenceQueryParser qp = getParser(null);
    Query q = qp.parse(query);
    String s = q.toString("field");
    if (!s.equals(result)) {
      fail("WildcardQuery /" + query + "/ yielded /" + s + "/, expecting /"
          + result + "/");
    }
  }

  public Query getQueryDOA(String query, Analyzer a)
    throws Exception {
    if (a == null)
      a = new MockAnalyzer(MockTokenizer.SIMPLE, true);
    PrecedenceQueryParser qp = new PrecedenceQueryParser("field", a);
    qp.setDefaultOperator(PrecedenceQueryParser.AND_OPERATOR);
    return qp.parse(query);
  }

  public void assertQueryEqualsDOA(String query, Analyzer a, String result)
    throws Exception {
    Query q = getQueryDOA(query, a);
    String s = q.toString("field");
    if (!s.equals(result)) {
      fail("Query /" + query + "/ yielded /" + s
           + "/, expecting /" + result + "/");
    }
  }

  // failing tests disabled since PrecedenceQueryParser
  // is currently unmaintained
  public void _testSimple() throws Exception {
    assertQueryEquals("", null, "");

    assertQueryEquals("term term term", null, "term term term");
    assertQueryEquals("türm term term", null, "türm term term");
    assertQueryEquals("ümlaut", null, "ümlaut");

    assertQueryEquals("+a", null, "+a");
    assertQueryEquals("-a", null, "-a");
    assertQueryEquals("a AND b", null, "+a +b");
    assertQueryEquals("(a AND b)", null, "+a +b");
    assertQueryEquals("c OR (a AND b)", null, "c (+a +b)");
    assertQueryEquals("a AND NOT b", null, "+a -b");
    assertQueryEquals("a AND -b", null, "+a -b");
    assertQueryEquals("a AND !b", null, "+a -b");
    assertQueryEquals("a && b", null, "+a +b");
    assertQueryEquals("a && ! b", null, "+a -b");

    assertQueryEquals("a OR b", null, "a b");
    assertQueryEquals("a || b", null, "a b");

    assertQueryEquals("+term -term term", null, "+term -term term");
    assertQueryEquals("foo:term AND field:anotherTerm", null,
                      "+foo:term +anotherterm");
    assertQueryEquals("term AND \"phrase phrase\"", null,
                      "+term +\"phrase phrase\"");
    assertQueryEquals("\"hello there\"", null, "\"hello there\"");
    assertTrue(getQuery("a AND b", null) instanceof BooleanQuery);
    assertTrue(getQuery("hello", null) instanceof TermQuery);
    assertTrue(getQuery("\"hello there\"", null) instanceof PhraseQuery);

    assertQueryEquals("germ term^2.0", null, "germ term^2.0");
    assertQueryEquals("(term)^2.0", null, "term^2.0");
    assertQueryEquals("(germ term)^2.0", null, "(germ term)^2.0");
    assertQueryEquals("term^2.0", null, "term^2.0");
    assertQueryEquals("term^2", null, "term^2.0");
    assertQueryEquals("\"germ term\"^2.0", null, "\"germ term\"^2.0");
    assertQueryEquals("\"term germ\"^2", null, "\"term germ\"^2.0");

    assertQueryEquals("(foo OR bar) AND (baz OR boo)", null,
                      "+(foo bar) +(baz boo)");
    assertQueryEquals("((a OR b) AND NOT c) OR d", null,
                      "(+(a b) -c) d");
    assertQueryEquals("+(apple \"steve jobs\") -(foo bar baz)", null,
                      "+(apple \"steve jobs\") -(foo bar baz)");
    assertQueryEquals("+title:(dog OR cat) -author:\"bob dole\"", null,
                      "+(title:dog title:cat) -author:\"bob dole\"");
    
    PrecedenceQueryParser qp = new PrecedenceQueryParser("field", new MockAnalyzer());
    // make sure OR is the default:
    assertEquals(PrecedenceQueryParser.OR_OPERATOR, qp.getDefaultOperator());
    qp.setDefaultOperator(PrecedenceQueryParser.AND_OPERATOR);
    assertEquals(PrecedenceQueryParser.AND_OPERATOR, qp.getDefaultOperator());
    qp.setDefaultOperator(PrecedenceQueryParser.OR_OPERATOR);
    assertEquals(PrecedenceQueryParser.OR_OPERATOR, qp.getDefaultOperator());

    assertQueryEquals("a OR !b", null, "a (-b)");
    assertQueryEquals("a OR ! b", null, "a (-b)");
    assertQueryEquals("a OR -b", null, "a (-b)");
  }

  public void testPunct() throws Exception {
    Analyzer a = new MockAnalyzer(MockTokenizer.WHITESPACE, false);
    assertQueryEquals("a&b", a, "a&b");
    assertQueryEquals("a&&b", a, "a&&b");
    assertQueryEquals(".NET", a, ".NET");
  }

  public void testSlop() throws Exception {
    assertQueryEquals("\"term germ\"~2", null, "\"term germ\"~2");
    assertQueryEquals("\"term germ\"~2 flork", null, "\"term germ\"~2 flork");
    assertQueryEquals("\"term\"~2", null, "term");
    assertQueryEquals("\" \"~2 germ", null, "germ");
    assertQueryEquals("\"term germ\"~2^2", null, "\"term germ\"~2^2.0");
  }

  public void testNumber() throws Exception {
// The numbers go away because SimpleAnalzyer ignores them
    assertQueryEquals("3", null, "");
    assertQueryEquals("term 1.0 1 2", null, "term");
    assertQueryEquals("term term1 term2", null, "term term term");

    Analyzer a = new MockAnalyzer(MockTokenizer.WHITESPACE, true);
    assertQueryEquals("3", a, "3");
    assertQueryEquals("term 1.0 1 2", a, "term 1.0 1 2");
    assertQueryEquals("term term1 term2", a, "term term1 term2");
  }

  //individual CJK chars as terms, like StandardAnalyzer
  private class SimpleCJKTokenizer extends Tokenizer {
    private CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);

    public SimpleCJKTokenizer(Reader input) {
      super(input);
    }

    @Override
    public boolean incrementToken() throws IOException {
      int ch = input.read();
      if (ch < 0)
        return false;
      clearAttributes();
      termAtt.setEmpty().append((char) ch);
      return true;
    }
  }

  private class SimpleCJKAnalyzer extends Analyzer {
    @Override
    public TokenStream tokenStream(String fieldName, Reader reader) {
      return new SimpleCJKTokenizer(reader);
    }
  }
  
  public void testCJKTerm() throws Exception {
    // individual CJK chars as terms
    SimpleCJKAnalyzer analyzer = new SimpleCJKAnalyzer();
    
    BooleanQuery expected = new BooleanQuery();
    expected.add(new TermQuery(new Term("field", "中")), BooleanClause.Occur.SHOULD);
    expected.add(new TermQuery(new Term("field", "国")), BooleanClause.Occur.SHOULD);
    
    assertEquals(expected, getQuery("中国", analyzer));
  }
  
  public void testCJKBoostedTerm() throws Exception {
    // individual CJK chars as terms
    SimpleCJKAnalyzer analyzer = new SimpleCJKAnalyzer();
    
    BooleanQuery expected = new BooleanQuery();
    expected.setBoost(0.5f);
    expected.add(new TermQuery(new Term("field", "中")), BooleanClause.Occur.SHOULD);
    expected.add(new TermQuery(new Term("field", "国")), BooleanClause.Occur.SHOULD);
    
    assertEquals(expected, getQuery("中国^0.5", analyzer));
  }
  
  public void testCJKPhrase() throws Exception {
    // individual CJK chars as terms
    SimpleCJKAnalyzer analyzer = new SimpleCJKAnalyzer();
    
    PhraseQuery expected = new PhraseQuery();
    expected.add(new Term("field", "中"));
    expected.add(new Term("field", "国"));
    
    assertEquals(expected, getQuery("\"中国\"", analyzer));
  }
  
  public void testCJKBoostedPhrase() throws Exception {
    // individual CJK chars as terms
    SimpleCJKAnalyzer analyzer = new SimpleCJKAnalyzer();
    
    PhraseQuery expected = new PhraseQuery();
    expected.setBoost(0.5f);
    expected.add(new Term("field", "中"));
    expected.add(new Term("field", "国"));
    
    assertEquals(expected, getQuery("\"中国\"^0.5", analyzer));
  }
  
  public void testCJKSloppyPhrase() throws Exception {
    // individual CJK chars as terms
    SimpleCJKAnalyzer analyzer = new SimpleCJKAnalyzer(); 
    
    PhraseQuery expected = new PhraseQuery();
    expected.setSlop(3);
    expected.add(new Term("field", "中"));
    expected.add(new Term("field", "国"));
    
    assertEquals(expected, getQuery("\"中国\"~3", analyzer));
  }

  // failing tests disabled since PrecedenceQueryParser
  // is currently unmaintained
  public void _testWildcard() throws Exception {
    assertQueryEquals("term*", null, "term*");
    assertQueryEquals("term*^2", null, "term*^2.0");
    assertQueryEquals("term~", null, "term~0.5");
    assertQueryEquals("term~0.7", null, "term~0.7");
    assertQueryEquals("term~^2", null, "term^2.0~0.5");
    assertQueryEquals("term^2~", null, "term^2.0~0.5");
    assertQueryEquals("term*germ", null, "term*germ");
    assertQueryEquals("term*germ^3", null, "term*germ^3.0");

    assertTrue(getQuery("term*", null) instanceof PrefixQuery);
    assertTrue(getQuery("term*^2", null) instanceof PrefixQuery);
    assertTrue(getQuery("term~", null) instanceof FuzzyQuery);
    assertTrue(getQuery("term~0.7", null) instanceof FuzzyQuery);
    FuzzyQuery fq = (FuzzyQuery)getQuery("term~0.7", null);
    assertEquals(0.7f, fq.getMinSimilarity(), 0.1f);
    assertEquals(FuzzyQuery.defaultPrefixLength, fq.getPrefixLength());
    fq = (FuzzyQuery)getQuery("term~", null);
    assertEquals(0.5f, fq.getMinSimilarity(), 0.1f);
    assertEquals(FuzzyQuery.defaultPrefixLength, fq.getPrefixLength());
    try {
      getQuery("term~1.1", null);   // value > 1, throws exception
      fail();
    } catch(ParseException pe) {
      // expected exception
    }
    assertTrue(getQuery("term*germ", null) instanceof WildcardQuery);

/* Tests to see that wild card terms are (or are not) properly
	 * lower-cased with propery parser configuration
	 */
// First prefix queries:
    // by default, convert to lowercase:
    assertWildcardQueryEquals("Term*", true, "term*");
    // explicitly set lowercase:
    assertWildcardQueryEquals("term*", true, "term*");
    assertWildcardQueryEquals("Term*", true, "term*");
    assertWildcardQueryEquals("TERM*", true, "term*");
    // explicitly disable lowercase conversion:
    assertWildcardQueryEquals("term*", false, "term*");
    assertWildcardQueryEquals("Term*", false, "Term*");
    assertWildcardQueryEquals("TERM*", false, "TERM*");
// Then 'full' wildcard queries:
    // by default, convert to lowercase:
    assertWildcardQueryEquals("Te?m", "te?m");
    // explicitly set lowercase:
    assertWildcardQueryEquals("te?m", true, "te?m");
    assertWildcardQueryEquals("Te?m", true, "te?m");
    assertWildcardQueryEquals("TE?M", true, "te?m");
    assertWildcardQueryEquals("Te?m*gerM", true, "te?m*germ");
    // explicitly disable lowercase conversion:
    assertWildcardQueryEquals("te?m", false, "te?m");
    assertWildcardQueryEquals("Te?m", false, "Te?m");
    assertWildcardQueryEquals("TE?M", false, "TE?M");
    assertWildcardQueryEquals("Te?m*gerM", false, "Te?m*gerM");
//  Fuzzy queries:
    assertWildcardQueryEquals("Term~", "term~0.5");
    assertWildcardQueryEquals("Term~", true, "term~0.5");
    assertWildcardQueryEquals("Term~", false, "Term~0.5");
//  Range queries:
    assertWildcardQueryEquals("[A TO C]", "[a TO c]");
    assertWildcardQueryEquals("[A TO C]", true, "[a TO c]");
    assertWildcardQueryEquals("[A TO C]", false, "[A TO C]");
  }

  public void testQPA() throws Exception {
    assertQueryEquals("term term term", qpAnalyzer, "term term term");
    assertQueryEquals("term +stop term", qpAnalyzer, "term term");
    assertQueryEquals("term -stop term", qpAnalyzer, "term term");
    assertQueryEquals("drop AND stop AND roll", qpAnalyzer, "+drop +roll");
    assertQueryEquals("term phrase term", qpAnalyzer,
                      "term (phrase1 phrase2) term");
    // note the parens in this next assertion differ from the original
    // QueryParser behavior
    assertQueryEquals("term AND NOT phrase term", qpAnalyzer,
                      "(+term -(phrase1 phrase2)) term");
    assertQueryEquals("stop", qpAnalyzer, "");
    assertQueryEquals("stop OR stop AND stop", qpAnalyzer, "");
    assertTrue(getQuery("term term term", qpAnalyzer) instanceof BooleanQuery);
    assertTrue(getQuery("term +stop", qpAnalyzer) instanceof TermQuery);
  }

  public void testRange() throws Exception {
    assertQueryEquals("[ a TO z]", null, "[a TO z]");
    assertTrue(getQuery("[ a TO z]", null) instanceof TermRangeQuery);
    assertQueryEquals("[ a TO z ]", null, "[a TO z]");
    assertQueryEquals("{ a TO z}", null, "{a TO z}");
    assertQueryEquals("{ a TO z }", null, "{a TO z}");
    assertQueryEquals("{ a TO z }^2.0", null, "{a TO z}^2.0");
    assertQueryEquals("[ a TO z] OR bar", null, "[a TO z] bar");
    assertQueryEquals("[ a TO z] AND bar", null, "+[a TO z] +bar");
    assertQueryEquals("( bar blar { a TO z}) ", null, "bar blar {a TO z}");
    assertQueryEquals("gack ( bar blar { a TO z}) ", null, "gack (bar blar {a TO z})");
  }
  
  private String escapeDateString(String s) {
    if (s.contains(" ")) {
      return "\"" + s + "\"";
    } else {
      return s;
    }
  }

  public String getDate(String s) throws Exception {
    DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT);
    return DateTools.dateToString(df.parse(s), DateTools.Resolution.DAY);
  }

  public String getLocalizedDate(int year, int month, int day) {
    DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT);
    Calendar calendar = new GregorianCalendar();
    calendar.clear();
    calendar.set(year, month, day);
    calendar.set(Calendar.HOUR_OF_DAY, 23);
    calendar.set(Calendar.MINUTE, 59);
    calendar.set(Calendar.SECOND, 59);
    calendar.set(Calendar.MILLISECOND, 999);
    return df.format(calendar.getTime());
  }

  public void testDateRange() throws Exception {
    String startDate = getLocalizedDate(2002, 1, 1);
    String endDate = getLocalizedDate(2002, 1, 4);
    assertQueryEquals("[ " + escapeDateString(startDate) + " TO " + escapeDateString(endDate) + "]", null,
                      "[" + getDate(startDate) + " TO " + getDate(endDate) + "]");
    assertQueryEquals("{  " + escapeDateString(startDate) + "    " + escapeDateString(endDate) + "   }", null,
                      "{" + getDate(startDate) + " TO " + getDate(endDate) + "}");
  }

  public void testEscaped() throws Exception {
    Analyzer a = new MockAnalyzer(MockTokenizer.WHITESPACE, false);
    
    /*assertQueryEquals("\\[brackets", a, "\\[brackets");
    assertQueryEquals("\\[brackets", null, "brackets");
    assertQueryEquals("\\\\", a, "\\\\");
    assertQueryEquals("\\+blah", a, "\\+blah");
    assertQueryEquals("\\(blah", a, "\\(blah");

    assertQueryEquals("\\-blah", a, "\\-blah");
    assertQueryEquals("\\!blah", a, "\\!blah");
    assertQueryEquals("\\{blah", a, "\\{blah");
    assertQueryEquals("\\}blah", a, "\\}blah");
    assertQueryEquals("\\:blah", a, "\\:blah");
    assertQueryEquals("\\^blah", a, "\\^blah");
    assertQueryEquals("\\[blah", a, "\\[blah");
    assertQueryEquals("\\]blah", a, "\\]blah");
    assertQueryEquals("\\\"blah", a, "\\\"blah");
    assertQueryEquals("\\(blah", a, "\\(blah");
    assertQueryEquals("\\)blah", a, "\\)blah");
    assertQueryEquals("\\~blah", a, "\\~blah");
    assertQueryEquals("\\*blah", a, "\\*blah");
    assertQueryEquals("\\?blah", a, "\\?blah");
    //assertQueryEquals("foo \\&\\& bar", a, "foo \\&\\& bar");
    //assertQueryEquals("foo \\|| bar", a, "foo \\|| bar");
    //assertQueryEquals("foo \\AND bar", a, "foo \\AND bar");*/

    assertQueryEquals("a\\-b:c", a, "a-b:c");
    assertQueryEquals("a\\+b:c", a, "a+b:c");
    assertQueryEquals("a\\:b:c", a, "a:b:c");
    assertQueryEquals("a\\\\b:c", a, "a\\b:c");

    assertQueryEquals("a:b\\-c", a, "a:b-c");
    assertQueryEquals("a:b\\+c", a, "a:b+c");
    assertQueryEquals("a:b\\:c", a, "a:b:c");
    assertQueryEquals("a:b\\\\c", a, "a:b\\c");

    assertQueryEquals("a:b\\-c*", a, "a:b-c*");
    assertQueryEquals("a:b\\+c*", a, "a:b+c*");
    assertQueryEquals("a:b\\:c*", a, "a:b:c*");

    assertQueryEquals("a:b\\\\c*", a, "a:b\\c*");

    assertQueryEquals("a:b\\-?c", a, "a:b-?c");
    assertQueryEquals("a:b\\+?c", a, "a:b+?c");
    assertQueryEquals("a:b\\:?c", a, "a:b:?c");

    assertQueryEquals("a:b\\\\?c", a, "a:b\\?c");

    assertQueryEquals("a:b\\-c~", a, "a:b-c~0.5");
    assertQueryEquals("a:b\\+c~", a, "a:b+c~0.5");
    assertQueryEquals("a:b\\:c~", a, "a:b:c~0.5");
    assertQueryEquals("a:b\\\\c~", a, "a:b\\c~0.5");

    assertQueryEquals("[ a\\- TO a\\+ ]", null, "[a- TO a+]");
    assertQueryEquals("[ a\\: TO a\\~ ]", null, "[a: TO a~]");
    assertQueryEquals("[ a\\\\ TO a\\* ]", null, "[a\\ TO a*]");
  }

  public void testTabNewlineCarriageReturn()
    throws Exception {
    assertQueryEqualsDOA("+weltbank +worlbank", null,
      "+weltbank +worlbank");

    assertQueryEqualsDOA("+weltbank\n+worlbank", null,
      "+weltbank +worlbank");
    assertQueryEqualsDOA("weltbank \n+worlbank", null,
      "+weltbank +worlbank");
    assertQueryEqualsDOA("weltbank \n +worlbank", null,
      "+weltbank +worlbank");

    assertQueryEqualsDOA("+weltbank\r+worlbank", null,
      "+weltbank +worlbank");
    assertQueryEqualsDOA("weltbank \r+worlbank", null,
      "+weltbank +worlbank");
    assertQueryEqualsDOA("weltbank \r +worlbank", null,
      "+weltbank +worlbank");

    assertQueryEqualsDOA("+weltbank\r\n+worlbank", null,
      "+weltbank +worlbank");
    assertQueryEqualsDOA("weltbank \r\n+worlbank", null,
      "+weltbank +worlbank");
    assertQueryEqualsDOA("weltbank \r\n +worlbank", null,
      "+weltbank +worlbank");
    assertQueryEqualsDOA("weltbank \r \n +worlbank", null,
      "+weltbank +worlbank");

    assertQueryEqualsDOA("+weltbank\t+worlbank", null,
      "+weltbank +worlbank");
    assertQueryEqualsDOA("weltbank \t+worlbank", null,
      "+weltbank +worlbank");
    assertQueryEqualsDOA("weltbank \t +worlbank", null,
      "+weltbank +worlbank");
  }

  public void testSimpleDAO()
    throws Exception {
    assertQueryEqualsDOA("term term term", null, "+term +term +term");
    assertQueryEqualsDOA("term +term term", null, "+term +term +term");
    assertQueryEqualsDOA("term term +term", null, "+term +term +term");
    assertQueryEqualsDOA("term +term +term", null, "+term +term +term");
    assertQueryEqualsDOA("-term term term", null, "-term +term +term");
  }

  public void testBoost()
    throws Exception {
    CharacterRunAutomaton stopSet = new CharacterRunAutomaton(BasicAutomata.makeString("on"));
    Analyzer oneStopAnalyzer = new MockAnalyzer(MockTokenizer.SIMPLE, true, stopSet, true);
    PrecedenceQueryParser qp = new PrecedenceQueryParser("field", oneStopAnalyzer);
    Query q = qp.parse("on^1.0");
    assertNotNull(q);
    q = qp.parse("\"hello\"^2.0");
    assertNotNull(q);
    assertEquals(q.getBoost(), (float) 2.0, (float) 0.5);
    q = qp.parse("hello^2.0");
    assertNotNull(q);
    assertEquals(q.getBoost(), (float) 2.0, (float) 0.5);
    q = qp.parse("\"on\"^1.0");
    assertNotNull(q);

    q = getParser(new MockAnalyzer(MockTokenizer.SIMPLE, true, MockTokenFilter.ENGLISH_STOPSET, true)).parse("the^3");
    assertNotNull(q);
  }

  public void testException() throws Exception {
    try {
      assertQueryEquals("\"some phrase", null, "abc");
      fail("ParseException expected, not thrown");
    } catch (ParseException expected) {
    }
  }

  public void testCustomQueryParserWildcard() {
    try {
      new QPTestParser("contents", new MockAnalyzer(MockTokenizer.WHITESPACE, false)).parse("a?t");
    } catch (ParseException expected) {
      return;
    }
    fail("Wildcard queries should not be allowed");
  }

  public void testCustomQueryParserFuzzy() throws Exception {
    try {
      new QPTestParser("contents", new MockAnalyzer(MockTokenizer.WHITESPACE, false)).parse("xunit~");
    } catch (ParseException expected) {
      return;
    }
    fail("Fuzzy queries should not be allowed");
  }

  public void testBooleanQuery() throws Exception {
    BooleanQuery.setMaxClauseCount(2);
    try {
      getParser(new MockAnalyzer(MockTokenizer.WHITESPACE, false)).parse("one two three");
      fail("ParseException expected due to too many boolean clauses");
    } catch (ParseException expected) {
      // too many boolean clauses, so ParseException is expected
    }
  }

  /**
   * This test differs from the original QueryParser, showing how the
   * precedence issue has been corrected.
   */
  // failing tests disabled since PrecedenceQueryParser
  // is currently unmaintained
  public void _testPrecedence() throws Exception {
    PrecedenceQueryParser parser = getParser(new MockAnalyzer(MockTokenizer.WHITESPACE, false));
    Query query1 = parser.parse("A AND B OR C AND D");
    Query query2 = parser.parse("(A AND B) OR (C AND D)");
    assertEquals(query1, query2);

    query1 = parser.parse("A OR B C");
    query2 = parser.parse("A B C");
    assertEquals(query1, query2);

    query1 = parser.parse("A AND B C");
    query2 = parser.parse("(+A +B) C");
    assertEquals(query1, query2);

    query1 = parser.parse("A AND NOT B");
    query2 = parser.parse("+A -B");
    assertEquals(query1, query2);

    query1 = parser.parse("A OR NOT B");
    query2 = parser.parse("A -B");
    assertEquals(query1, query2);

    query1 = parser.parse("A OR NOT B AND C");
    query2 = parser.parse("A (-B +C)");
    assertEquals(query1, query2);
  }
  
  public void testRegexps() throws Exception {
    PrecedenceQueryParser qp =  getParser(new MockAnalyzer(MockTokenizer.WHITESPACE, false));
    RegexpQuery q = new RegexpQuery(new Term("field", "[a-z][123]"));
    assertEquals(q, qp.parse("/[a-z][123]/"));
    qp.setLowercaseExpandedTerms(true);
    assertEquals(q, qp.parse("/[A-Z][123]/"));
    q.setBoost(0.5f);
    assertEquals(q, qp.parse("/[A-Z][123]/^0.5"));
    qp.setMultiTermRewriteMethod(MultiTermQuery.SCORING_BOOLEAN_QUERY_REWRITE);
    q.setRewriteMethod(MultiTermQuery.SCORING_BOOLEAN_QUERY_REWRITE);
    assertTrue(qp.parse("/[A-Z][123]/^0.5") instanceof RegexpQuery);
    assertEquals(MultiTermQuery.SCORING_BOOLEAN_QUERY_REWRITE, ((RegexpQuery)qp.parse("/[A-Z][123]/^0.5")).getRewriteMethod());
    assertEquals(q, qp.parse("/[A-Z][123]/^0.5"));
    qp.setMultiTermRewriteMethod(MultiTermQuery.CONSTANT_SCORE_AUTO_REWRITE_DEFAULT);
    
    Query escaped = new RegexpQuery(new Term("field", "[a-z]\\/[123]"));
    assertEquals(escaped, qp.parse("/[a-z]\\/[123]/"));
    Query escaped2 = new RegexpQuery(new Term("field", "[a-z]\\*[123]"));
    assertEquals(escaped2, qp.parse("/[a-z]\\*[123]/"));
    
    BooleanQuery complex = new BooleanQuery();
    BooleanQuery inner = new BooleanQuery();
    inner.add(new RegexpQuery(new Term("field", "[a-z]\\/[123]")), Occur.MUST);
    inner.add(new TermQuery(new Term("path", "/etc/init.d/")), Occur.MUST);
    complex.add(inner, Occur.SHOULD);
    complex.add(new TermQuery(new Term("field", "/etc/init[.]d/lucene/")), Occur.SHOULD);
    assertEquals(complex, qp.parse("/[a-z]\\/[123]/ AND path:/etc/init.d/ OR /etc\\/init\\[.\\]d/lucene/ "));
  }


  @Override
  protected void tearDown() throws Exception {
    BooleanQuery.setMaxClauseCount(originalMaxClauses);
    super.tearDown();
  }

}
