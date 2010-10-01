package org.apache.lucene.search;

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

import java.util.HashSet;
import java.util.Random;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.index.SlowMultiReaderWrapper;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.OpenBitSet;

public class TermsFilterTest extends LuceneTestCase {
  
	public void testCachability() throws Exception
	{
		TermsFilter a=new TermsFilter();
		a.addTerm(new Term("field1","a"));
		a.addTerm(new Term("field1","b"));
		HashSet<Filter> cachedFilters=new HashSet<Filter>();
		cachedFilters.add(a);
		TermsFilter b=new TermsFilter();
		b.addTerm(new Term("field1","a"));
		b.addTerm(new Term("field1","b"));
		
		assertTrue("Must be cached",cachedFilters.contains(b));
		b.addTerm(new Term("field1","a")); //duplicate term
		assertTrue("Must be cached",cachedFilters.contains(b));
		b.addTerm(new Term("field1","c"));
		assertFalse("Must not be cached",cachedFilters.contains(b));
	}
	
	public void testMissingTerms() throws Exception {
		String fieldName="field1";
		Random random = newRandom();
		Directory rd=newDirectory(random);
		RandomIndexWriter w = new RandomIndexWriter(random, rd);
		for (int i = 0; i < 100; i++) {
			Document doc=new Document();
			int term=i*10; //terms are units of 10;
			doc.add(new Field(fieldName,""+term,Field.Store.YES,Field.Index.NOT_ANALYZED));
			w.addDocument(doc);			
		}
		IndexReader mainReader = w.getReader();
		w.close();

                IndexReader reader = SlowMultiReaderWrapper.wrap(mainReader);
		
		TermsFilter tf=new TermsFilter();
		tf.addTerm(new Term(fieldName,"19"));
		OpenBitSet bits = (OpenBitSet)tf.getDocIdSet(reader);
		assertEquals("Must match nothing", 0, bits.cardinality());

		tf.addTerm(new Term(fieldName,"20"));
		bits = (OpenBitSet)tf.getDocIdSet(reader);
		assertEquals("Must match 1", 1, bits.cardinality());
		
		tf.addTerm(new Term(fieldName,"10"));
		bits = (OpenBitSet)tf.getDocIdSet(reader);
		assertEquals("Must match 2", 2, bits.cardinality());
		
		tf.addTerm(new Term(fieldName,"00"));
		bits = (OpenBitSet)tf.getDocIdSet(reader);
		assertEquals("Must match 2", 2, bits.cardinality());
		
		mainReader.close();
		rd.close();
	}
}
