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

package org.apache.solr.search;

import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.util.GeotargetAdapter;

/**
 * This class returns a QParser that will parse and execute a spatial query
 * 
 */
public class SpatialQParserPlugin extends QParserPlugin {

  private GeotargetAdapter geoTargeter;

  @Override
  public QParser createParser(String qstr, SolrParams localParams,
      SolrParams params, SolrQueryRequest req) {
    SpatialQParser parser = new SpatialQParser(qstr, localParams, params, req,
        geoTargeter);
    return parser;
  }

  @Override
  public void init(NamedList args) {
    final SolrParams p = SolrParams.toSolrParams(args);
    String geocodeIpAdapterClass = p.get("GeotargetAdapter");

    // create adapter object via reflection
    try {
      Class<?> adapterClass = Class.forName(geocodeIpAdapterClass);
      geoTargeter = (GeotargetAdapter) adapterClass.newInstance();
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    } catch (InstantiationException e) {
      e.printStackTrace();
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    }
  }

}
