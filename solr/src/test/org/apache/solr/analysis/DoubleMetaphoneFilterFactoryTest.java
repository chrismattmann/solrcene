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
package org.apache.solr.analysis;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.phonetic.DoubleMetaphoneFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

public class DoubleMetaphoneFilterFactoryTest extends BaseTokenTestCase {

  public void testDefaults() throws Exception {
    DoubleMetaphoneFilterFactory factory = new DoubleMetaphoneFilterFactory();
    factory.init(new HashMap<String, String>());
    TokenStream inputStream = new WhitespaceTokenizer(DEFAULT_VERSION, new StringReader("international"));

    TokenStream filteredStream = factory.create(inputStream);
    assertEquals(DoubleMetaphoneFilter.class, filteredStream.getClass());
    assertTokenStreamContents(filteredStream, new String[] { "international", "ANTR" });
  }

  public void testSettingSizeAndInject() throws Exception {
    DoubleMetaphoneFilterFactory factory = new DoubleMetaphoneFilterFactory();
    Map<String, String> parameters = new HashMap<String, String>();
    parameters.put("inject", "false");
    parameters.put("maxCodeLength", "8");
    factory.init(parameters);

    TokenStream inputStream = new WhitespaceTokenizer(DEFAULT_VERSION, new StringReader("international"));

    TokenStream filteredStream = factory.create(inputStream);
    assertEquals(DoubleMetaphoneFilter.class, filteredStream.getClass());
    assertTokenStreamContents(filteredStream, new String[] { "ANTRNXNL" });
  }
  
  /**
   * Ensure that reset() removes any state (buffered tokens)
   */
  public void testReset() throws Exception {
    DoubleMetaphoneFilterFactory factory = new DoubleMetaphoneFilterFactory();
    factory.init(new HashMap<String, String>());
    TokenStream inputStream = new WhitespaceTokenizer(DEFAULT_VERSION, new StringReader("international"));

    TokenStream filteredStream = factory.create(inputStream);
    CharTermAttribute termAtt = filteredStream.addAttribute(CharTermAttribute.class);
    assertEquals(DoubleMetaphoneFilter.class, filteredStream.getClass());
    
    assertTrue(filteredStream.incrementToken());
    assertEquals(13, termAtt.length());
    assertEquals("international", termAtt.toString());
    filteredStream.reset();
    
    // ensure there are no more tokens, such as ANTRNXNL
    assertFalse(filteredStream.incrementToken());
  }
}
