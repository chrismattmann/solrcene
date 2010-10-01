package org.apache.lucene.search.regex;

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
import java.util.Random;

import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MultiSearcher;
import org.apache.lucene.search.spans.SpanFirstQuery;
import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.util.LuceneTestCase;

public class TestSpanRegexQuery extends LuceneTestCase {
  
  Directory indexStoreA;
  Directory indexStoreB;
  Random random;
  
  @Override
  public void setUp() throws Exception {
    super.setUp();
    random = newRandom();
    indexStoreA = newDirectory(random);
    indexStoreB = newDirectory(random);
  }
  
  @Override
  public void tearDown() throws Exception {
    indexStoreA.close();
    indexStoreB.close();
    super.tearDown();
  }
  
  public void testSpanRegex() throws Exception {
    Directory directory = newDirectory(random);
    IndexWriter writer = new IndexWriter(directory, newIndexWriterConfig(random,
        TEST_VERSION_CURRENT, new MockAnalyzer()));
    Document doc = new Document();
    // doc.add(new Field("field", "the quick brown fox jumps over the lazy dog",
    // Field.Store.NO, Field.Index.ANALYZED));
    // writer.addDocument(doc);
    // doc = new Document();
    doc.add(new Field("field", "auto update", Field.Store.NO,
        Field.Index.ANALYZED));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(new Field("field", "first auto update", Field.Store.NO,
        Field.Index.ANALYZED));
    writer.addDocument(doc);
    writer.optimize();
    writer.close();

    IndexSearcher searcher = new IndexSearcher(directory, true);
    SpanRegexQuery srq = new SpanRegexQuery(new Term("field", "aut.*"));
    SpanFirstQuery sfq = new SpanFirstQuery(srq, 1);
    // SpanNearQuery query = new SpanNearQuery(new SpanQuery[] {srq, stq}, 6,
    // true);
    int numHits = searcher.search(sfq, null, 1000).totalHits;
    assertEquals(1, numHits);
    searcher.close();
    directory.close();
  }

  public void testSpanRegexBug() throws CorruptIndexException, IOException {
    createRAMDirectories();

    SpanRegexQuery srq = new SpanRegexQuery(new Term("field", "a.*"));
    SpanRegexQuery stq = new SpanRegexQuery(new Term("field", "b.*"));
    SpanNearQuery query = new SpanNearQuery(new SpanQuery[] { srq, stq }, 6,
        true);

    // 1. Search the same store which works
    IndexSearcher[] arrSearcher = new IndexSearcher[2];
    arrSearcher[0] = new IndexSearcher(indexStoreA, true);
    arrSearcher[1] = new IndexSearcher(indexStoreB, true);
    MultiSearcher searcher = new MultiSearcher(arrSearcher);
    int numHits = searcher.search(query, null, 1000).totalHits;
    arrSearcher[0].close();
    arrSearcher[1].close();

    // Will fail here
    // We expect 2 but only one matched
    // The rewriter function only write it once on the first IndexSearcher
    // So it's using term: a1 b1 to search on the second IndexSearcher
    // As a result, it won't match the document in the second IndexSearcher
    assertEquals(2, numHits);
    indexStoreA.close();
    indexStoreB.close();
  }

  private void createRAMDirectories() throws CorruptIndexException,
      LockObtainFailedException, IOException {
    // creating a document to store
    Document lDoc = new Document();
    lDoc.add(new Field("field", "a1 b1", Field.Store.NO,
        Field.Index.ANALYZED_NO_NORMS));

    // creating a document to store
    Document lDoc2 = new Document();
    lDoc2.add(new Field("field", "a2 b2", Field.Store.NO,
        Field.Index.ANALYZED_NO_NORMS));

    // creating first index writer
    IndexWriter writerA = new IndexWriter(indexStoreA, newIndexWriterConfig(random,
        TEST_VERSION_CURRENT, new MockAnalyzer()).setOpenMode(OpenMode.CREATE));
    writerA.addDocument(lDoc);
    writerA.optimize();
    writerA.close();

    // creating second index writer
    IndexWriter writerB = new IndexWriter(indexStoreB, newIndexWriterConfig(random,
        TEST_VERSION_CURRENT, new MockAnalyzer()).setOpenMode(OpenMode.CREATE));
    writerB.addDocument(lDoc2);
    writerB.optimize();
    writerB.close();
  }
}
