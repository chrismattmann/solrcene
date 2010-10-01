package org.apache.lucene.analysis.miscellaneous;

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

import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.StopAnalyzer;

/**
 * Verifies the behavior of PatternAnalyzer.
 */
public class PatternAnalyzerTest extends BaseTokenStreamTestCase {

  /**
   * Test PatternAnalyzer when it is configured with a non-word pattern.
   * Behavior can be similar to SimpleAnalyzer (depending upon options)
   */
  public void testNonWordPattern() throws IOException {
    // Split on non-letter pattern, do not lowercase, no stopwords
    PatternAnalyzer a = new PatternAnalyzer(TEST_VERSION_CURRENT, PatternAnalyzer.NON_WORD_PATTERN,
        false, null);
    check(a, "The quick brown Fox,the abcd1234 (56.78) dc.", new String[] {
        "The", "quick", "brown", "Fox", "the", "abcd", "dc" });

    // split on non-letter pattern, lowercase, english stopwords
    PatternAnalyzer b = new PatternAnalyzer(TEST_VERSION_CURRENT, PatternAnalyzer.NON_WORD_PATTERN,
        true, StopAnalyzer.ENGLISH_STOP_WORDS_SET);
    check(b, "The quick brown Fox,the abcd1234 (56.78) dc.", new String[] {
        "quick", "brown", "fox", "abcd", "dc" });
  }

  /**
   * Test PatternAnalyzer when it is configured with a whitespace pattern.
   * Behavior can be similar to WhitespaceAnalyzer (depending upon options)
   */
  public void testWhitespacePattern() throws IOException {
    // Split on whitespace patterns, do not lowercase, no stopwords
    PatternAnalyzer a = new PatternAnalyzer(TEST_VERSION_CURRENT, PatternAnalyzer.WHITESPACE_PATTERN,
        false, null);
    check(a, "The quick brown Fox,the abcd1234 (56.78) dc.", new String[] {
        "The", "quick", "brown", "Fox,the", "abcd1234", "(56.78)", "dc." });

    // Split on whitespace patterns, lowercase, english stopwords
    PatternAnalyzer b = new PatternAnalyzer(TEST_VERSION_CURRENT, PatternAnalyzer.WHITESPACE_PATTERN,
        true, StopAnalyzer.ENGLISH_STOP_WORDS_SET);
    check(b, "The quick brown Fox,the abcd1234 (56.78) dc.", new String[] {
        "quick", "brown", "fox,the", "abcd1234", "(56.78)", "dc." });
  }

  /**
   * Test PatternAnalyzer when it is configured with a custom pattern. In this
   * case, text is tokenized on the comma ","
   */
  public void testCustomPattern() throws IOException {
    // Split on comma, do not lowercase, no stopwords
    PatternAnalyzer a = new PatternAnalyzer(TEST_VERSION_CURRENT, Pattern.compile(","), false, null);
    check(a, "Here,Are,some,Comma,separated,words,", new String[] { "Here",
        "Are", "some", "Comma", "separated", "words" });

    // split on comma, lowercase, english stopwords
    PatternAnalyzer b = new PatternAnalyzer(TEST_VERSION_CURRENT, Pattern.compile(","), true,
        StopAnalyzer.ENGLISH_STOP_WORDS_SET);
    check(b, "Here,Are,some,Comma,separated,words,", new String[] { "here",
        "some", "comma", "separated", "words" });
  }

  /**
   * Test PatternAnalyzer against a large document.
   */
  public void testHugeDocument() throws IOException {
    StringBuilder document = new StringBuilder();
    // 5000 a's
    char largeWord[] = new char[5000];
    Arrays.fill(largeWord, 'a');
    document.append(largeWord);

    // a space
    document.append(' ');

    // 2000 b's
    char largeWord2[] = new char[2000];
    Arrays.fill(largeWord2, 'b');
    document.append(largeWord2);

    // Split on whitespace patterns, do not lowercase, no stopwords
    PatternAnalyzer a = new PatternAnalyzer(TEST_VERSION_CURRENT, PatternAnalyzer.WHITESPACE_PATTERN,
        false, null);
    check(a, document.toString(), new String[] { new String(largeWord),
        new String(largeWord2) });
  }

  /**
   * Verify the analyzer analyzes to the expected contents. For PatternAnalyzer,
   * several methods are verified:
   * <ul>
   * <li>Analysis with a normal Reader
   * <li>Analysis with a FastStringReader
   * <li>Analysis with a String
   * </ul>
   */
  private void check(PatternAnalyzer analyzer, String document,
      String expected[]) throws IOException {
    // ordinary analysis of a Reader
    assertAnalyzesTo(analyzer, document, expected);

    // analysis with a "FastStringReader"
    TokenStream ts = analyzer.tokenStream("dummy",
        new PatternAnalyzer.FastStringReader(document));
    assertTokenStreamContents(ts, expected);

    // analysis of a String, uses PatternAnalyzer.tokenStream(String, String)
    TokenStream ts2 = analyzer.tokenStream("dummy", document);
    assertTokenStreamContents(ts2, expected);
  }
}
