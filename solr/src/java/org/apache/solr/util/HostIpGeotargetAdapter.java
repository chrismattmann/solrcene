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

package org.apache.solr.util;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.lucene.spatial.geometry.FloatLatLng;
import org.apache.lucene.spatial.geometry.LatLng;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Implementation of GeotargetAdapter using hostip.info web service
 * 
 */
public class HostIpGeotargetAdapter implements GeotargetAdapter {

  public static final String URL = "http://api.hostip.info/";

  @Override
  public LatLng getCurrentLocation(String ip) {

    GMLHandler gmlHandler = new GMLHandler();
    try {
      // connect to webservice
      URL url = new URL(URL + "?ip=" + ip);
      HttpURLConnection urlc = (HttpURLConnection) url.openConnection();
      InputStream stream = urlc.getInputStream();
      SAXParserFactory factory = SAXParserFactory.newInstance();
      SAXParser saxParser;

      // parse. if error, return a null location
      try {
        saxParser = factory.newSAXParser();
        saxParser.parse(stream, gmlHandler);
      } catch (ParserConfigurationException e) {
        e.printStackTrace();
        return null;
      } catch (SAXException e) {
        e.printStackTrace();
        return null;
      }
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }

    return gmlHandler.getLocation();
  }

  /**
   * This class handles the SAX parser callbacks and retrieves the pertinent
   * information parsed by SAX
   * 
   */
  private class GMLHandler extends DefaultHandler {
    private static final String COORDINATES_TAG = "gml:coordinates";
    private boolean coordinates;
    private LatLng location;

    public GMLHandler() {
      coordinates = false;
    }

    @Override
    public void startElement(String uri, String localName, String qName,
        Attributes attributes) throws SAXException {
      if (qName.equalsIgnoreCase(COORDINATES_TAG)) {
        coordinates = true;
      }
    }

    @Override
    public void characters(char ch[], int start, int length)
        throws SAXException {

      if (coordinates) {
        String coord = new String(ch, start, length);
        String[] lng_lat = coord.split(",");
        float lat = Float.parseFloat(lng_lat[1]);
        float lng = Float.parseFloat(lng_lat[0]);

        location = new FloatLatLng(lat, lng);
        coordinates = false;
      }
    }

    /**
     * Returns the location parsed from the web service's response
     * 
     * @return LatLng location in lat/long
     */
    public LatLng getLocation() {
      return location;
    }
  }
}
