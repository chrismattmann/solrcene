package org.apache.lucene.wordnet;

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

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.util.AttributeSource;

/**
 * Injects additional tokens for synonyms of token terms fetched from the
 * underlying child stream; the child stream must deliver lowercase tokens
 * for synonyms to be found.
 *
 */
public class SynonymTokenFilter extends TokenFilter {
    
  /** The Token.type used to indicate a synonym to higher level filters. */
  public static final String SYNONYM_TOKEN_TYPE = "SYNONYM";

  private final SynonymMap synonyms;
  private final int maxSynonyms;
  
  private String[] stack = null;
  private int index = 0;
  private AttributeSource.State current = null;
  private int todo = 0;
  
  private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
  private final TypeAttribute typeAtt = addAttribute(TypeAttribute.class);
  private final PositionIncrementAttribute posIncrAtt = addAttribute(PositionIncrementAttribute.class);
  
  /**
   * Creates an instance for the given underlying stream and synonym table.
   * 
   * @param input
   *            the underlying child token stream
   * @param synonyms
   *            the map used to extract synonyms for terms
   * @param maxSynonyms
   *            the maximum number of synonym tokens to return per underlying
   *            token word (a value of Integer.MAX_VALUE indicates unlimited)
   */
  public SynonymTokenFilter(TokenStream input, SynonymMap synonyms, int maxSynonyms) {
    super(input);
    if (input == null)
      throw new IllegalArgumentException("input must not be null");
    if (synonyms == null)
      throw new IllegalArgumentException("synonyms must not be null");
    if (maxSynonyms < 0) 
      throw new IllegalArgumentException("maxSynonyms must not be negative");
    
    this.synonyms = synonyms;
    this.maxSynonyms = maxSynonyms;
  }
  
  /** Returns the next token in the stream, or null at EOS. */
  @Override
  public final boolean incrementToken() throws IOException {
    while (todo > 0 && index < stack.length) { // pop from stack
      if (createToken(stack[index++], current)) {
        todo--;
        return true;
      }
    }
    
    if (!input.incrementToken()) return false; // EOS; iterator exhausted 
    
    stack = synonyms.getSynonyms(termAtt.toString()); // push onto stack
    if (stack.length > maxSynonyms) randomize(stack);
    index = 0;
    current = captureState();
    todo = maxSynonyms;
    return true;
  }
  
  /**
   * Creates and returns a token for the given synonym of the current input
   * token; Override for custom (stateless or stateful) behavior, if desired.
   * 
   * @param synonym 
   *            a synonym for the current token's term
   * @param current
   *            the current token from the underlying child stream
   * @return a new token, or null to indicate that the given synonym should be
   *         ignored
   */
  protected boolean createToken(String synonym, AttributeSource.State current) {
    restoreState(current);
    termAtt.setEmpty().append(synonym);
    typeAtt.setType(SYNONYM_TOKEN_TYPE);
    posIncrAtt.setPositionIncrement(0);
    return true;
  }
  
  /**
   * Randomize synonyms to later sample a subset. Uses constant random seed
   * for reproducibility. Uses "DRand", a simple, fast, uniform pseudo-random
   * number generator with medium statistical quality (multiplicative
   * congruential method), producing integers in the range [Integer.MIN_VALUE,
   * Integer.MAX_VALUE].
   */
  private static void randomize(Object[] arr) {
    int seed = 1234567; // constant
    int randomState = 4*seed + 1;
//    Random random = new Random(seed); // unnecessary overhead
    int len = arr.length;
    for (int i=0; i < len-1; i++) {
      randomState *= 0x278DDE6D; // z(i+1)=a*z(i) (mod 2**32)
      int r = randomState % (len-i);
      if (r < 0) r = -r; // e.g. -9 % 2 == -1
//      int r = random.nextInt(len-i);
      
      // swap arr[i, i+r]
      Object tmp = arr[i];
      arr[i] = arr[i + r];
      arr[i + r] = tmp;
    }   
  }

  @Override
  public void reset() throws IOException {
    super.reset();
    stack = null;
    index = 0;
    current = null;
    todo = 0;
  }
}
