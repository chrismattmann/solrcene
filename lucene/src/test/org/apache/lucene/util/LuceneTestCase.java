package org.apache.lucene.util;

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

import java.io.File;
import java.io.PrintStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.TimeZone;

import junit.framework.TestCase;
import junit.framework.TestResult;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.ConcurrentMergeScheduler;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.codecs.Codec;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FieldCache;
import org.apache.lucene.search.FieldCache.CacheEntry;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.MockDirectoryWrapper;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.FieldCacheSanityChecker.Insanity;

/** 
 * Base class for all Lucene unit tests.  
 * <p>
 * Currently the
 * only added functionality over JUnit's TestCase is
 * asserting that no unhandled exceptions occurred in
 * threads launched by ConcurrentMergeScheduler and asserting sane
 * FieldCache usage athe moment of tearDown.
 * </p>
 * <p>
 * If you
 * override either <code>setUp()</code> or
 * <code>tearDown()</code> in your unit test, make sure you
 * call <code>super.setUp()</code> and
 * <code>super.tearDown()</code>
 * </p>
 * @see #assertSaneFieldCaches
 *
 */
public abstract class LuceneTestCase extends TestCase {

  /**
   * true iff tests are run in verbose mode. Note: if it is false, tests are not
   * expected to print any messages.
   */
  public static final boolean VERBOSE = LuceneTestCaseJ4.VERBOSE;

  /** Use this constant when creating Analyzers and any other version-dependent stuff. */
  public static final Version TEST_VERSION_CURRENT = LuceneTestCaseJ4.TEST_VERSION_CURRENT;

  /** Create indexes in this directory, optimally use a subdir, named after the test */
  public static final File TEMP_DIR = LuceneTestCaseJ4.TEMP_DIR;

  /** Gets the codec to run tests with. */
  public static final String TEST_CODEC = LuceneTestCaseJ4.TEST_CODEC;
  /** Gets the locale to run tests with */
  static final String TEST_LOCALE = LuceneTestCaseJ4.TEST_LOCALE;
  /** Gets the timezone to run tests with */
  static final String TEST_TIMEZONE = LuceneTestCaseJ4.TEST_TIMEZONE;
  /** Gets the directory to run tests with */
  static final String TEST_DIRECTORY = LuceneTestCaseJ4.TEST_DIRECTORY;
  
  /**
   * A random multiplier which you should use when writing random tests:
   * multiply it by the number of iterations
   */
  public static final int RANDOM_MULTIPLIER = LuceneTestCaseJ4.RANDOM_MULTIPLIER;
  
  private int savedBoolMaxClauseCount;
  
  private volatile Thread.UncaughtExceptionHandler savedUncaughtExceptionHandler = null;
  
  private Codec codec;

  private Locale locale;
  private Locale savedLocale;
  private TimeZone timeZone;
  private TimeZone savedTimeZone;

  private Map<MockDirectoryWrapper,StackTraceElement[]> stores;

  /** Used to track if setUp and tearDown are called correctly from subclasses */
  private boolean setup;

  private static class UncaughtExceptionEntry {
    public final Thread thread;
    public final Throwable exception;
    
    public UncaughtExceptionEntry(Thread thread, Throwable exception) {
      this.thread = thread;
      this.exception = exception;
    }
  }
  private List<UncaughtExceptionEntry> uncaughtExceptions = Collections.synchronizedList(new ArrayList<UncaughtExceptionEntry>());

  public LuceneTestCase() {
    super();
  }

