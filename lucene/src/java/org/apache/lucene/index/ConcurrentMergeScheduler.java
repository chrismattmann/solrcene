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

import org.apache.lucene.store.Directory;
import org.apache.lucene.util.ThreadInterruptedException;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;

/** A {@link MergeScheduler} that runs each merge using a
 *  separate thread.
 *
 *  <p>Specify the max number of threads that may run at
 *  once with {@link #setMaxThreadCount}.</p>
 *
 *  <p>Separately specify the maximum number of simultaneous
 *  merges with {@link #setMaxMergeCount}.  If the number of
 *  merges exceeds the max number of threads then the
 *  largest merges are paused until one of the smaller
 *  merges completes.</p>
 *
 *  <p>If more than {@link #getMaxMergeCount} merges are
 *  requested then this class will forcefully throttle the
 *  incoming threads by pausing until one more more merges
 *  complete.</p>
 */ 
public class ConcurrentMergeScheduler extends MergeScheduler {

  private int mergeThreadPriority = -1;

  protected List<MergeThread> mergeThreads = new ArrayList<MergeThread>();

  // Max number of merge threads allowed to be running at
  // once.  When there are more merges then this, we
  // forcefully pause the larger ones, letting the smaller
  // ones run, up until maxMergeCount merges at which point
  // we forcefully pause incoming threads (that presumably
  // are the ones causing so much merging).  We dynamically
  // default this from 1 to 3, depending on how many cores
  // you have:
  private int maxThreadCount = Math.max(1, Math.min(3, Runtime.getRuntime().availableProcessors()/2));

  // Max number of merges we accept before forcefully
  // throttling the incoming threads
  private int maxMergeCount = maxThreadCount+2;

  protected Directory dir;

  private boolean closed;
  protected IndexWriter writer;
  protected int mergeThreadCount;

  public ConcurrentMergeScheduler() {
    if (allInstances != null) {
      // Only for testing
      addMyself();
    }
  }

  /** Sets the max # simultaneous merge threads that should
   *  be running at once.  This must be <= {@link
   *  #setMaxMergeCount}. */
  public void setMaxThreadCount(int count) {
    if (count < 1) {
      throw new IllegalArgumentException("count should be at least 1");
    }
    if (count > maxMergeCount) {
      throw new IllegalArgumentException("count should be <= maxMergeCount (= " + maxMergeCount + ")");
    }
    maxThreadCount = count;
  }

  /** @see #setMaxThreadCount(int) */
  public int getMaxThreadCount() {
    return maxThreadCount;
  }

  /** Sets the max # simultaneous merges that are allowed.
   *  If a merge is necessary yet we already have this many
   *  threads running, the incoming thread (that is calling
   *  add/updateDocument) will block until a merge thread
   *  has completed.  Note that we will only run the
   *  smallest {@link #setMaxThreadCount} merges at a time. */
  public void setMaxMergeCount(int count) {
    if (count < 1) {
      throw new IllegalArgumentException("count should be at least 1");
    }
    if (count < maxThreadCount) {
      throw new IllegalArgumentException("count should be >= maxThreadCount (= " + maxThreadCount + ")");
    }
    maxMergeCount = count;
  }

  /** See {@link #setMaxMergeCount}. */
  public int getMaxMergeCount() {
    return maxMergeCount;
  }

  /** Return the priority that merge threads run at.  By
   *  default the priority is 1 plus the priority of (ie,
   *  slightly higher priority than) the first thread that
   *  calls merge. */
  public synchronized int getMergeThreadPriority() {
    initMergeThreadPriority();
    return mergeThreadPriority;
  }

  /** Set the base priority that merge threads run at.
   *  Note that CMS may increase priority of some merge
   *  threads beyond this base priority.  It's best not to
   *  set this any higher than
   *  Thread.MAX_PRIORITY-maxThreadCount, so that CMS has
   *  room to set relative priority among threads.  */
  public synchronized void setMergeThreadPriority(int pri) {
    if (pri > Thread.MAX_PRIORITY || pri < Thread.MIN_PRIORITY)
      throw new IllegalArgumentException("priority must be in range " + Thread.MIN_PRIORITY + " .. " + Thread.MAX_PRIORITY + " inclusive");
    mergeThreadPriority = pri;
    updateMergeThreads();
  }

  // Larger merges come first
  protected static class CompareByMergeDocCount implements Comparator<MergeThread> {
    public int compare(MergeThread t1, MergeThread t2) {
      final MergePolicy.OneMerge m1 = t1.getCurrentMerge();
      final MergePolicy.OneMerge m2 = t2.getCurrentMerge();
      
      final int c1 = m1 == null ? Integer.MAX_VALUE : m1.segments.totalDocCount();
      final int c2 = m2 == null ? Integer.MAX_VALUE : m2.segments.totalDocCount();

      return c2 - c1;
    }
  }

