package org.apache.lucene.index.codecs.preflex;

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
import java.util.Comparator;

import org.apache.lucene.store.IndexInput;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.FieldInfos;

final class TermBuffer implements Cloneable {

  private String field;
  private Term term;                            // cached

  private BytesRef bytes = new BytesRef(10);

  private static final Comparator<BytesRef> utf8AsUTF16Comparator = BytesRef.getUTF8SortedAsUTF16Comparator();

  int newSuffixStart;                             // only valid right after .read is called

  public int compareTo(TermBuffer other) {
    if (field == other.field) 	  // fields are interned
      return utf8AsUTF16Comparator.compare(bytes, other.bytes);
    else
      return field.compareTo(other.field);
  }

  public void read(IndexInput input, FieldInfos fieldInfos)
    throws IOException {
    this.term = null;                           // invalidate cache
    newSuffixStart = input.readVInt();
    int length = input.readVInt();
    int totalLength = newSuffixStart + length;
    if (bytes.bytes.length < totalLength) {
      bytes.grow(totalLength);
    }
    bytes.length = totalLength;
    input.readBytes(bytes.bytes, newSuffixStart, length);
    this.field = fieldInfos.fieldName(input.readVInt());
  }

  public void set(Term term) {
    if (term == null) {
      reset();
      return;
    }
    bytes.copy(term.bytes());
    field = term.field();
    this.term = term;
  }

  public void set(TermBuffer other) {
    field = other.field;
    // dangerous to copy Term over, since the underlying
    // BytesRef could subsequently be modified:
    term = null;
    bytes.copy(other.bytes);
  }

  public void reset() {
    field = null;
    term = null;
  }

  public Term toTerm() {
    if (field == null)                            // unset
      return null;

    if (term == null) {
      term = new Term(field, new BytesRef(bytes), false);
      //term = new Term(field, bytes, false);
    }

    return term;
  }

  @Override
  protected Object clone() {
    TermBuffer clone = null;
    try {
      clone = (TermBuffer)super.clone();
    } catch (CloneNotSupportedException e) {}
    clone.bytes = new BytesRef(bytes);
    return clone;
  }
}
