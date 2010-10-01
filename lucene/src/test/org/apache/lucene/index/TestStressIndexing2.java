package org.apache.lucene.index;

/**
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.apache.lucene.util.*;

import junit.framework.Assert;

import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;

public class TestStressIndexing2 extends MultiCodecTestCase {
  static int maxFields=4;
  static int bigFieldSize=10;
  static boolean sameFieldOrder=false;
  static int mergeFactor=3;
  static int maxBufferedDocs=3;
  static int seed=0;

  Random r;

  public class MockIndexWriter extends IndexWriter {

    public MockIndexWriter(Directory dir, IndexWriterConfig conf) throws IOException {
      super(dir, conf);
    }

    @Override
    boolean testPoint(String name) {
      //      if (name.equals("startCommit")) {
      if (r.nextInt(4) == 2)
        Thread.yield();
      return true;
    }
  }
  
  public void testRandomIWReader() throws Throwable {
    r = newRandom();
    Directory dir = newDirectory(r);
    
    // TODO: verify equals using IW.getReader
    DocsAndWriter dw = indexRandomIWReader(5, 3, 100, dir);
    IndexReader reader = dw.writer.getReader();
    dw.writer.commit();
    verifyEquals(r, reader, dir, "id");
    reader.close();
    dw.writer.close();
    dir.close();
  }
  
  public void testRandom() throws Throwable {
    r = newRandom();
    Directory dir1 = newDirectory(r);
    // dir1 = FSDirectory.open("foofoofoo");
    Directory dir2 = newDirectory(r);
    // mergeFactor=2; maxBufferedDocs=2; Map docs = indexRandom(1, 3, 2, dir1);
    int maxThreadStates = 1+r.nextInt(10);
    boolean doReaderPooling = r.nextBoolean();
    Map<String,Document> docs = indexRandom(5, 3, 100, dir1, maxThreadStates, doReaderPooling);
    indexSerial(r, docs, dir2);

    // verifying verify
    // verifyEquals(dir1, dir1, "id");
    // verifyEquals(dir2, dir2, "id");

    verifyEquals(dir1, dir2, "id");
    dir1.close();
    dir2.close();
  }

  public void testMultiConfig() throws Throwable {
    // test lots of smaller different params together

    r = newRandom();

    int num = 3 * RANDOM_MULTIPLIER;
    for (int i = 0; i < num; i++) { // increase iterations for better testing
      sameFieldOrder=r.nextBoolean();
      mergeFactor=r.nextInt(3)+2;
      maxBufferedDocs=r.nextInt(3)+2;
      int maxThreadStates = 1+r.nextInt(10);
      boolean doReaderPooling = r.nextBoolean();
      seed++;

      int nThreads=r.nextInt(5)+1;
      int iter=r.nextInt(5)+1;
      int range=r.nextInt(20)+1;
      Directory dir1 = newDirectory(r);
      Directory dir2 = newDirectory(r);
      Map<String,Document> docs = indexRandom(nThreads, iter, range, dir1, maxThreadStates, doReaderPooling);
      //System.out.println("TEST: index serial");
      indexSerial(r, docs, dir2);
      //System.out.println("TEST: verify");
      verifyEquals(dir1, dir2, "id");
      dir1.close();
      dir2.close();
    }
  }


  static Term idTerm = new Term("id","");
  IndexingThread[] threads;
  static Comparator<Fieldable> fieldNameComparator = new Comparator<Fieldable>() {
        public int compare(Fieldable o1, Fieldable o2) {
          return o1.name().compareTo(o2.name());
        }
  };

  // This test avoids using any extra synchronization in the multiple
  // indexing threads to test that IndexWriter does correctly synchronize
  // everything.
  
  public static class DocsAndWriter {
    Map<String,Document> docs;
    IndexWriter writer;
  }
  
  public DocsAndWriter indexRandomIWReader(int nThreads, int iterations, int range, Directory dir) throws IOException, InterruptedException {
    Map<String,Document> docs = new HashMap<String,Document>();
    IndexWriter w = new MockIndexWriter(dir, newIndexWriterConfig(r,
        TEST_VERSION_CURRENT, new MockAnalyzer()).setOpenMode(OpenMode.CREATE).setRAMBufferSizeMB(
        0.1).setMaxBufferedDocs(maxBufferedDocs));
    w.commit();
    LogMergePolicy lmp = (LogMergePolicy) w.getConfig().getMergePolicy();
    lmp.setUseCompoundFile(false);
    lmp.setUseCompoundDocStore(false);
    lmp.setMergeFactor(mergeFactor);
    /***
        w.setMaxMergeDocs(Integer.MAX_VALUE);
        w.setMaxFieldLength(10000);
        w.setRAMBufferSizeMB(1);
        w.setMergeFactor(10);
    ***/

    threads = new IndexingThread[nThreads];
    for (int i=0; i<threads.length; i++) {
      IndexingThread th = new IndexingThread();
      th.w = w;
      th.base = 1000000*i;
      th.range = range;
      th.iterations = iterations;
      threads[i] = th;
    }

    for (int i=0; i<threads.length; i++) {
      threads[i].start();
    }
    for (int i=0; i<threads.length; i++) {
      threads[i].join();
    }

    // w.optimize();
    //w.close();    

    for (int i=0; i<threads.length; i++) {
      IndexingThread th = threads[i];
      synchronized(th) {
        docs.putAll(th.docs);
      }
    }

    _TestUtil.checkIndex(dir);
    DocsAndWriter dw = new DocsAndWriter();
    dw.docs = docs;
    dw.writer = w;
    return dw;
  }
  
  public Map<String,Document> indexRandom(int nThreads, int iterations, int range, Directory dir, int maxThreadStates,
                                          boolean doReaderPooling) throws IOException, InterruptedException {
    Map<String,Document> docs = new HashMap<String,Document>();
    for(int iter=0;iter<3;iter++) {
      IndexWriter w = new MockIndexWriter(dir, newIndexWriterConfig(r,
          TEST_VERSION_CURRENT, new MockAnalyzer()).setOpenMode(OpenMode.CREATE)
               .setRAMBufferSizeMB(0.1).setMaxBufferedDocs(maxBufferedDocs).setMaxThreadStates(maxThreadStates)
               .setReaderPooling(doReaderPooling));
      LogMergePolicy lmp = (LogMergePolicy) w.getConfig().getMergePolicy();
      lmp.setUseCompoundFile(false);
      lmp.setUseCompoundDocStore(false);
      lmp.setMergeFactor(mergeFactor);

      threads = new IndexingThread[nThreads];
      for (int i=0; i<threads.length; i++) {
        IndexingThread th = new IndexingThread();
        th.w = w;
        th.base = 1000000*i;
        th.range = range;
        th.iterations = iterations;
        threads[i] = th;
      }

      for (int i=0; i<threads.length; i++) {
        threads[i].start();
      }
      for (int i=0; i<threads.length; i++) {
        threads[i].join();
      }

      //w.optimize();
      w.close();    

      for (int i=0; i<threads.length; i++) {
        IndexingThread th = threads[i];
        synchronized(th) {
          docs.putAll(th.docs);
        }
      }
    }

    //System.out.println("TEST: checkindex");
    _TestUtil.checkIndex(dir);

    return docs;
  }

  
  public static void indexSerial(Random random, Map<String,Document> docs, Directory dir) throws IOException {
    IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(random, TEST_VERSION_CURRENT, new MockAnalyzer()));

    // index all docs in a single thread
    Iterator<Document> iter = docs.values().iterator();
    while (iter.hasNext()) {
      Document d = iter.next();
      ArrayList<Fieldable> fields = new ArrayList<Fieldable>();
      fields.addAll(d.getFields());
      // put fields in same order each time
      Collections.sort(fields, fieldNameComparator);
      
      Document d1 = new Document();
      d1.setBoost(d.getBoost());
      for (int i=0; i<fields.size(); i++) {
        d1.add(fields.get(i));
      }
      w.addDocument(d1);
      // System.out.println("indexing "+d1);
    }
    
    w.close();
  }
  
  public static void verifyEquals(Random r, IndexReader r1, Directory dir2, String idField) throws Throwable {
    IndexReader r2 = IndexReader.open(dir2);
    verifyEquals(r1, r2, idField);
    r2.close();
  }

  public static void verifyEquals(Directory dir1, Directory dir2, String idField) throws Throwable {
    IndexReader r1 = IndexReader.open(dir1, true);
    IndexReader r2 = IndexReader.open(dir2, true);
    verifyEquals(r1, r2, idField);
    r1.close();
    r2.close();
  }


  public static void verifyEquals(IndexReader r1, IndexReader r2, String idField) throws Throwable {
    assertEquals(r1.numDocs(), r2.numDocs());
    boolean hasDeletes = !(r1.maxDoc()==r2.maxDoc() && r1.numDocs()==r1.maxDoc());

    int[] r2r1 = new int[r2.maxDoc()];   // r2 id to r1 id mapping

    // create mapping from id2 space to id2 based on idField
    idField = StringHelper.intern(idField);
    final Fields f1 = MultiFields.getFields(r1);
    if (f1 == null) {
      // make sure r2 is empty
      assertNull(MultiFields.getFields(r2));
      return;
    }
    final Terms terms1 = f1.terms(idField);
    if (terms1 == null) {
      assertTrue(MultiFields.getFields(r2) == null ||
                 MultiFields.getFields(r2).terms(idField) == null);
      return;
    }
    final TermsEnum termsEnum = terms1.iterator();

    final Bits delDocs1 = MultiFields.getDeletedDocs(r1);
    final Bits delDocs2 = MultiFields.getDeletedDocs(r2);
    
    Fields fields = MultiFields.getFields(r2);
    if (fields == null) {
      // make sure r1 is in fact empty (eg has only all
      // deleted docs):
      DocsEnum docs = null;
      while(termsEnum.next() != null) {
        docs = termsEnum.docs(delDocs1, docs);
        while(docs.nextDoc() != DocsEnum.NO_MORE_DOCS) {
          fail("r1 is not empty but r2 is");
        }
      }
      return;
    }
    Terms terms2 = fields.terms(idField);

    DocsEnum termDocs1 = null;
    DocsEnum termDocs2 = null;

    while(true) {
      BytesRef term = termsEnum.next();
      //System.out.println("TEST: match id term=" + term);
      if (term == null) {
        break;
      }

      termDocs1 = termsEnum.docs(delDocs1, termDocs1);
      termDocs2 = terms2.docs(delDocs2, term, termDocs2);

      if (termDocs1.nextDoc() == DocsEnum.NO_MORE_DOCS) {
        // This doc is deleted and wasn't replaced
        assertTrue(termDocs2 == null || termDocs2.nextDoc() == DocsEnum.NO_MORE_DOCS);
        continue;
      }

      int id1 = termDocs1.docID();
      assertEquals(DocsEnum.NO_MORE_DOCS, termDocs1.nextDoc());

      assertTrue(termDocs2.nextDoc() != DocsEnum.NO_MORE_DOCS);
      int id2 = termDocs2.docID();
      assertEquals(DocsEnum.NO_MORE_DOCS, termDocs2.nextDoc());

      r2r1[id2] = id1;

      // verify stored fields are equivalent
      try {
        verifyEquals(r1.document(id1), r2.document(id2));
      } catch (Throwable t) {
        System.out.println("FAILED id=" + term + " id1=" + id1 + " id2=" + id2 + " term="+ term);
        System.out.println("  d1=" + r1.document(id1));
        System.out.println("  d2=" + r2.document(id2));
        throw t;
      }

      try {
        // verify term vectors are equivalent        
        verifyEquals(r1.getTermFreqVectors(id1), r2.getTermFreqVectors(id2));
      } catch (Throwable e) {
        System.out.println("FAILED id=" + term + " id1=" + id1 + " id2=" + id2);
        TermFreqVector[] tv1 = r1.getTermFreqVectors(id1);
        System.out.println("  d1=" + tv1);
        if (tv1 != null)
          for(int i=0;i<tv1.length;i++)
            System.out.println("    " + i + ": " + tv1[i]);
        
        TermFreqVector[] tv2 = r2.getTermFreqVectors(id2);
        System.out.println("  d2=" + tv2);
        if (tv2 != null)
          for(int i=0;i<tv2.length;i++)
            System.out.println("    " + i + ": " + tv2[i]);
        
        throw e;
      }

    }

    //System.out.println("TEST: done match id");

    // Verify postings
    //System.out.println("TEST: create te1");
    final FieldsEnum fields1 = MultiFields.getFields(r1).iterator();
    final FieldsEnum fields2 = MultiFields.getFields(r2).iterator();

    String field1=null, field2=null;
    TermsEnum termsEnum1 = null;
    TermsEnum termsEnum2 = null;
    DocsEnum docs1=null, docs2=null;

    // pack both doc and freq into single element for easy sorting
    long[] info1 = new long[r1.numDocs()];
    long[] info2 = new long[r2.numDocs()];

    for(;;) {
      BytesRef term1=null, term2=null;

      // iterate until we get some docs
      int len1;
      for(;;) {
        len1=0;
        if (termsEnum1 == null) {
          field1 = fields1.next();
          if (field1 == null) {
            break;
          } else {
            termsEnum1 = fields1.terms();
          }
        }
        term1 = termsEnum1.next();
        if (term1 == null) {
          // no more terms in this field
          termsEnum1 = null;
          continue;
        }
        
        //System.out.println("TEST: term1=" + term1);
        docs1 = termsEnum1.docs(delDocs1, docs1);
        while (docs1.nextDoc() != DocsEnum.NO_MORE_DOCS) {
          int d = docs1.docID();
          int f = docs1.freq();
          info1[len1] = (((long)d)<<32) | f;
          len1++;
        }
        if (len1>0) break;
      }

      // iterate until we get some docs
      int len2;
      for(;;) {
        len2=0;
        if (termsEnum2 == null) {
          field2 = fields2.next();
          if (field2 == null) {
            break;
          } else {
            termsEnum2 = fields2.terms();
          }
        }
        term2 = termsEnum2.next();
        if (term2 == null) {
          // no more terms in this field
          termsEnum2 = null;
          continue;
        }
        
        //System.out.println("TEST: term1=" + term1);
        docs2 = termsEnum2.docs(delDocs2, docs2);
        while (docs2.nextDoc() != DocsEnum.NO_MORE_DOCS) {
          int d = r2r1[docs2.docID()];
          int f = docs2.freq();
          info2[len2] = (((long)d)<<32) | f;
          len2++;
        }
        if (len2>0) break;
      }

      assertEquals(len1, len2);
      if (len1==0) break;  // no more terms

      assertEquals(field1, field2);
      assertTrue(term1.bytesEquals(term2));

      if (!hasDeletes)
        assertEquals(termsEnum1.docFreq(), termsEnum2.docFreq());

      assertEquals("len1=" + len1 + " len2=" + len2 + " deletes?=" + hasDeletes, term1, term2);

      // sort info2 to get it into ascending docid
      Arrays.sort(info2, 0, len2);

      // now compare
      for (int i=0; i<len1; i++) {
        assertEquals("i=" + i + " len=" + len1 + " d1=" + (info1[i]>>>32) + " f1=" + (info1[i]&Integer.MAX_VALUE) + " d2=" + (info2[i]>>>32) + " f2=" + (info2[i]&Integer.MAX_VALUE) +
                     " field=" + field1 + " term=" + term1.utf8ToString(),
                     info1[i],
                     info2[i]);
      }
    }
  }

  public static void verifyEquals(Document d1, Document d2) {
    List<Fieldable> ff1 = d1.getFields();
    List<Fieldable> ff2 = d2.getFields();

    Collections.sort(ff1, fieldNameComparator);
    Collections.sort(ff2, fieldNameComparator);

    assertEquals(ff1 + " : " + ff2, ff1.size(), ff2.size());

    for (int i=0; i<ff1.size(); i++) {
      Fieldable f1 = ff1.get(i);
      Fieldable f2 = ff2.get(i);
      if (f1.isBinary()) {
        assert(f2.isBinary());
      } else {
        String s1 = f1.stringValue();
        String s2 = f2.stringValue();
        assertEquals(ff1 + " : " + ff2, s1,s2);
        }
      }
    }

  public static void verifyEquals(TermFreqVector[] d1, TermFreqVector[] d2) {
    if (d1 == null) {
      assertTrue(d2 == null);
      return;
    }
    assertTrue(d2 != null);

    assertEquals(d1.length, d2.length);
    for(int i=0;i<d1.length;i++) {
      TermFreqVector v1 = d1[i];
      TermFreqVector v2 = d2[i];
      if (v1 == null || v2 == null)
        System.out.println("v1=" + v1 + " v2=" + v2 + " i=" + i + " of " + d1.length);
      assertEquals(v1.size(), v2.size());
      int numTerms = v1.size();
      BytesRef[] terms1 = v1.getTerms();
      BytesRef[] terms2 = v2.getTerms();
      int[] freq1 = v1.getTermFrequencies();
      int[] freq2 = v2.getTermFrequencies();
      for(int j=0;j<numTerms;j++) {
        if (!terms1[j].equals(terms2[j]))
          assertEquals(terms1[j], terms2[j]);
        assertEquals(freq1[j], freq2[j]);
      }
      if (v1 instanceof TermPositionVector) {
        assertTrue(v2 instanceof TermPositionVector);
        TermPositionVector tpv1 = (TermPositionVector) v1;
        TermPositionVector tpv2 = (TermPositionVector) v2;
        for(int j=0;j<numTerms;j++) {
          int[] pos1 = tpv1.getTermPositions(j);
          int[] pos2 = tpv2.getTermPositions(j);
          assertEquals(pos1.length, pos2.length);
          TermVectorOffsetInfo[] offsets1 = tpv1.getOffsets(j);
          TermVectorOffsetInfo[] offsets2 = tpv2.getOffsets(j);
          if (offsets1 == null)
            assertTrue(offsets2 == null);
          else
            assertTrue(offsets2 != null);
          for(int k=0;k<pos1.length;k++) {
            assertEquals(pos1[k], pos2[k]);
            if (offsets1 != null) {
              assertEquals(offsets1[k].getStartOffset(),
                           offsets2[k].getStartOffset());
              assertEquals(offsets1[k].getEndOffset(),
                           offsets2[k].getEndOffset());
            }
          }
        }
      }
    }
  }

  private static class IndexingThread extends Thread {
    IndexWriter w;
    int base;
    int range;
    int iterations;
    Map<String,Document> docs = new HashMap<String,Document>();  
    Random r;

    public int nextInt(int lim) {
      return r.nextInt(lim);
    }

    // start is inclusive and end is exclusive
    public int nextInt(int start, int end) {
      return start + r.nextInt(end-start);
    }

    char[] buffer = new char[100];

    private int addUTF8Token(int start) {
      final int end = start + nextInt(20);
      if (buffer.length < 1+end) {
        char[] newBuffer = new char[(int) ((1+end)*1.25)];
        System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
        buffer = newBuffer;
      }

      for(int i=start;i<end;i++) {
        int t = nextInt(6);
        if (0 == t && i < end-1) {
          // Make a surrogate pair
          // High surrogate
          buffer[i++] = (char) nextInt(0xd800, 0xdc00);
          // Low surrogate
          buffer[i] = (char) nextInt(0xdc00, 0xe000);
        } else if (t <= 1)
          buffer[i] = (char) nextInt(0x80);
        else if (2 == t)
          buffer[i] = (char) nextInt(0x80, 0x800);
        else if (3 == t)
          buffer[i] = (char) nextInt(0x800, 0xd800);
        else if (4 == t)
          buffer[i] = (char) nextInt(0xe000, 0xffff);
        else if (5 == t) {
          // Illegal unpaired surrogate
          if (r.nextBoolean())
            buffer[i] = (char) nextInt(0xd800, 0xdc00);
          else
            buffer[i] = (char) nextInt(0xdc00, 0xe000);
        }
      }
      buffer[end] = ' ';
      return 1+end;
    }

    public String getString(int nTokens) {
      nTokens = nTokens!=0 ? nTokens : r.nextInt(4)+1;

      // Half the time make a random UTF8 string
      if (r.nextBoolean())
        return getUTF8String(nTokens);

      // avoid StringBuffer because it adds extra synchronization.
      char[] arr = new char[nTokens*2];
      for (int i=0; i<nTokens; i++) {
        arr[i*2] = (char)('A' + r.nextInt(10));
        arr[i*2+1] = ' ';
      }
      return new String(arr);
    }
    
    public String getUTF8String(int nTokens) {
      int upto = 0;
      Arrays.fill(buffer, (char) 0);
      for(int i=0;i<nTokens;i++)
        upto = addUTF8Token(upto);
      return new String(buffer, 0, upto);
    }

    public String getIdString() {
      return Integer.toString(base + nextInt(range));
    }

    public void indexDoc() throws IOException {
      Document d = new Document();

      ArrayList<Field> fields = new ArrayList<Field>();      
      String idString = getIdString();
      Field idField =  new Field(idTerm.field(), idString, Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS);
      fields.add(idField);

      int nFields = nextInt(maxFields);
      for (int i=0; i<nFields; i++) {

        Field.TermVector tvVal = Field.TermVector.NO;
        switch (nextInt(4)) {
        case 0:
          tvVal = Field.TermVector.NO;
          break;
        case 1:
          tvVal = Field.TermVector.YES;
          break;
        case 2:
          tvVal = Field.TermVector.WITH_POSITIONS;
          break;
        case 3:
          tvVal = Field.TermVector.WITH_POSITIONS_OFFSETS;
          break;
        }
        
        switch (nextInt(4)) {
          case 0:
            fields.add(new Field("f" + nextInt(100), getString(1), Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS, tvVal));
            break;
          case 1:
            fields.add(new Field("f" + nextInt(100), getString(0), Field.Store.NO, Field.Index.ANALYZED, tvVal));
            break;
          case 2:
            fields.add(new Field("f" + nextInt(100), getString(0), Field.Store.YES, Field.Index.NO, Field.TermVector.NO));
            break;
          case 3:
            fields.add(new Field("f" + nextInt(100), getString(bigFieldSize), Field.Store.YES, Field.Index.ANALYZED, tvVal));
            break;          
        }
      }

      if (sameFieldOrder) {
        Collections.sort(fields, fieldNameComparator);
      } else {
        // random placement of id field also
        Collections.swap(fields,nextInt(fields.size()), 0);
      }

      for (int i=0; i<fields.size(); i++) {
        d.add(fields.get(i));
      }
      w.updateDocument(idTerm.createTerm(idString), d);
      // System.out.println("indexing "+d);
      docs.put(idString, d);
    }

    public void deleteDoc() throws IOException {
      String idString = getIdString();
      w.deleteDocuments(idTerm.createTerm(idString));
      docs.remove(idString);
    }

    public void deleteByQuery() throws IOException {
      String idString = getIdString();
      w.deleteDocuments(new TermQuery(idTerm.createTerm(idString)));
      docs.remove(idString);
    }

    @Override
    public void run() {
      try {
        r = new Random(base+range+seed);
        for (int i=0; i<iterations; i++) {
          int what = nextInt(100);
          if (what < 5) {
            deleteDoc();
          } else if (what < 10) {
            deleteByQuery();
          } else {
            indexDoc();
          }
        }
      } catch (Throwable e) {
        e.printStackTrace();
        Assert.fail(e.toString());
      }

      synchronized (this) {
        docs.size();
      }
    }
  }
}