  /** Called whenever the running merges have changed, to
   *  pause & unpause threads. */
  protected synchronized void updateMergeThreads() {

    Collections.sort(mergeThreads, new CompareByMergeDocCount());
    
    final int count = mergeThreads.size();
    int pri = mergeThreadPriority;
    for(int i=0;i<count;i++) {
      final MergeThread mergeThread = mergeThreads.get(i);
      final MergePolicy.OneMerge merge = mergeThread.getCurrentMerge();
      if (merge == null) {
        continue;
      }
      final boolean doPause;
      if (i < count-maxThreadCount) {
        doPause = true;
      } else {
        doPause = false;
      }

      if (verbose()) {
        if (doPause != merge.getPause()) {
          if (doPause) {
            message("pause thread " + mergeThread.getName());
          } else {
            message("unpause thread " + mergeThread.getName());
          }
        }
      }
      if (doPause != merge.getPause()) {
        merge.setPause(doPause);
      }

      if (!doPause) {
        if (verbose()) {
          message("set priority of merge thread " + mergeThread.getName() + " to " + pri);
        }
        mergeThread.setThreadPriority(pri);
        pri = Math.min(Thread.MAX_PRIORITY, 1+pri);
      }
    }
  }

  private boolean verbose() {
    return writer != null && writer.verbose();
  }
  
  private void message(String message) {
    if (verbose())
      writer.message("CMS: " + message);
  }

  private synchronized void initMergeThreadPriority() {
    if (mergeThreadPriority == -1) {
      // Default to slightly higher priority than our
      // calling thread
      mergeThreadPriority = 1+Thread.currentThread().getPriority();
      if (mergeThreadPriority > Thread.MAX_PRIORITY)
        mergeThreadPriority = Thread.MAX_PRIORITY;
    }
  }

  @Override
  public void close() {
    closed = true;
  }

  public synchronized void sync() {
    while(mergeThreadCount() > 0) {
      if (verbose())
        message("now wait for threads; currently " + mergeThreads.size() + " still running");
      final int count = mergeThreads.size();
      if (verbose()) {
        for(int i=0;i<count;i++)
          message("    " + i + ": " + mergeThreads.get(i));
      }
      
      try {
        wait();
      } catch (InterruptedException ie) {
        throw new ThreadInterruptedException(ie);
      }
    }
  }

  private synchronized int mergeThreadCount() {
    int count = 0;
    final int numThreads = mergeThreads.size();
    for(int i=0;i<numThreads;i++)
      if (mergeThreads.get(i).isAlive())
        count++;
    return count;
  }

  @Override
  public void merge(IndexWriter writer)
    throws CorruptIndexException, IOException {

    assert !Thread.holdsLock(writer);

    this.writer = writer;

    initMergeThreadPriority();

    dir = writer.getDirectory();

    // First, quickly run through the newly proposed merges
    // and add any orthogonal merges (ie a merge not
    // involving segments already pending to be merged) to
    // the queue.  If we are way behind on merging, many of
    // these newly proposed merges will likely already be
    // registered.

    if (verbose()) {
      message("now merge");
      message("  index: " + writer.segString());
    }
    
    // Iterate, pulling from the IndexWriter's queue of
    // pending merges, until it's empty:
    while(true) {

      // TODO: we could be careful about which merges to do in
      // the BG (eg maybe the "biggest" ones) vs FG, which
      // merges to do first (the easiest ones?), etc.

      MergePolicy.OneMerge merge = writer.getNextMerge();
      if (merge == null) {
        if (verbose())
          message("  no more merges pending; now return");
        return;
      }

      // We do this w/ the primary thread to keep
      // deterministic assignment of segment names
      writer.mergeInit(merge);

      boolean success = false;
      try {
        synchronized(this) {
          final MergeThread merger;
          long startStallTime = 0;
          while (mergeThreadCount() >= maxMergeCount) {
            startStallTime = System.currentTimeMillis();
            if (verbose()) {
              message("    too many merges; stalling...");
            }
            try {
              wait();
            } catch (InterruptedException ie) {
              throw new ThreadInterruptedException(ie);
            }
          }

          if (verbose()) {
            if (startStallTime != 0) {
              message("  stalled for " + (System.currentTimeMillis()-startStallTime) + " msec");
            }
            message("  consider merge " + merge.segString(dir));
          }

          assert mergeThreadCount() < maxMergeCount;

          // OK to spawn a new merge thread to handle this
          // merge:
          merger = getMergeThread(writer, merge);
          mergeThreads.add(merger);
          updateMergeThreads();
          if (verbose())
            message("    launch new thread [" + merger.getName() + "]");

          merger.start();
          success = true;
        }
      } finally {
        if (!success) {
          writer.mergeFinish(merge);
        }
      }
    }
  }

  /** Does the actual merge, by calling {@link IndexWriter#merge} */
  protected void doMerge(MergePolicy.OneMerge merge)
    throws IOException {
    writer.merge(merge);
  }

  /** Create and return a new MergeThread */
  protected synchronized MergeThread getMergeThread(IndexWriter writer, MergePolicy.OneMerge merge) throws IOException {
    final MergeThread thread = new MergeThread(writer, merge);
    thread.setThreadPriority(mergeThreadPriority);
    thread.setDaemon(true);
    thread.setName("Lucene Merge Thread #" + mergeThreadCount++);
    return thread;
  }

