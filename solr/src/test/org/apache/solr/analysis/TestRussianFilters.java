package org.apache.solr.analysis;

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

import java.io.Reader;
import java.io.StringReader;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;

/**
 * Simple tests to ensure the Russian filter factories are working.
 */
public class TestRussianFilters extends BaseTokenTestCase {
  /**
   * Test RussianLetterTokenizerFactory
   */
  public void testTokenizer() throws Exception {
    Reader reader = new StringReader("Вместе с тем о силе электромагнитной 100");
    RussianLetterTokenizerFactory factory = new RussianLetterTokenizerFactory();
    factory.init(DEFAULT_VERSION_PARAM);
    Tokenizer stream = factory.create(reader);
    assertTokenStreamContents(stream, new String[] {"Вместе", "с", "тем", "о",
        "силе", "электромагнитной", "100"});
  }
  
  /**
   * Test RussianLowerCaseFilterFactory
   */
  public void testLowerCase() throws Exception {
    Reader reader = new StringReader("Вместе с тем о силе электромагнитной 100");
    RussianLetterTokenizerFactory factory = new RussianLetterTokenizerFactory();
    factory.init(DEFAULT_VERSION_PARAM);
    RussianLowerCaseFilterFactory filterFactory = new RussianLowerCaseFilterFactory();
    filterFactory.init(DEFAULT_VERSION_PARAM);
    Tokenizer tokenizer = factory.create(reader);
    TokenStream stream = filterFactory.create(tokenizer);
    assertTokenStreamContents(stream, new String[] {"вместе", "с", "тем", "о",
        "силе", "электромагнитной", "100"});
  }
  
  /**
   * Test RussianStemFilterFactory
   */
  public void testStemmer() throws Exception {
    Reader reader = new StringReader("Вместе с тем о силе электромагнитной 100");
    RussianLetterTokenizerFactory factory = new RussianLetterTokenizerFactory();
    factory.init(DEFAULT_VERSION_PARAM);
    RussianLowerCaseFilterFactory caseFactory = new RussianLowerCaseFilterFactory();
    caseFactory.init(DEFAULT_VERSION_PARAM);
    RussianStemFilterFactory stemFactory = new RussianStemFilterFactory();
    stemFactory.init(DEFAULT_VERSION_PARAM);
    Tokenizer tokenizer = factory.create(reader);
    TokenStream stream = caseFactory.create(tokenizer);
    stream = stemFactory.create(stream);
    assertTokenStreamContents(stream, new String[] {"вмест", "с", "тем", "о",
        "сил", "электромагнитн", "100"});
  }
}
