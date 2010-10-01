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
package org.apache.solr.analysis;

import java.io.StringReader;
import java.lang.reflect.Field;

import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.solr.core.Config;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.FieldType;
import org.apache.solr.util.AbstractSolrTestCase;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.util.Version;

/**
 * Tests for luceneMatchVersion property for analyzers
 */
public class TestLuceneMatchVersion extends AbstractSolrTestCase {

  @Override
  public String getSchemaFile() {
    return "schema-luceneMatchVersion.xml";
  }
  
  @Override
  public String getSolrConfigFile() {
    return "solrconfig.xml";
  }
  
  // this must match the solrconfig.xml version for this test
  public static final Version DEFAULT_VERSION =
    Config.parseLuceneVersionString(System.getProperty("tests.luceneMatchVersion", "LUCENE_CURRENT"));

  public void testStandardTokenizerVersions() throws Exception {
    assertEquals(DEFAULT_VERSION, solrConfig.luceneMatchVersion);
    
    final IndexSchema schema = h.getCore().getSchema();
    
    FieldType type = schema.getFieldType("textDefault");
    TokenizerChain ana = (TokenizerChain) type.getAnalyzer();
    assertEquals(DEFAULT_VERSION, ((BaseTokenizerFactory) ana.getTokenizerFactory()).luceneMatchVersion);
    assertEquals(DEFAULT_VERSION, ((BaseTokenFilterFactory) ana.getTokenFilterFactories()[2]).luceneMatchVersion);
    TokenizerChain.TokenStreamInfo tsi = ana.getStream("textDefault",new StringReader(""));
    StandardTokenizer tok = (StandardTokenizer) tsi.getTokenizer();
    assertTrue(tok.isReplaceInvalidAcronym());
    
    type = schema.getFieldType("text20");
    ana = (TokenizerChain) type.getAnalyzer();
    assertEquals(Version.LUCENE_20, ((BaseTokenizerFactory) ana.getTokenizerFactory()).luceneMatchVersion);
    assertEquals(Version.LUCENE_24, ((BaseTokenFilterFactory) ana.getTokenFilterFactories()[2]).luceneMatchVersion);
    tsi = ana.getStream("text20",new StringReader(""));
    tok = (StandardTokenizer) tsi.getTokenizer();
    assertFalse(tok.isReplaceInvalidAcronym());

    // this is a hack to get the private matchVersion field in StandardAnalyzer's superclass, may break in later lucene versions - we have no getter :(
    final Field matchVersionField = StandardAnalyzer.class.getSuperclass().getDeclaredField("matchVersion");
    matchVersionField.setAccessible(true);

    type = schema.getFieldType("textStandardAnalyzerDefault");
    Analyzer ana1 = type.getAnalyzer();
    assertTrue(ana1 instanceof StandardAnalyzer);
    assertEquals(DEFAULT_VERSION, matchVersionField.get(ana1));

    type = schema.getFieldType("textStandardAnalyzer20");
    ana1 = type.getAnalyzer();
    assertTrue(ana1 instanceof StandardAnalyzer);
    assertEquals(Version.LUCENE_20, matchVersionField.get(ana1));
  }
}
