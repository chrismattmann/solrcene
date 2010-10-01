package org.apache.lucene.queryParser.surround.query;
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


import java.util.List;
import java.util.Iterator;

import java.io.IOException;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanQuery;

public class DistanceQuery extends ComposedQuery implements DistanceSubQuery {
  public DistanceQuery(
      List<SrndQuery> queries,
      boolean infix,
      int opDistance,
      String opName,
      boolean ordered) {
    super(queries, infix, opName);
    this.opDistance = opDistance; /* the distance indicated in the operator */
    this.ordered = ordered;
  }

  private int opDistance;
  public int getOpDistance() {return opDistance;}
  
  private boolean ordered;
  public boolean subQueriesOrdered() {return ordered;}
  
  public String distanceSubQueryNotAllowed() {
    Iterator<?> sqi = getSubQueriesIterator();
    while (sqi.hasNext()) {
      Object leq = sqi.next();
      if (leq instanceof DistanceSubQuery) {
        DistanceSubQuery dsq = (DistanceSubQuery) leq;
        String m = dsq.distanceSubQueryNotAllowed();
        if (m != null) {
          return m; 
        }
      } else {
        return "Operator " + getOperatorName() + " does not allow subquery " + leq.toString();
      }
    }
    return null; /* subqueries acceptable */
  }

  
  public void addSpanQueries(SpanNearClauseFactory sncf) throws IOException {
    Query snq = getSpanNearQuery(sncf.getIndexReader(),
                                  sncf.getFieldName(),
                                  getWeight(),
                                  sncf.getBasicQueryFactory());
    sncf.addSpanNearQuery(snq);
  }

  @Override
  public Query makeLuceneQueryFieldNoBoost(final String fieldName, final BasicQueryFactory qf) {
    return new Query () {
      
      @Override
      public String toString(String fn) {
        return getClass().toString() + " " + fieldName + " (" + fn + "?)";
      }
      
      @Override
      public Query rewrite(IndexReader reader) throws IOException {
        return getSpanNearQuery(reader, fieldName, getBoost(), qf);
      }
      
    };
  }
  
  public Query getSpanNearQuery(
          IndexReader reader,
          String fieldName,
          float boost,
          BasicQueryFactory qf) throws IOException {
    SpanQuery[] spanNearClauses = new SpanQuery[getNrSubQueries()];
    Iterator<?> sqi = getSubQueriesIterator();
    int qi = 0;
    while (sqi.hasNext()) {
      SpanNearClauseFactory sncf = new SpanNearClauseFactory(reader, fieldName, qf);
      
      ((DistanceSubQuery)sqi.next()).addSpanQueries(sncf);
      if (sncf.size() == 0) { /* distance operator requires all sub queries */
        while (sqi.hasNext()) { /* produce evt. error messages but ignore results */
          ((DistanceSubQuery)sqi.next()).addSpanQueries(sncf);
          sncf.clear();
        }
        return SrndQuery.theEmptyLcnQuery;
      }
      
      spanNearClauses[qi] = sncf.makeSpanNearClause();

      qi++;
    }
    
    SpanNearQuery r = new SpanNearQuery(spanNearClauses, getOpDistance() - 1, subQueriesOrdered());
    r.setBoost(boost);
    return r;
  }
}

