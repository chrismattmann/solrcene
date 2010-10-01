package org.apache.lucene.analysis.ru;

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
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/**
 * Normalizes token text to lower case.
 * @deprecated Use {@link LowerCaseFilter} instead, which has the same
 *  functionality. This filter will be removed in Lucene 4.0
 */
@Deprecated
public final class RussianLowerCaseFilter extends TokenFilter
{
    private CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
   
    public RussianLowerCaseFilter(TokenStream in)
    {
        super(in);
    }

    @Override
    public final boolean incrementToken() throws IOException
    {
      if (input.incrementToken()) {
        char[] chArray = termAtt.buffer();
        int chLen = termAtt.length();
        for (int i = 0; i < chLen; i++)
        {
          chArray[i] = Character.toLowerCase(chArray[i]);
        }
        return true;
      } else {
        return false;
      }
    }
}