  protected class MergeThread extends Thread {

    IndexWriter tWriter;
    MergePolicy.OneMerge startMerge;
    MergePolicy.OneMerge runningMerge;
    private volatile boolean done;

    public MergeThread(IndexWriter writer, MergePolicy.OneMerge startMerge) throws IOException {
      this.tWriter = writer;
      this.startMerge = startMerge;
    }

    public synchronized void setRunningMerge(MergePolicy.OneMerge merge) {
      runningMerge = merge;
    }

    public synchronized MergePolicy.OneMerge getRunningMerge() {
      return runningMerge;
    }

    public synchronized MergePolicy.OneMerge getCurrentMerge() {
      if (done) {
        return null;
      } else if (runningMerge != null) {
        return runningMerge;
      } else {
        return startMerge;
      }
    }

    public void setThreadPriority(int pri) {
      try {
        setPriority(pri);
      } catch (NullPointerException npe) {
        // Strangely, Sun's JDK 1.5 on Linux sometimes
        // throws NPE out of here...
      } catch (SecurityException se) {
        // Ignore this because we will still run fine with
        // normal thread priority
      }
    }

    @Override
    public void run() {
      
      // First time through the while loop we do the merge
      // that we were started with:
      MergePolicy.OneMerge merge = this.startMerge;
      
      try {

        if (verbose())
          message("  merge thread: start");

        while(true) {
          setRunningMerge(merge);
          doMerge(merge);

          // Subsequent times through the loop we do any new
          // merge that writer says is necessary:
          merge = tWriter.getNextMerge();
          if (merge != null) {
            tWriter.mergeInit(merge);
            updateMergeThreads();
            if (verbose())
              message("  merge thread: do another merge " + merge.segString(dir));
          } else {
            done = true;
            updateMergeThreads();
            break;
          }
        }

        if (verbose())
          message("  merge thread: done");

      } catch (Throwable exc) {

        // Ignore the exception if it was due to abort:
        if (!(exc instanceof MergePolicy.MergeAbortedException)) {
          if (!suppressExceptions) {
            // suppressExceptions is normally only set during
            // testing.
            anyExceptions = true;
            handleMergeException(exc);
          }
        }
      } finally {
        synchronized(ConcurrentMergeScheduler.this) {
          ConcurrentMergeScheduler.this.notifyAll();
          boolean removed = mergeThreads.remove(this);
          assert removed;
          updateMergeThreads();
        }
      }
    }

    @Override
    public String toString() {
      MergePolicy.OneMerge merge = getRunningMerge();
      if (merge == null)
        merge = startMerge;
      return "merge thread: " + merge.segString(dir);
    }
  }

  /** Called when an exception is hit in a background merge
   *  thread */
  protected void handleMergeException(Throwable exc) {
    try {
      // When an exception is hit during merge, IndexWriter
      // removes any partial files and then allows another
      // merge to run.  If whatever caused the error is not
      // transient then the exception will keep happening,
      // so, we sleep here to avoid saturating CPU in such
      // cases:
      Thread.sleep(250);
    } catch (InterruptedException ie) {
      throw new ThreadInterruptedException(ie);
    }
    throw new MergePolicy.MergeException(exc, dir);
  }

  static boolean anyExceptions = false;

  /** Used for testing */
  public static boolean anyUnhandledExceptions() {
    if (allInstances == null) {
      throw new RuntimeException("setTestMode() was not called; often this is because your test case's setUp method fails to call super.setUp in LuceneTestCase");
    }
    synchronized(allInstances) {
      final int count = allInstances.size();
      // Make sure all outstanding threads are done so we see
      // any exceptions they may produce:
      for(int i=0;i<count;i++)
        allInstances.get(i).sync();
      boolean v = anyExceptions;
      anyExceptions = false;
      return v;
    }
  }

  public static void clearUnhandledExceptions() {
    synchronized(allInstances) {
      anyExceptions = false;
    }
  }

  /** Used for testing */
  private void addMyself() {
    synchronized(allInstances) {
      final int size = allInstances.size();
      int upto = 0;
      for(int i=0;i<size;i++) {
        final ConcurrentMergeScheduler other = allInstances.get(i);
        if (!(other.closed && 0 == other.mergeThreadCount()))
          // Keep this one for now: it still has threads or
          // may spawn new threads
          allInstances.set(upto++, other);
      }
      allInstances.subList(upto, allInstances.size()).clear();
      allInstances.add(this);
    }
  }

  private boolean suppressExceptions;

  /** Used for testing */
  void setSuppressExceptions() {
    suppressExceptions = true;
  }

  /** Used for testing */
  void clearSuppressExceptions() {
    suppressExceptions = false;
  }

  /** Used for testing */
  private static List<ConcurrentMergeScheduler> allInstances;
  public static void setTestMode() {
    allInstances = new ArrayList<ConcurrentMergeScheduler>();
  }
}
