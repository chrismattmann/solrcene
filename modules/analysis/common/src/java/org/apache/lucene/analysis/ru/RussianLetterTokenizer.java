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

import java.io.Reader;
import org.apache.lucene.analysis.CharTokenizer;
import org.apache.lucene.analysis.Tokenizer; // for javadocs
import org.apache.lucene.analysis.core.LetterTokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer; // for javadocs
import org.apache.lucene.util.AttributeSource;
import org.apache.lucene.util.Version;

/**
 * A RussianLetterTokenizer is a {@link Tokenizer} that extends {@link LetterTokenizer}
 * by also allowing the basic Latin digits 0-9.
 * <p>
 * <a name="version"/>
 * You must specify the required {@link Version} compatibility when creating
 * {@link RussianLetterTokenizer}:
 * <ul>
 * <li>As of 3.1, {@link CharTokenizer} uses an int based API to normalize and
 * detect token characters. See {@link CharTokenizer#isTokenChar(int)} and
 * {@link CharTokenizer#normalize(int)} for details.</li>
 * </ul>
 * @deprecated Use {@link StandardTokenizer} instead, which has the same functionality.
 * This filter will be removed in Lucene 4.0 
 */
@Deprecated
public class RussianLetterTokenizer extends CharTokenizer
{    
    private static final int DIGIT_0 = '0';
    private static final int DIGIT_9 = '9';
    
    /**
     * Construct a new RussianLetterTokenizer. * @param matchVersion Lucene version
     * to match See {@link <a href="#version">above</a>}
     * 
     * @param in
     *          the input to split up into tokens
     */
    public RussianLetterTokenizer(Version matchVersion, Reader in) {
      super(matchVersion, in);
    }

    /**
     * Construct a new RussianLetterTokenizer using a given {@link AttributeSource}.
     * 
     * @param matchVersion
     *          Lucene version to match See {@link <a href="#version">above</a>}
     * @param source
     *          the attribute source to use for this {@link Tokenizer}
     * @param in
     *          the input to split up into tokens
     */
    public RussianLetterTokenizer(Version matchVersion, AttributeSource source, Reader in) {
      super(matchVersion, source, in);
    }

    /**
     * Construct a new RussianLetterTokenizer using a given
     * {@link org.apache.lucene.util.AttributeSource.AttributeFactory}. * @param
     * matchVersion Lucene version to match See
     * {@link <a href="#version">above</a>}
     * 
     * @param factory
     *          the attribute factory to use for this {@link Tokenizer}
     * @param in
     *          the input to split up into tokens
     */
    public RussianLetterTokenizer(Version matchVersion, AttributeFactory factory, Reader in) {
      super(matchVersion, factory, in);
    }
    
    /**
     * Construct a new RussianLetterTokenizer.
     * 
     * @deprecated use {@link #RussianLetterTokenizer(Version, Reader)} instead. This will
     *             be removed in Lucene 4.0.
     */
    @Deprecated
    public RussianLetterTokenizer(Reader in) {
      super(in);
    }

    /**
     * Construct a new RussianLetterTokenizer using a given {@link AttributeSource}.
     * 
     * @deprecated use {@link #RussianLetterTokenizer(Version, AttributeSource, Reader)}
     *             instead. This will be removed in Lucene 4.0.
     */
    @Deprecated
    public RussianLetterTokenizer(AttributeSource source, Reader in) {
      super(source, in);
    }

    /**
     * Construct a new RussianLetterTokenizer using a given
     * {@link org.apache.lucene.util.AttributeSource.AttributeFactory}.
     * 
     * @deprecated use {@link #RussianLetterTokenizer(Version, AttributeSource.AttributeFactory, Reader)}
     *             instead. This will be removed in Lucene 4.0.
     */
    @Deprecated
    public RussianLetterTokenizer(AttributeFactory factory, Reader in) {
      super(factory, in);
    }
    
    
    /**
     * Collects only characters which satisfy
     * {@link Character#isLetter(int)}.
     */
    @Override
    protected boolean isTokenChar(int c) {
        return Character.isLetter(c) || (c >= DIGIT_0 && c <= DIGIT_9);
    }
}
