package org.apache.solr.search;
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

import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.common.SolrException;
import org.apache.solr.schema.IndexSchema;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 *
 **/
public class QueryParsingTest extends SolrTestCaseJ4 {
  @BeforeClass
  public static void beforeClass() throws Exception {
    initCore("solrconfig.xml","schema.xml");
  }

  @Test
  public void testSort() throws Exception {
    Sort sort;

    IndexSchema schema = h.getCore().getSchema();
    sort = QueryParsing.parseSort("score desc", schema);
    assertNull("sort", sort);//only 1 thing in the list, no Sort specified

    sort = QueryParsing.parseSort("score asc", schema);
    SortField[] flds = sort.getSort();
    assertEquals(flds[0].getType(), SortField.SCORE);
    assertTrue(flds[0].getReverse());

    sort = QueryParsing.parseSort("weight desc", schema);
    flds = sort.getSort();
    assertEquals(flds[0].getType(), SortField.FLOAT);
    assertEquals(flds[0].getField(), "weight");
    assertEquals(flds[0].getReverse(), true);
    sort = QueryParsing.parseSort("weight desc,bday asc", schema);
    flds = sort.getSort();
    assertEquals(flds[0].getType(), SortField.FLOAT);
    assertEquals(flds[0].getField(), "weight");
    assertEquals(flds[0].getReverse(), true);
    assertEquals(flds[1].getType(), SortField.LONG);
    assertEquals(flds[1].getField(), "bday");
    assertEquals(flds[1].getReverse(), false);
    //order aliases
    sort = QueryParsing.parseSort("weight top,bday asc", schema);
    flds = sort.getSort();
    assertEquals(flds[0].getType(), SortField.FLOAT);
    assertEquals(flds[0].getField(), "weight");
    assertEquals(flds[0].getReverse(), true);
    assertEquals(flds[1].getType(), SortField.LONG);
    assertEquals(flds[1].getField(), "bday");
    assertEquals(flds[1].getReverse(), false);
    sort = QueryParsing.parseSort("weight top,bday bottom", schema);
    flds = sort.getSort();
    assertEquals(flds[0].getType(), SortField.FLOAT);
    assertEquals(flds[0].getField(), "weight");
    assertEquals(flds[0].getReverse(), true);
    assertEquals(flds[1].getType(), SortField.LONG);
    assertEquals(flds[1].getField(), "bday");
    assertEquals(flds[1].getReverse(), false);

    //test weird spacing
    sort = QueryParsing.parseSort("weight         desc,            bday         asc", schema);
    flds = sort.getSort();
    assertEquals(flds[0].getType(), SortField.FLOAT);
    assertEquals(flds[0].getField(), "weight");
    assertEquals(flds[1].getField(), "bday");
    assertEquals(flds[1].getType(), SortField.LONG);
    //handles trailing commas
    sort = QueryParsing.parseSort("weight desc,", schema);
    flds = sort.getSort();
    assertEquals(flds[0].getType(), SortField.FLOAT);
    assertEquals(flds[0].getField(), "weight");

    //test functions
    sort = QueryParsing.parseSort("pow(weight, 2) desc", schema);
    flds = sort.getSort();
    assertEquals(flds[0].getType(), SortField.CUSTOM);
    //Not thrilled about the fragility of string matching here, but...
    //the value sources get wrapped, so the out field is different than the input
    assertEquals(flds[0].getField(), "pow(float(weight),const(2.0))");
    
    //test functions (more deep)
    sort = QueryParsing.parseSort("sum(product(r_f,sum(d_f,t_f,1)),a_f) asc", schema);
    flds = sort.getSort();
    assertEquals(flds[0].getType(), SortField.CUSTOM);
    assertEquals(flds[0].getField(), "sum(product(float(r_f),sum(float(d_f),float(t_f),const(1.0))),float(a_f))");

    sort = QueryParsing.parseSort("pow(weight,                 2)         desc", schema);
    flds = sort.getSort();
    assertEquals(flds[0].getType(), SortField.CUSTOM);
    //Not thrilled about the fragility of string matching here, but...
    //the value sources get wrapped, so the out field is different than the input
    assertEquals(flds[0].getField(), "pow(float(weight),const(2.0))");


    sort = QueryParsing.parseSort("pow(weight, 2) desc, weight    desc,   bday    asc", schema);
    flds = sort.getSort();
    assertEquals(flds[0].getType(), SortField.CUSTOM);

    //Not thrilled about the fragility of string matching here, but...
    //the value sources get wrapped, so the out field is different than the input
    assertEquals(flds[0].getField(), "pow(float(weight),const(2.0))");

    assertEquals(flds[1].getType(), SortField.FLOAT);
    assertEquals(flds[1].getField(), "weight");
    assertEquals(flds[2].getField(), "bday");
    assertEquals(flds[2].getType(), SortField.LONG);
    
    //handles trailing commas
    sort = QueryParsing.parseSort("weight desc,", schema);
    flds = sort.getSort();
    assertEquals(flds[0].getType(), SortField.FLOAT);
    assertEquals(flds[0].getField(), "weight");

    try {
      //bad number of parens, but the function parser can handle an extra close
      sort = QueryParsing.parseSort("pow(weight,2)) desc, bday asc", schema);
    } catch (SolrException e) {
      assertTrue(false);
    }
    //Test literals in functions
    sort = QueryParsing.parseSort("strdist(foo_s, \"junk\", jw) desc", schema);
    flds = sort.getSort();
    assertEquals(flds[0].getType(), SortField.CUSTOM);
    //the value sources get wrapped, so the out field is different than the input
    assertEquals(flds[0].getField(), "strdist(str(foo_s),literal(junk), dist=org.apache.lucene.search.spell.JaroWinklerDistance)");

    sort = QueryParsing.parseSort("", schema);
    assertNull(sort);

  }

  @Test
  public void testBad() throws Exception {
    Sort sort;

    IndexSchema schema = h.getCore().getSchema();
    //test some bad vals
    try {
      sort = QueryParsing.parseSort("weight, desc", schema);
      assertTrue(false);
    } catch (SolrException e) {
      //expected
    }
    try {
      sort = QueryParsing.parseSort("w", schema);
      assertTrue(false);
    } catch (SolrException e) {
      //expected
    }
    try {
      sort = QueryParsing.parseSort("weight desc, bday", schema);
      assertTrue(false);
    } catch (SolrException e) {
    }

    try {
      //bad number of commas
      sort = QueryParsing.parseSort("pow(weight,,2) desc, bday asc", schema);
      assertTrue(false);
    } catch (SolrException e) {
    }

    try {
      //bad function
      sort = QueryParsing.parseSort("pow() desc, bday asc", schema);
      assertTrue(false);
    } catch (SolrException e) {
    }

    try {
      //bad number of parens
      sort = QueryParsing.parseSort("pow((weight,2) desc, bday asc", schema);
      assertTrue(false);
    } catch (SolrException e) {
    }

  }

}
