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
package org.apache.solr.search.function;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Searcher;

import java.io.IOException;
import java.util.Map;

public class MaxDocValueSource extends ValueSource {
  public String name() {
    return "maxdoc";
  }

  @Override
  public String description() {
    return name() + "()";
  }

  @Override
  public void createWeight(Map context, Searcher searcher) throws IOException {
    context.put("searcher",searcher);
  }

  @Override
  public DocValues getValues(Map context, IndexReader reader) throws IOException {
    Searcher searcher = (Searcher)context.get("searcher");
    return new ConstIntDocValues(searcher.maxDoc(), this);
  }

  @Override
  public boolean equals(Object o) {
    return this.getClass() == o.getClass();
  }

  @Override
  public int hashCode() {
    return this.getClass().hashCode();
  }
}
