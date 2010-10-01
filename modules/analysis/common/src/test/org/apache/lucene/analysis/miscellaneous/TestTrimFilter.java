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

import java.io.IOException;
import java.util.Collection;

import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.FlagsAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;

/**
 * @version $Id:$
 */
public class TestTrimFilter extends BaseTokenStreamTestCase {

  public void testTrim() throws Exception {
    char[] a = " a ".toCharArray();
    char[] b = "b   ".toCharArray();
    char[] ccc = "cCc".toCharArray();
    char[] whitespace = "   ".toCharArray();
    char[] empty = "".toCharArray();

    TokenStream ts = new IterTokenStream(new Token(a, 0, a.length, 1, 5),
                    new Token(b, 0, b.length, 6, 10),
                    new Token(ccc, 0, ccc.length, 11, 15),
                    new Token(whitespace, 0, whitespace.length, 16, 20),
                    new Token(empty, 0, empty.length, 21, 21));
    ts = new TrimFilter(ts, false);

    assertTokenStreamContents(ts, new String[] { "a", "b", "cCc", "", ""});

    a = " a".toCharArray();
    b = "b ".toCharArray();
    ccc = " c ".toCharArray();
    whitespace = "   ".toCharArray();
    ts = new IterTokenStream(
            new Token(a, 0, a.length, 0, 2),
            new Token(b, 0, b.length, 0, 2),
            new Token(ccc, 0, ccc.length, 0, 3),
            new Token(whitespace, 0, whitespace.length, 0, 3));
    ts = new TrimFilter(ts, true);
    
    assertTokenStreamContents(ts, 
        new String[] { "a", "b", "c", "" },
        new int[] { 1, 0, 1, 3 },
        new int[] { 2, 1, 2, 3 },
        new int[] { 1, 1, 1, 1 });
  }
  
  /**
   * @deprecated does not support custom attributes
   */
  private static class IterTokenStream extends TokenStream {
    final Token tokens[];
    int index = 0;
    CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);
    PositionIncrementAttribute posIncAtt = addAttribute(PositionIncrementAttribute.class);
    FlagsAttribute flagsAtt = addAttribute(FlagsAttribute.class);
    TypeAttribute typeAtt = addAttribute(TypeAttribute.class);
    PayloadAttribute payloadAtt = addAttribute(PayloadAttribute.class);
    
    public IterTokenStream(Token... tokens) {
      super();
      this.tokens = tokens;
    }
    
    public IterTokenStream(Collection<Token> tokens) {
      this(tokens.toArray(new Token[tokens.size()]));
    }
    
    public boolean incrementToken() throws IOException {
      if (index >= tokens.length)
        return false;
      else {
        clearAttributes();
        Token token = tokens[index++];
        termAtt.setEmpty().append(token);
        offsetAtt.setOffset(token.startOffset(), token.endOffset());
        posIncAtt.setPositionIncrement(token.getPositionIncrement());
        flagsAtt.setFlags(token.getFlags());
        typeAtt.setType(token.type());
        payloadAtt.setPayload(token.getPayload());
        return true;
      }
    }
  }
}
