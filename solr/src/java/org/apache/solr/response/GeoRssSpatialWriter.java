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

package org.apache.solr.response;

import java.io.IOException;
import java.io.Writer;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.request.SolrQueryRequest;

/**
 * Implementation of a writer that outputs GeoRSS.
 */
public class GeoRssSpatialWriter extends BaseResponseWriter implements
    QueryResponseWriter {

  // content type and encoding
  private static String CONTENT_TYPE_GEORSS_UTF8 = "text/xml; charset=UTF-8";

  /*
   * (non-Javadoc)
   * 
   * @see
   * org.apache.solr.response.QueryResponseWriter#getContentType(org.apache.
   * solr.request.SolrQueryRequest, org.apache.solr.response.SolrQueryResponse)
   */
  @Override
  public String getContentType(SolrQueryRequest request,
      SolrQueryResponse response) {
    return CONTENT_TYPE_GEORSS_UTF8;
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.apache.solr.response.QueryResponseWriter#write(java.io.Writer,
   * org.apache.solr.request.SolrQueryRequest,
   * org.apache.solr.response.SolrQueryResponse)
   */
  @Override
  public void write(Writer writer, SolrQueryRequest request,
      SolrQueryResponse response) throws IOException {
    this.write(new GeoRssSingleResponseWriter(writer), request, response);
  }

  class GeoRssSingleResponseWriter extends
      BaseResponseWriter.SingleResponseWriter {

    private Writer writer;

    public GeoRssSingleResponseWriter(Writer writer) {
      this.writer = writer;
    }

    /*
     * (non-Javadoc)
     * 
     * @seeorg.apache.solr.response.BaseResponseWriter.SingleResponseWriter#
     * isStreamingDocs()
     */
    @Override
    public boolean isStreamingDocs() {
      return true;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.solr.response.BaseResponseWriter.SingleResponseWriter#writeDoc
     * (org.apache.solr.common.SolrDocument)
     */
    @Override
    public void writeDoc(SolrDocument solrDocument) throws IOException {
      StringBuilder geoRssBody = new StringBuilder();

      geoRssBody.append(createElement("item", 2, false, true));

      String title = getTitle(solrDocument);

      if (title != null) {
        geoRssBody.append(createElement("title", 3, false, false));
        geoRssBody.append(title);
        geoRssBody.append(createElement("title", 0, true, true));
      }

      String description = getDescription(solrDocument);

      if (description != null) {
        geoRssBody.append(createElement("description", 3, false, false));
        geoRssBody.append(description);
        geoRssBody.append(createElement("description", 0, true, true));
      }

      String link = getLink(solrDocument);

      if (link != null) {
        geoRssBody.append(createElement("link", 3, false, false));
        geoRssBody.append(link);
        geoRssBody.append(createElement("link", 0, true, true));
      }

      String lat = getLatitude(solrDocument);

      if (lat != null) {
        geoRssBody.append(createElement("geo:lat", 3, false, false));
        geoRssBody.append(lat);
        geoRssBody.append(createElement("geo:lat", 0, true, true));
      }

      String lon = getLongitude(solrDocument);

      if (lon != null) {
        geoRssBody.append(createElement("geo:long", 3, false, false));
        geoRssBody.append(lon);
        geoRssBody.append(createElement("geo:long", 0, true, true));
      }

      geoRssBody.append(createElement("item", 2, true, true));
      this.writer.write(geoRssBody.toString());
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.solr.response.BaseResponseWriter.SingleResponseWriter#end()
     */
    @Override
    public void end() throws IOException {
      StringBuilder geoRssFooter = new StringBuilder();
      geoRssFooter.append(createElement("channel", 1, true, true));
      geoRssFooter.append(createElement("rss", 0, true, false));
      this.writer.write(geoRssFooter.toString());
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.solr.response.BaseResponseWriter.SingleResponseWriter#start()
     */
    @Override
    public void start() throws IOException {
      StringBuilder geoRssHeader = new StringBuilder();
      geoRssHeader.append("<?xml version='1.0'?>\n");
      geoRssHeader.append(createElement("rss", 0, false, true, "version='2.0'",
          "xmlns:geo='http://www.w3.org/2003/01/geo/wgs84_pos#'"));
      geoRssHeader.append(createElement("channel", 1, false, true));
      geoRssHeader.append(createElement("title", 2, false, false));
      geoRssHeader.append("Solr Search Results");
      geoRssHeader.append(createElement("title", 0, true, true));
      geoRssHeader.append(createElement("description", 2, false, false));
      geoRssHeader.append("GeoRSS Formatted Search Results");
      geoRssHeader.append(createElement("description", 0, true, true));
      this.writer.write(geoRssHeader.toString());
    }

    private String createElement(String tagName, int tabIndents, boolean isEnd,
        boolean newLine, String... attributes) {
      StringBuilder element = new StringBuilder();
      for (int i = 0; i < tabIndents; i++) {
        element.append("\t");
      }

      element.append("<");

      if (!isEnd) {
        element.append(tagName + " ");

        for (String attr : attributes) {
          element.append(attr + " ");
        }

        element.deleteCharAt(element.length() - 1);
      } else
        element.append("/" + tagName);

      element.append(">");

      if (newLine)
        element.append("\n");

      return element.toString();
    }

    private String getDescription(SolrDocument doc) {
      String description = (String) doc.getFirstValue("features");
      return description != null ? StringEscapeUtils.escapeXml(description)
          : description;
    }

    private String getLatitude(SolrDocument doc) {
      String location = (String) doc.getFirstValue("location");
      if(location == null) return "";
      String[] loc = location.split(",");
      return loc[0] != null ? StringEscapeUtils.escapeXml(loc[0]) : loc[0];
    }

    private String getLink(SolrDocument doc) {
      String link = (String) doc.getFirstValue("link");
      return link != null ? StringEscapeUtils.escapeXml(link) : link;
    }

    private String getLongitude(SolrDocument doc) {
      String location = (String) doc.getFirstValue("location");
      if(location == null) return "";
      String[] loc = location.split(",");
      return loc[1] != null ? StringEscapeUtils.escapeXml(loc[1]) : loc[1];
    }

    private String getTitle(SolrDocument doc) {
      String title = (String) doc.getFirstValue("name");
      return title != null ? StringEscapeUtils.escapeXml(title) : title;
    }

  }

}
