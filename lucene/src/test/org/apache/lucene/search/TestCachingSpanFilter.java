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
import java.util.Random;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;

public class TestCachingSpanFilter extends LuceneTestCase {

  public void testEnforceDeletions() throws Exception {
    Random random = newRandom();
    Directory dir = newDirectory(random);
    RandomIndexWriter writer = new RandomIndexWriter(random, dir);
    // NOTE: cannot use writer.getReader because RIW (on
    // flipping a coin) may give us a newly opened reader,
    // but we use .reopen on this reader below and expect to
    // (must) get an NRT reader:
    IndexReader reader = writer.w.getReader();
    IndexSearcher searcher = new IndexSearcher(reader);

    // add a doc, refresh the reader, and check that its there
    Document doc = new Document();
    doc.add(new Field("id", "1", Field.Store.YES, Field.Index.NOT_ANALYZED));
    writer.addDocument(doc);

    reader = refreshReader(reader);
    searcher = new IndexSearcher(reader);

    TopDocs docs = searcher.search(new MatchAllDocsQuery(), 1);
    assertEquals("Should find a hit...", 1, docs.totalHits);

    final SpanFilter startFilter = new SpanQueryFilter(new SpanTermQuery(new Term("id", "1")));

    // ignore deletions
    CachingSpanFilter filter = new CachingSpanFilter(startFilter, CachingWrapperFilter.DeletesMode.IGNORE);
        
    docs = searcher.search(new MatchAllDocsQuery(), filter, 1);
    assertEquals("[query + filter] Should find a hit...", 1, docs.totalHits);
    ConstantScoreQuery constantScore = new ConstantScoreQuery(filter);
    docs = searcher.search(constantScore, 1);
    assertEquals("[just filter] Should find a hit...", 1, docs.totalHits);

    // now delete the doc, refresh the reader, and see that it's not there
    writer.deleteDocuments(new Term("id", "1"));

    reader = refreshReader(reader);
    searcher = new IndexSearcher(reader);

    docs = searcher.search(new MatchAllDocsQuery(), filter, 1);
    assertEquals("[query + filter] Should *not* find a hit...", 0, docs.totalHits);

    docs = searcher.search(constantScore, 1);
    assertEquals("[just filter] Should find a hit...", 1, docs.totalHits);


    // force cache to regenerate:
    filter = new CachingSpanFilter(startFilter, CachingWrapperFilter.DeletesMode.RECACHE);

    writer.addDocument(doc);
    reader = refreshReader(reader);
    searcher = new IndexSearcher(reader);
        
    docs = searcher.search(new MatchAllDocsQuery(), filter, 1);
    assertEquals("[query + filter] Should find a hit...", 1, docs.totalHits);

    constantScore = new ConstantScoreQuery(filter);
    docs = searcher.search(constantScore, 1);
    assertEquals("[just filter] Should find a hit...", 1, docs.totalHits);

    // make sure we get a cache hit when we reopen readers
    // that had no new deletions
    IndexReader newReader = refreshReader(reader);
    assertTrue(reader != newReader);
    reader = newReader;
    searcher = new IndexSearcher(reader);
    int missCount = filter.missCount;
    docs = searcher.search(constantScore, 1);
    assertEquals("[just filter] Should find a hit...", 1, docs.totalHits);
    assertEquals(missCount, filter.missCount);

    // now delete the doc, refresh the reader, and see that it's not there
    writer.deleteDocuments(new Term("id", "1"));

    reader = refreshReader(reader);
    searcher = new IndexSearcher(reader);

    docs = searcher.search(new MatchAllDocsQuery(), filter, 1);
    assertEquals("[query + filter] Should *not* find a hit...", 0, docs.totalHits);

    docs = searcher.search(constantScore, 1);
    assertEquals("[just filter] Should *not* find a hit...", 0, docs.totalHits);
    writer.close();
    reader.close();
    dir.close();
  }

  private static IndexReader refreshReader(IndexReader reader) throws IOException {
    IndexReader oldReader = reader;
    reader = reader.reopen();
    if (reader != oldReader) {
      oldReader.close();
    }
    return reader;
  }
}
