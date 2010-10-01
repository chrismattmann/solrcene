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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Random;

import junit.framework.Assert;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;

public class TestDocIdSet extends LuceneTestCase {
  public void testFilteredDocIdSet() throws Exception {
    final int maxdoc=10;
    final DocIdSet innerSet = new DocIdSet() {

        @Override
        public DocIdSetIterator iterator() {
          return new DocIdSetIterator() {

            int docid = -1;
            
            @Override
            public int docID() {
              return docid;
            }
            
            @Override
            public int nextDoc() throws IOException {
              docid++;
              return docid < maxdoc ? docid : (docid = NO_MORE_DOCS);
            }

            @Override
            public int advance(int target) throws IOException {
              while (nextDoc() < target) {}
              return docid;
            }
          };
        } 
      };
	  
		
    DocIdSet filteredSet = new FilteredDocIdSet(innerSet){
        @Override
        protected boolean match(int docid) {
          return docid%2 == 0;  //validate only even docids
        }	
      };
	  
    DocIdSetIterator iter = filteredSet.iterator();
    ArrayList<Integer> list = new ArrayList<Integer>();
    int doc = iter.advance(3);
    if (doc != DocIdSetIterator.NO_MORE_DOCS) {
      list.add(Integer.valueOf(doc));
      while((doc = iter.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
        list.add(Integer.valueOf(doc));
      }
    }
	  
    int[] docs = new int[list.size()];
    int c=0;
    Iterator<Integer> intIter = list.iterator();
    while(intIter.hasNext()) {
      docs[c++] = intIter.next().intValue();
    }
    int[] answer = new int[]{4,6,8};
    boolean same = Arrays.equals(answer, docs);
    if (!same) {
      System.out.println("answer: " + Arrays.toString(answer));
      System.out.println("gotten: " + Arrays.toString(docs));
      fail();
    }
  }
  
  public void testNullDocIdSet() throws Exception {
    // Tests that if a Filter produces a null DocIdSet, which is given to
    // IndexSearcher, everything works fine. This came up in LUCENE-1754.
    Random random = newRandom();
    Directory dir = newDirectory(random);
    RandomIndexWriter writer = new RandomIndexWriter(random, dir);
    Document doc = new Document();
    doc.add(new Field("c", "val", Store.NO, Index.NOT_ANALYZED_NO_NORMS));
    writer.addDocument(doc);
    IndexReader reader = writer.getReader();
    writer.close();
    
    // First verify the document is searchable.
    IndexSearcher searcher = new IndexSearcher(reader);
    Assert.assertEquals(1, searcher.search(new MatchAllDocsQuery(), 10).totalHits);
    
    // Now search w/ a Filter which returns a null DocIdSet
    Filter f = new Filter() {
      @Override
      public DocIdSet getDocIdSet(IndexReader reader) throws IOException {
        return null;
      }
    };
    
    Assert.assertEquals(0, searcher.search(new MatchAllDocsQuery(), f, 10).totalHits);
    searcher.close();
    reader.close();
    dir.close();
  }

}
