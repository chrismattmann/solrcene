package org.apache.lucene.index;

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

import java.util.Comparator;

import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.StringHelper;

/**
  A Term represents a word from text.  This is the unit of search.  It is
  composed of two elements, the text of the word, as a string, and the name of
  the field that the text occurred in, an interned string.

  Note that terms may represent more than words from text fields, but also
  things like dates, email addresses, urls, etc.  */

public final class Term implements Comparable<Term>, java.io.Serializable {
  String field;
  BytesRef bytes;

  /** Constructs a Term with the given field and bytes.
   * <p>Note that a null field or null bytes value results in undefined
   * behavior for most Lucene APIs that accept a Term parameter. 
   * <p>WARNING: the provided BytesRef is not copied, but used directly.
   * Therefore the bytes should not be modified after construction, for
   * example, you should clone a copy rather than pass reused bytes from
   * a TermsEnum.
   */
  public Term(String fld, BytesRef bytes) {
    field = fld == null ? null : StringHelper.intern(fld);
    this.bytes = bytes;
  }
  
  /** Constructs a Term with the given field and text.
   * <p>Note that a null field or null text value results in undefined
   * behavior for most Lucene APIs that accept a Term parameter. */
  public Term(String fld, String text) {
    this(fld, new BytesRef(text));
  }

  /** Constructs a Term with the given field and empty text.
   * This serves two purposes: 1) reuse of a Term with the same field.
   * 2) pattern for a query.
   * 
   * @param fld
   */
  public Term(String fld) {
    this(fld, new BytesRef(), true);
  }

  /** 
   * WARNING: the provided BytesRef is not copied, but used directly.
   * Therefore the bytes should not be modified after construction, for
   * example, you should clone a copy rather than pass reused bytes from
   * a TermsEnum.
   * 
   * @lucene.experimental 
   */
  public Term(String fld, BytesRef bytes, boolean intern) {
    field = intern ? StringHelper.intern(fld) : fld;	  // field names are interned
    this.bytes = bytes;					          // unless already known to be
  }

  /** @lucene.experimental */
  public Term(String fld, String text, boolean intern) {
    this(fld, new BytesRef(text), intern);
  }
  
  /** Returns the field of this term, an interned string.   The field indicates
    the part of a document which this term came from. */
  public final String field() { return field; }

  /** Returns the text of this term.  In the case of words, this is simply the
    text of the word.  In the case of dates and other types, this is an
    encoding of the object as a string.  */
  public final String text() { return bytes.utf8ToString(); }

  /** Returns the bytes of this term. */
  public final BytesRef bytes() { return bytes; }

  /**
   * Optimized construction of new Terms by reusing same field as this Term
   * - avoids field.intern() overhead 
   * <p>WARNING: the provided BytesRef is not copied, but used directly.
   * Therefore the bytes should not be modified after construction, for
   * example, you should clone a copy rather than pass reused bytes from
   * a TermsEnum.
   * @param bytes The bytes of the new term (field is implicitly same as this Term instance)
   * @return A new Term
   */
  public Term createTerm(BytesRef bytes)
  {
      return new Term(field,bytes,false);
  }

  /**
   * Optimized construction of new Terms by reusing same field as this Term
   * - avoids field.intern() overhead 
   * @param text The text of the new term (field is implicitly same as this Term instance)
   * @return A new Term
   */
  public Term createTerm(String text)
  {
      return new Term(field,text,false);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Term other = (Term) obj;
    if (field == null) {
      if (other.field != null)
        return false;
    } else if (!field.equals(other.field))
      return false;
    if (bytes == null) {
      if (other.bytes != null)
        return false;
    } else if (!bytes.equals(other.bytes))
      return false;
    return true;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((field == null) ? 0 : field.hashCode());
    result = prime * result + ((bytes == null) ? 0 : bytes.hashCode());
    return result;
  }

  /** Compares two terms, returning a negative integer if this
    term belongs before the argument, zero if this term is equal to the
    argument, and a positive integer if this term belongs after the argument.

    The ordering of terms is first by field, then by text.*/
  public final int compareTo(Term other) {
    if (field == other.field)			  // fields are interned
      return bytes.compareTo(other.bytes);
    else
      return field.compareTo(other.field);
  }
  
  @Deprecated
  private static final Comparator<BytesRef> legacyComparator = 
    BytesRef.getUTF8SortedAsUTF16Comparator();

  /** 
   * @deprecated For internal backwards compatibility use only
   * @lucene.internal
   */
  @Deprecated
  public final int compareToUTF16(Term other) {
    if (field == other.field) // fields are interned
      return legacyComparator.compare(this.bytes, other.bytes);
    else
      return field.compareTo(other.field);
  }

  /** 
   * Resets the field and text of a Term. 
   * <p>WARNING: the provided BytesRef is not copied, but used directly.
   * Therefore the bytes should not be modified after construction, for
   * example, you should clone a copy rather than pass reused bytes from
   * a TermsEnum.
   */
  final void set(String fld, BytesRef bytes) {
    field = fld;
    this.bytes = bytes;
  }

  /** Resets the field and text of a Term. */
  final void set(String fld, String txt) {
    field = fld;
    this.bytes = new BytesRef(txt);
  }

  @Override
  public final String toString() { return field + ":" + bytes.utf8ToString(); }

  private void readObject(java.io.ObjectInputStream in)
    throws java.io.IOException, ClassNotFoundException
  {
      in.defaultReadObject();
      field = StringHelper.intern(field);
  }
}
