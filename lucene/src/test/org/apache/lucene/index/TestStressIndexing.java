package org.apache.lucene.index;

/**
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.lucene.util.*;
import org.apache.lucene.store.*;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.search.*;

import java.util.Random;
import java.io.File;

public class TestStressIndexing extends MultiCodecTestCase {
  private Random RANDOM;

  private static abstract class TimedThread extends Thread {
    volatile boolean failed;
    int count;
    private static int RUN_TIME_SEC = 1 * RANDOM_MULTIPLIER;
    private TimedThread[] allThreads;

    abstract public void doWork() throws Throwable;

    TimedThread(TimedThread[] threads) {
      this.allThreads = threads;
    }

    @Override
    public void run() {
      final long stopTime = System.currentTimeMillis() + 1000*RUN_TIME_SEC;

      count = 0;

      try {
        do {
          if (anyErrors()) break;
          doWork();
          count++;
        } while(System.currentTimeMillis() < stopTime);
      } catch (Throwable e) {
        System.out.println(Thread.currentThread() + ": exc");
        e.printStackTrace(System.out);
        failed = true;
      }
    }

    private boolean anyErrors() {
      for(int i=0;i<allThreads.length;i++)
        if (allThreads[i] != null && allThreads[i].failed)
          return true;
      return false;
    }
  }

  private class IndexerThread extends TimedThread {
    IndexWriter writer;
    int nextID;

    public IndexerThread(IndexWriter writer, TimedThread[] threads) {
      super(threads);
      this.writer = writer;
    }

    @Override
    public void doWork() throws Exception {
      // Add 10 docs:
      for(int j=0; j<10; j++) {
        Document d = new Document();
        int n = RANDOM.nextInt();
        d.add(new Field("id", Integer.toString(nextID++), Field.Store.YES, Field.Index.NOT_ANALYZED));
        d.add(new Field("contents", English.intToEnglish(n), Field.Store.NO, Field.Index.ANALYZED));
        writer.addDocument(d);
      }

      // Delete 5 docs:
      int deleteID = nextID-1;
      for(int j=0; j<5; j++) {
        writer.deleteDocuments(new Term("id", ""+deleteID));
        deleteID -= 2;
      }
    }
  }

  private static class SearcherThread extends TimedThread {
    private Directory directory;

    public SearcherThread(Directory directory, TimedThread[] threads) {
      super(threads);
      this.directory = directory;
    }

    @Override
    public void doWork() throws Throwable {
      for (int i=0; i<100; i++)
        (new IndexSearcher(directory, true)).close();
      count += 100;
    }
  }

  /*
    Run one indexer and 2 searchers against single index as
    stress test.
  */
  public void runStressTest(Directory directory, MergeScheduler mergeScheduler) throws Exception {
    IndexWriter modifier = new IndexWriter(directory, newIndexWriterConfig(RANDOM,
        TEST_VERSION_CURRENT, new MockAnalyzer())
        .setOpenMode(OpenMode.CREATE).setMaxBufferedDocs(10).setMergeScheduler(
            mergeScheduler));
    modifier.commit();
    
    TimedThread[] threads = new TimedThread[4];
    int numThread = 0;


    // One modifier that writes 10 docs then removes 5, over
    // and over:
    IndexerThread indexerThread = new IndexerThread(modifier, threads);
    threads[numThread++] = indexerThread;
    indexerThread.start();
    
    IndexerThread indexerThread2 = new IndexerThread(modifier, threads);
    threads[numThread++] = indexerThread2;
    indexerThread2.start();
      
    // Two searchers that constantly just re-instantiate the
    // searcher:
    SearcherThread searcherThread1 = new SearcherThread(directory, threads);
    threads[numThread++] = searcherThread1;
    searcherThread1.start();

    SearcherThread searcherThread2 = new SearcherThread(directory, threads);
    threads[numThread++] = searcherThread2;
    searcherThread2.start();

    for(int i=0;i<numThread;i++)
      threads[i].join();

    modifier.close();

    for(int i=0;i<numThread;i++)
      assertTrue(! threads[i].failed);

    //System.out.println("    Writer: " + indexerThread.count + " iterations");
    //System.out.println("Searcher 1: " + searcherThread1.count + " searchers created");
    //System.out.println("Searcher 2: " + searcherThread2.count + " searchers created");
  }

  /*
    Run above stress test against RAMDirectory and then
    FSDirectory.
  */
  public void testStressIndexAndSearching() throws Exception {
    RANDOM = newRandom();

    // With ConcurrentMergeScheduler, in RAMDir
    Directory directory = newDirectory(RANDOM);
    runStressTest(directory, new ConcurrentMergeScheduler());
    directory.close();

    // With ConcurrentMergeScheduler, in FSDir
    File dirPath = _TestUtil.getTempDir("lucene.test.stress");
    directory = FSDirectory.open(dirPath);
    runStressTest(directory, new ConcurrentMergeScheduler());
    directory.close();

    _TestUtil.rmDir(dirPath);
  }
}
