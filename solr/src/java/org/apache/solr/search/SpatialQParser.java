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

import java.util.ArrayList;
import javax.servlet.http.HttpServletRequest;

import org.apache.lucene.document.Document;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.spatial.geometry.LatLng;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.params.SpatialParams;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.schema.FieldType;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.util.GeotargetAdapter;

/**
 * This class parses and executes a spatial query
 */
public class SpatialQParser extends QParser {

  private static final String NAME_FIELD = "asciiname";
  private static final String STATE_FIELD = "admin1code";
  private static final String COUNTRY_FIELD = "countrycode";
  private static final String LOCATION_FIELD = "location";
  private static final String POPULATION_FIELD = "population";

  public static final String SEARCH_RADIUS = "20";
  public static final String CITY = "ct";
  public static final String STATE = "s";
  public static final String COUNTRY = "c";
  public static final String DISTANCE = "d";

  private GeotargetAdapter geoTargeter;

  /**
   * Constructor
   * 
   * @param qstr
   *          the query string
   * @param localParams
   *          solr localParams
   * @param params
   *          other params
   * @param req
   *          the query request object
   * @param geoTargeter
   *          the adapter to use to geotarget client ip if no location is
   *          provided
   */
  public SpatialQParser(String qstr, SolrParams localParams, SolrParams params,
      SolrQueryRequest req, GeotargetAdapter geoTargeter) {
    super(qstr, localParams, params, req);
    this.geoTargeter = geoTargeter;
  }

  @Override
  public Query parse() throws ParseException {
    // this will combine the results that are found for each
    // administrative level
    BooleanQuery allQuery = new BooleanQuery();

    // attempt to create a query on city (low level administrative division)
    String city = localParams.get(CITY);
    if (city != null) {
      city = city.toLowerCase();
      SchemaField nameField = req.getSchema().getField(NAME_FIELD);
      FieldType nameFieldType = nameField.getType();
      Query cityQuery = nameFieldType.getFieldQuery(this, nameField, city);
      allQuery.add(cityQuery, Occur.MUST);
    }

    // attempt to create a query on state (mid level administrative division)
    String state = localParams.get(STATE);
    if (state != null) {
      state = state.toLowerCase();
      SchemaField stateField = req.getSchema().getField(STATE_FIELD);
      FieldType stateFieldType = stateField.getType();
      Query stateQuery = stateFieldType.getFieldQuery(this, stateField, state);
      allQuery.add(stateQuery, Occur.MUST);
    }

    // attempt to create a query on city (high level administrative division)
    String country = localParams.get(COUNTRY);
    if (country != null) {
      country = country.toLowerCase();
      SchemaField countryField = req.getSchema().getField(COUNTRY_FIELD);
      FieldType countryFieldType = countryField.getType();
      Query countryQuery = countryFieldType.getFieldQuery(this, countryField,
          country);
      allQuery.add(countryQuery, Occur.MUST);
    }

    String latitude = null;
    String longitude = null;

    // no location provided, computer user's location via reverse-ip lookup
    if (allQuery.getClauses().length == 0) {
      HttpServletRequest httpreq = req.getHttpServletRequest();
      String ip = httpreq.getRemoteAddr();

      LatLng currLoc = geoTargeter.getCurrentLocation(ip);

      if (currLoc != null) {
        latitude = Double.toString(currLoc.getLat());
        longitude = Double.toString(currLoc.getLng());
      }
    } else {
      SolrIndexSearcher searcher = req.getSearcher();
      Document geocodeDoc = null;

      try {
        Sort s = new Sort(new SortField(POPULATION_FIELD, SortField.LONG, true));
        DocList docs = searcher.getDocList(allQuery, new ArrayList<Query>(), s,
            0, 1, 0);

        if (docs == null)
          return query;

        DocIterator iter = docs.iterator();
        int geocodeDocId = iter.nextDoc();
        geocodeDoc = searcher.doc(geocodeDocId);
      } catch (Exception e) {
        e.printStackTrace();
        return query;
      }
      latitude = geocodeDoc.get("latitude");
      longitude = geocodeDoc.get("longitude");
    }

    // combine the spatial and free-text queries
    BooleanQuery finalQuery = new BooleanQuery();

    // if no location is provided and user's location cannot be determined,
    // do not search location
    if (latitude != null && longitude != null) {
      String distance = localParams.get(DISTANCE);

      try {
        Double.parseDouble(distance);
      } catch (Exception e) {
        distance = SEARCH_RADIUS;
      }

      SpatialFilterQParserPlugin spatialFilter = new SpatialFilterQParserPlugin();
      ModifiableSolrParams spatialParams = new ModifiableSolrParams();
      spatialParams.add(SpatialParams.POINT, latitude + "," + longitude);
      spatialParams.add(SpatialParams.DISTANCE, distance);
      spatialParams.add(CommonParams.FL, LOCATION_FIELD);
      Query spatialQuery = spatialFilter.createParser(qstr, spatialParams,
          spatialParams, req).parse();
      finalQuery.add(spatialQuery, Occur.MUST);
    }

    // get results from default LuceneQParser
    Query defQuery = new LuceneQParserPlugin().createParser(qstr, localParams,
        params, req).parse();

    finalQuery.add(defQuery, Occur.MUST);

    return finalQuery;
  }
}
