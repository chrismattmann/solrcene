package org.apache.solr.analysis;

/**
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.solr.common.ResourceLoader;
import org.apache.solr.common.util.StrUtils;
import org.apache.solr.core.SolrResourceLoader;
import org.tartarus.snowball.ext.EnglishStemmer;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;

public class SnowballPorterFilterFactoryTest extends BaseTokenTestCase {

  public void test() throws IOException {
    EnglishStemmer stemmer = new EnglishStemmer();
    String[] test = {"The", "fledgling", "banks", "were", "counting", "on", "a", "big", "boom", "in", "banking"};
    String[] gold = new String[test.length];
    for (int i = 0; i < test.length; i++) {
      stemmer.setCurrent(test[i]);
      stemmer.stem();
      gold[i] = stemmer.getCurrent();
    }

    SnowballPorterFilterFactory factory = new SnowballPorterFilterFactory();
    Map<String, String> args = new HashMap<String, String>(DEFAULT_VERSION_PARAM);
    args.put("language", "English");

    factory.init(args);
    factory.inform(new LinesMockSolrResourceLoader(new ArrayList<String>()));
    Tokenizer tokenizer = new WhitespaceTokenizer(DEFAULT_VERSION,
        new StringReader(StrUtils.join(Arrays.asList(test), ' ')));
    TokenStream stream = factory.create(tokenizer);
    assertTokenStreamContents(stream, gold);
  }

  /**
   * Tests the protected words mechanism of EnglishPorterFilterFactory
   */
  @Deprecated
  public void testProtectedOld() throws Exception {
    EnglishStemmer stemmer = new EnglishStemmer();
    String[] test = {"The", "fledgling", "banks", "were", "counting", "on", "a", "big", "boom", "in", "banking"};
    String[] gold = new String[test.length];
    for (int i = 0; i < test.length; i++) {
      if (test[i].equals("fledgling") == false && test[i].equals("banks") == false) {
        stemmer.setCurrent(test[i]);
        stemmer.stem();
        gold[i] = stemmer.getCurrent();
      } else {
        gold[i] = test[i];
      }
    }

    EnglishPorterFilterFactory factory = new EnglishPorterFilterFactory();
    Map<String, String> args = new HashMap<String, String>(DEFAULT_VERSION_PARAM);
    args.put(SnowballPorterFilterFactory.PROTECTED_TOKENS, "who-cares.txt");
    factory.init(args);
    List<String> lines = new ArrayList<String>();
    Collections.addAll(lines, "banks", "fledgling");
    factory.inform(new LinesMockSolrResourceLoader(lines));
    Tokenizer tokenizer = new WhitespaceTokenizer(DEFAULT_VERSION,
        new StringReader(StrUtils.join(Arrays.asList(test), ' ')));
    TokenStream stream = factory.create(tokenizer);
    assertTokenStreamContents(stream, gold);
  }

  class LinesMockSolrResourceLoader implements ResourceLoader {
    List<String> lines;

    LinesMockSolrResourceLoader(List<String> lines) {
      this.lines = lines;
    }

    public List<String> getLines(String resource) throws IOException {
      return lines;
    }

    public Object newInstance(String cname, String... subpackages) {
      return null;
    }

    public InputStream openResource(String resource) throws IOException {
      return null;
    }
  }
  
  /**
   * Test the protected words mechanism of SnowballPorterFilterFactory
   */
  public void testProtected() throws Exception {
    SnowballPorterFilterFactory factory = new SnowballPorterFilterFactory();
    ResourceLoader loader = new SolrResourceLoader(null, null);
    Map<String,String> args = new HashMap<String,String>(DEFAULT_VERSION_PARAM);
    args.put("protected", "protwords.txt");
    args.put("language", "English");
    factory.init(args);
    factory.inform(loader);
    Reader reader = new StringReader("ridding of some stemming");
    Tokenizer tokenizer = new WhitespaceTokenizer(DEFAULT_VERSION, reader);
    TokenStream stream = factory.create(tokenizer);
    assertTokenStreamContents(stream, new String[] { "ridding", "of", "some", "stem" });
  }
}