  public LuceneTestCase(String name) {
    super(name);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    assertFalse("ensure your tearDown() calls super.tearDown()!!!", setup);
    setup = true;
    stores = new IdentityHashMap<MockDirectoryWrapper,StackTraceElement[]>();
    savedUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
      public void uncaughtException(Thread t, Throwable e) {
        uncaughtExceptions.add(new UncaughtExceptionEntry(t, e));
        if (savedUncaughtExceptionHandler != null)
          savedUncaughtExceptionHandler.uncaughtException(t, e);
      }
    });
    
    ConcurrentMergeScheduler.setTestMode();
    savedBoolMaxClauseCount = BooleanQuery.getMaxClauseCount();
    codec = LuceneTestCaseJ4.installTestCodecs();
    savedLocale = Locale.getDefault();
    locale = TEST_LOCALE.equals("random") 
      ? LuceneTestCaseJ4.randomLocale(seedRnd) 
      : LuceneTestCaseJ4.localeForName(TEST_LOCALE);
    Locale.setDefault(locale);
    savedTimeZone = TimeZone.getDefault();
    timeZone = TEST_TIMEZONE.equals("random")
      ? LuceneTestCaseJ4.randomTimeZone(seedRnd)
      : TimeZone.getTimeZone(TEST_TIMEZONE);
    TimeZone.setDefault(timeZone);
  }

  /**
   * Forcible purges all cache entries from the FieldCache.
   * <p>
   * This method will be called by tearDown to clean up FieldCache.DEFAULT.
   * If a (poorly written) test has some expectation that the FieldCache
   * will persist across test methods (ie: a static IndexReader) this 
   * method can be overridden to do nothing.
   * </p>
   * @see FieldCache#purgeAllCaches()
   */
  protected void purgeFieldCache(final FieldCache fc) {
    fc.purgeAllCaches();
  }

  protected String getTestLabel() {
    return getClass().getName() + "." + getName();
  }

  @Override
  protected void tearDown() throws Exception {
    assertTrue("ensure your setUp() calls super.setUp()!!!", setup);
    setup = false;
    BooleanQuery.setMaxClauseCount(savedBoolMaxClauseCount);
    LuceneTestCaseJ4.removeTestCodecs(codec);
    Locale.setDefault(savedLocale);
    TimeZone.setDefault(savedTimeZone);
    System.clearProperty("solr.solr.home");
    System.clearProperty("solr.data.dir");
    try {
      Thread.setDefaultUncaughtExceptionHandler(savedUncaughtExceptionHandler);
      if (!uncaughtExceptions.isEmpty()) {
        System.err.println("The following exceptions were thrown by threads:");
        for (UncaughtExceptionEntry entry : uncaughtExceptions) {
          System.err.println("*** Thread: " + entry.thread.getName() + " ***");
          entry.exception.printStackTrace(System.err);
        }
        fail("Some threads threw uncaught exceptions!");
      }

      // this isn't as useful as calling directly from the scope where the 
      // index readers are used, because they could be gc'ed just before
      // tearDown is called.
      // But it's better then nothing.
      assertSaneFieldCaches(getTestLabel());
      
      if (ConcurrentMergeScheduler.anyUnhandledExceptions()) {
        // Clear the failure so that we don't just keep
        // failing subsequent test cases
        ConcurrentMergeScheduler.clearUnhandledExceptions();
        fail("ConcurrentMergeScheduler hit unhandled exceptions");
      }
    } finally {
      purgeFieldCache(FieldCache.DEFAULT);
    }

    // now look for unclosed resources
    for (MockDirectoryWrapper d : stores.keySet()) {
      if (d.isOpen()) {
        StackTraceElement elements[] = stores.get(d);
        StackTraceElement element = (elements.length > 1) ? elements[1] : null;
        fail("directory of testcase " + getName() + " was not closed, opened from: " + element);
      }
    }
    stores = null;
    super.tearDown();
  }

  /** 
   * Asserts that FieldCacheSanityChecker does not detect any 
   * problems with FieldCache.DEFAULT.
   * <p>
   * If any problems are found, they are logged to System.err 
   * (allong with the msg) when the Assertion is thrown.
   * </p>
   * <p>
   * This method is called by tearDown after every test method, 
   * however IndexReaders scoped inside test methods may be garbage 
   * collected prior to this method being called, causing errors to 
   * be overlooked. Tests are encouraged to keep their IndexReaders 
   * scoped at the class level, or to explicitly call this method 
   * directly in the same scope as the IndexReader.
   * </p>
   * @see FieldCacheSanityChecker
   */
  protected void assertSaneFieldCaches(final String msg) {
    final CacheEntry[] entries = FieldCache.DEFAULT.getCacheEntries();
    Insanity[] insanity = null;
    try {
      try {
        insanity = FieldCacheSanityChecker.checkSanity(entries);
      } catch (RuntimeException e) {
        dumpArray(msg+ ": FieldCache", entries, System.err);
        throw e;
      }

      assertEquals(msg + ": Insane FieldCache usage(s) found", 
                   0, insanity.length);
      insanity = null;
    } finally {

      // report this in the event of any exception/failure
      // if no failure, then insanity will be null anyway
      if (null != insanity) {
        dumpArray(msg + ": Insane FieldCache usage(s)", insanity, System.err);
      }

    }
  }

  /**
   * Convinience method for logging an iterator.
   * @param label String logged before/after the items in the iterator
   * @param iter Each next() is toString()ed and logged on it's own line. If iter is null this is logged differnetly then an empty iterator.
   * @param stream Stream to log messages to.
   */
  public static <T> void dumpIterator(String label, Iterator<T> iter, 
                                  PrintStream stream) {
    stream.println("*** BEGIN "+label+" ***");
    if (null == iter) {
      stream.println(" ... NULL ...");
    } else {
      while (iter.hasNext()) {
        stream.println(iter.next().toString());
      }
    }
    stream.println("*** END "+label+" ***");
  }

  /** 
   * Convinience method for logging an array.  Wraps the array in an iterator and delegates
   * @see #dumpIterator(String,Iterator,PrintStream)
   */
  public static void dumpArray(String label, Object[] objs, 
                               PrintStream stream) {
    Iterator<Object> iter = (null == objs) ? null : Arrays.asList(objs).iterator();
    dumpIterator(label, iter, stream);
  }
  
  /**
   * Returns a {@link Random} instance for generating random numbers during the test.
   * The random seed is logged during test execution and printed to System.out on any failure
   * for reproducing the test using {@link #newRandom(long)} with the recorded seed
   *.
   */
  public Random newRandom() {
    if (seed != null) {
      throw new IllegalStateException("please call LuceneTestCase.newRandom only once per test");
    }
    this.seed = Long.valueOf(seedRnd.nextLong());
    if (VERBOSE) {
      System.out.println("NOTE: random seed of testcase '" + getName() + "' is: " + this.seed);
    }
    return new Random(seed);
  }
  
  /**
   * Returns a {@link Random} instance for generating random numbers during the test.
   * If an error occurs in the test that is not reproducible, you can use this method to
   * initialize the number generator with the seed that was printed out during the failing test.
   */
  public Random newRandom(long seed) {
    if (this.seed != null) {
      throw new IllegalStateException("please call LuceneTestCase.newRandom only once per test");
    }
    System.out.println("WARNING: random seed of testcase '" + getName() + "' is fixed to: " + seed);
    this.seed = Long.valueOf(seed);
    return new Random(seed);
  }

  /** create a new index writer config with random defaults */
  public static IndexWriterConfig newIndexWriterConfig(Random r, Version v, Analyzer a) {
    return LuceneTestCaseJ4.newIndexWriterConfig(r, v, a);
  }

  /**
   * Returns a new Dictionary instance. Use this when the test does not
   * care about the specific Directory implementation (most tests).
   * <p>
   * The Directory is wrapped with {@link MockDirectoryWrapper}.
   * By default this means it will be picky, such as ensuring that you
   * properly close it and all open files in your test. It will emulate
   * some features of Windows, such as not allowing open files to be
   * overwritten.
   */
  public MockDirectoryWrapper newDirectory(Random r) throws IOException {
    StackTraceElement[] stack = new Exception().getStackTrace();
    Directory impl = LuceneTestCaseJ4.newDirectoryImpl(r, TEST_DIRECTORY);
    MockDirectoryWrapper dir = new MockDirectoryWrapper(impl);
    stores.put(dir, stack);
    return dir;
  }
  
  /**
   * Returns a new Dictionary instance, with contents copied from the
   * provided directory. See {@link #newDirectory(Random)} for more
   * information.
   */
  public MockDirectoryWrapper newDirectory(Random r, Directory d) throws IOException {
    StackTraceElement[] stack = new Exception().getStackTrace();
    Directory impl = LuceneTestCaseJ4.newDirectoryImpl(r, TEST_DIRECTORY);
    for (String file : d.listAll()) {
     d.copy(impl, file, file);
    }
    MockDirectoryWrapper dir = new MockDirectoryWrapper(impl);
    stores.put(dir, stack);
    return dir;
  }
  
  /** Gets a resource from the classpath as {@link File}. This method should only be used,
   * if a real file is needed. To get a stream, code should prefer
   * {@link Class#getResourceAsStream} using {@code this.getClass()}.
   */
  protected File getDataFile(String name) throws IOException {
    try {
      return new File(this.getClass().getResource(name).toURI());
    } catch (Exception e) {
      throw new IOException("Cannot find resource: " + name);
    }
  }
  

  @Override
  public void run(TestResult result) {
    for (int i = 0; i < LuceneTestCaseJ4.TEST_ITER; i++)
      if (LuceneTestCaseJ4.TEST_METHOD == null || 
        getName().equals(LuceneTestCaseJ4.TEST_METHOD))
        super.run(result);
  }
  
  @Override
  public void runBare() throws Throwable {
    //long t0 = System.currentTimeMillis();
    try {
      seed = null;
      super.runBare();
    } catch (Throwable e) {
      System.out.println("NOTE: random codec of testcase '" + getName() + "' was: " + codec);
      if (TEST_LOCALE.equals("random"))
        System.out.println("NOTE: random locale of testcase '" + getName() + "' was: " + locale);
      if (TEST_TIMEZONE.equals("random")) // careful to not deliver NPE here in case they forgot super.setUp
        System.out.println("NOTE: random timezone of testcase '" + getName() + "' was: " + (timeZone == null ? "(null)" : timeZone.getID()));
      if (seed != null) {
        System.out.println("NOTE: random seed of testcase '" + getName() + "' was: " + seed);
      }
      throw e;
    }
    //long t = System.currentTimeMillis() - t0;
    //System.out.println(t + " msec for " + getName());
  }
  
  // recorded seed
  protected Long seed = null;
  
  // static members
  private static final Random seedRnd = new Random();
}
