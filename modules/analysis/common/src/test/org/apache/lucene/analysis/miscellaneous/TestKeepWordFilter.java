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

package org.apache.lucene.analysis.miscellaneous;

import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;

import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;

/** Test {@link KeepWordFilter} */
public class TestKeepWordFilter extends BaseTokenStreamTestCase {
  
  public void testStopAndGo() throws Exception 
  {  
    Set<String> words = new HashSet<String>();
    words.add( "aaa" );
    words.add( "bbb" );
    
    String input = "aaa BBB ccc ddd EEE";
    
    // Test Stopwords
    TokenStream stream = new WhitespaceTokenizer(TEST_VERSION_CURRENT, new StringReader(input));
    stream = new KeepWordFilter(stream, words, true);
    assertTokenStreamContents(stream, new String[] { "aaa", "BBB" });
       
    // Now force case
    stream = new WhitespaceTokenizer(TEST_VERSION_CURRENT, new StringReader(input));
    stream = new KeepWordFilter(stream, words, false);
    assertTokenStreamContents(stream, new String[] { "aaa" });
  }
}
