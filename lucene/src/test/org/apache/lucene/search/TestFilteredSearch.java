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

package org.apache.lucene.search;

import java.io.IOException;
import java.util.Random;

import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.util.OpenBitSet;


/**
 *
 */
public class TestFilteredSearch extends LuceneTestCase {

  public TestFilteredSearch(String name) {
    super(name);
  }

  private static final String FIELD = "category";
  
  public void testFilteredSearch() throws CorruptIndexException, LockObtainFailedException, IOException {
    boolean enforceSingleSegment = true;
    Random random = newRandom();
    Directory directory = newDirectory(random);
    int[] filterBits = {1, 36};
    SimpleDocIdSetFilter filter = new SimpleDocIdSetFilter(filterBits);
    IndexWriter writer = new IndexWriter(directory, newIndexWriterConfig(random, TEST_VERSION_CURRENT, new MockAnalyzer()));
    searchFiltered(writer, directory, filter, enforceSingleSegment);
    // run the test on more than one segment
    enforceSingleSegment = false;
    // reset - it is stateful
    filter.reset();
    writer.close();
    writer = new IndexWriter(directory, newIndexWriterConfig(random, TEST_VERSION_CURRENT, new MockAnalyzer()).setOpenMode(OpenMode.CREATE).setMaxBufferedDocs(10));
    // we index 60 docs - this will create 6 segments
    searchFiltered(writer, directory, filter, enforceSingleSegment);
    writer.close();
    directory.close();
  }

  public void searchFiltered(IndexWriter writer, Directory directory, SimpleDocIdSetFilter filter, boolean optimize) {
    try {
      for (int i = 0; i < 60; i++) {//Simple docs
        Document doc = new Document();
        doc.add(new Field(FIELD, Integer.toString(i), Field.Store.YES, Field.Index.NOT_ANALYZED));
        writer.addDocument(doc);
      }
      if(optimize)
        writer.optimize();
      writer.close();

      BooleanQuery booleanQuery = new BooleanQuery();
      booleanQuery.add(new TermQuery(new Term(FIELD, "36")), BooleanClause.Occur.SHOULD);
     
     
      IndexSearcher indexSearcher = new IndexSearcher(directory, true);
      filter.setTopReader(indexSearcher.getIndexReader());
      ScoreDoc[] hits = indexSearcher.search(booleanQuery, filter, 1000).scoreDocs;
      assertEquals("Number of matched documents", 1, hits.length);
      indexSearcher.close();
    }
    catch (IOException e) {
      fail(e.getMessage());
    }
    
  }
 
  public static final class SimpleDocIdSetFilter extends Filter {
    private final int[] docs;
    private int index;
    private IndexReader topReader;
    public SimpleDocIdSetFilter(int[] docs) {
      this.docs = docs;
    }

    public void setTopReader(IndexReader r) {
      topReader = r;
    }

    @Override
    public DocIdSet getDocIdSet(IndexReader reader) {
      final OpenBitSet set = new OpenBitSet();
      int docBase = topReader.getSubReaderDocBase(reader);
      final int limit = docBase+reader.maxDoc();
      for (;index < docs.length; index++) {
        final int docId = docs[index];
        if(docId > limit)
          break;
        if (docId >= docBase) {
          set.set(docId-docBase);
        }
      }
      return set.isEmpty()?null:set;
    }
    
    public void reset(){
      index = 0;
    }
  }

}
