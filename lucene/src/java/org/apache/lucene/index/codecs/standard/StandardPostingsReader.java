package org.apache.lucene.index.codecs.standard;

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
import java.io.Closeable;

import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.DocsAndPositionsEnum;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.util.Bits;

/** StandardTermsDictReader interacts with a single instance
 *  of this to manage creation of {@link DocsEnum} and
 *  {@link DocsAndPositionsEnum} instances.  It provides an
 *  IndexInput (termsIn) where this class may read any
 *  previously stored data that it had written in its
 *  corresponding {@link StandardPostingsWriter} at indexing
 *  time. 
 *  @lucene.experimental */

public abstract class StandardPostingsReader implements Closeable {

  public abstract void init(IndexInput termsIn) throws IOException;

  /** Return a newly created empty TermState */
  public abstract TermState newTermState() throws IOException;

  public abstract void readTerm(IndexInput termsIn, FieldInfo fieldInfo, TermState state, boolean isIndexTerm) throws IOException;

  /** Must fully consume state, since after this call that
   *  TermState may be reused. */
  public abstract DocsEnum docs(FieldInfo fieldInfo, TermState state, Bits skipDocs, DocsEnum reuse) throws IOException;

  /** Must fully consume state, since after this call that
   *  TermState may be reused. */
  public abstract DocsAndPositionsEnum docsAndPositions(FieldInfo fieldInfo, TermState state, Bits skipDocs, DocsAndPositionsEnum reuse) throws IOException;

  public abstract void close() throws IOException;
}
