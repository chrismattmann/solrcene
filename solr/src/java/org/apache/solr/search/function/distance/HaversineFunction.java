package org.apache.solr.search.function.distance;
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

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.spatial.DistanceUtils;
import org.apache.solr.common.SolrException;
import org.apache.solr.search.function.MultiValueSource;
import org.apache.solr.search.function.DocValues;
import org.apache.solr.search.function.ValueSource;

import java.io.IOException;
import java.util.Map;


/**
 * Calculate the Haversine formula (distance) between any two points on a sphere
 * Takes in four value sources: (latA, lonA); (latB, lonB).
 * <p/>
 * Assumes the value sources are in radians unless
 * <p/>
 * See http://en.wikipedia.org/wiki/Great-circle_distance and
 * http://en.wikipedia.org/wiki/Haversine_formula for the actual formula and
 * also http://www.movable-type.co.uk/scripts/latlong.html
 */
public class HaversineFunction extends ValueSource {

  private MultiValueSource p1;
  private MultiValueSource p2;
  private boolean convertToRadians = false;
  private double radius;

  public HaversineFunction(MultiValueSource p1, MultiValueSource p2, double radius) {
    this(p1, p2, radius, false);
  }

  public HaversineFunction(MultiValueSource p1, MultiValueSource p2, double radius, boolean convertToRads){
    this.p1 = p1;
    this.p2 = p2;
    if (p1.dimension() != 2 || p2.dimension() != 2) {
      throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "Illegal dimension for value sources");
    }
    this.radius = radius;
    this.convertToRadians = convertToRads;
  }

  protected String name() {
    return "hsin";
  }

  /**
   * @param doc  The doc to score
   * @param p1DV
   * @param p2DV
   * @return The haversine distance formula
   */
  protected double distance(int doc, DocValues p1DV, DocValues p2DV) {

    double[] p1D = new double[2];
    double[] p2D = new double[2];
    p1DV.doubleVal(doc, p1D);
    p2DV.doubleVal(doc, p2D);
    double x1;
    double y1;
    double x2;
    double y2;
    if (convertToRadians) {
      x1 = p1D[0] * DistanceUtils.DEGREES_TO_RADIANS;
      y1 = p1D[1] * DistanceUtils.DEGREES_TO_RADIANS;
      x2 = p2D[0] * DistanceUtils.DEGREES_TO_RADIANS;
      y2 = p2D[1] * DistanceUtils.DEGREES_TO_RADIANS;
    } else {
      x1 = p1D[0];
      y1 = p1D[1];
      x2 = p2D[0];
      y2 = p2D[1];
    }
    return DistanceUtils.haversine(x1, y1, x2, y2, radius);
  }


  @Override
  public DocValues getValues(Map context, IndexReader reader) throws IOException {
    final DocValues vals1 = p1.getValues(context, reader);

    final DocValues vals2 = p2.getValues(context, reader);
    return new DocValues() {
      public float floatVal(int doc) {
        return (float) doubleVal(doc);
      }

      public int intVal(int doc) {
        return (int) doubleVal(doc);
      }

      public long longVal(int doc) {
        return (long) doubleVal(doc);
      }

      public double doubleVal(int doc) {
        return (double) distance(doc, vals1, vals2);
      }

      public String strVal(int doc) {
        return Double.toString(doubleVal(doc));
      }

      @Override
      public String toString(int doc) {
        StringBuilder sb = new StringBuilder();
        sb.append(name()).append('(');
        sb.append(vals1.toString(doc)).append(',').append(vals2.toString(doc));
        sb.append(')');
        return sb.toString();
      }
    };
  }

  @Override
  public void createWeight(Map context, Searcher searcher) throws IOException {
    p1.createWeight(context, searcher);
    p2.createWeight(context, searcher);

  }

  @Override
  public boolean equals(Object o) {
    if (this.getClass() != o.getClass()) return false;
    HaversineFunction other = (HaversineFunction) o;
    return this.name().equals(other.name())
            && p1.equals(other.p1) &&
            p2.equals(other.p2) && radius == other.radius;
  }

  @Override
  public int hashCode() {
    int result;
    long temp;
    result = p1.hashCode();
    result = 31 * result + p2.hashCode();
    result = 31 * result + name().hashCode();
    temp = Double.doubleToRawLongBits(radius);
    result = 31 * result + (int) (temp ^ (temp >>> 32));
    return result;
  }

  public String description() {
    StringBuilder sb = new StringBuilder();
    sb.append(name()).append('(');
    sb.append(p1).append(',').append(p2);
    sb.append(')');
    return sb.toString();
  }
}
