package org.apache.solr.core;

import java.io.IOException;

import org.apache.lucene.index.ConcurrentMergeScheduler;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LogByteSizeMergePolicy;
import org.apache.solr.update.DirectUpdateHandler2;
import org.apache.solr.util.AbstractSolrTestCase;

public class TestPropInject extends AbstractSolrTestCase {
  public String getSchemaFile() {
    return "schema.xml";
  }

  public String getSolrConfigFile() {
    if ("testMergePolicyDefaults".equals(getName()) || "testPropsDefaults".equals(getName()))
      return "solrconfig-propinject-indexdefault.xml";
    else
      return "solrconfig-propinject.xml";
  }
  
  class ExposeWriterHandler extends DirectUpdateHandler2 {
    public ExposeWriterHandler() throws IOException {
      super(h.getCore());
    }

    public IndexWriter getWriter() throws IOException {
      forceOpenWriter();
      return writer;
    }
  }

  public void testMergePolicy() throws Exception {
    ExposeWriterHandler uh = new ExposeWriterHandler();
    IndexWriter writer = uh.getWriter();
    LogByteSizeMergePolicy mp = (LogByteSizeMergePolicy)writer.getMergePolicy();
    assertEquals(64.0, mp.getMaxMergeMB());
    uh.close();
  }

  public void testMergePolicyDefaults() throws Exception {
    ExposeWriterHandler uh = new ExposeWriterHandler();
    IndexWriter writer = uh.getWriter();
    LogByteSizeMergePolicy mp = (LogByteSizeMergePolicy)writer.getMergePolicy();
    assertEquals(32.0, mp.getMaxMergeMB());
    uh.close();
  }
  
  public void testProps() throws Exception {
    ExposeWriterHandler uh = new ExposeWriterHandler();
    IndexWriter writer = uh.getWriter();
    ConcurrentMergeScheduler cms = (ConcurrentMergeScheduler)writer.getMergeScheduler();
    assertEquals(2, cms.getMaxThreadCount());
    uh.close();
  }

  public void testPropsDefaults() throws Exception {
    ExposeWriterHandler uh = new ExposeWriterHandler();
    IndexWriter writer = uh.getWriter();
    ConcurrentMergeScheduler cms = (ConcurrentMergeScheduler)writer.getMergeScheduler();
    assertEquals(4, cms.getMaxThreadCount());
    uh.close();
  }
}
