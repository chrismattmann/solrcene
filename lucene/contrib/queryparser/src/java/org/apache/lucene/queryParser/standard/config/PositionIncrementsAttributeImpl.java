package org.apache.lucene.queryParser.standard.config;

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

import org.apache.lucene.queryParser.core.config.QueryConfigHandler;
import org.apache.lucene.queryParser.standard.processors.AnalyzerQueryNodeProcessor;
import org.apache.lucene.util.AttributeImpl;

/**
 * This attribute is used by {@link AnalyzerQueryNodeProcessor} processor and
 * must be defined in the {@link QueryConfigHandler}. This attribute tells the
 * processor if the position increment is enabled. <br/>
 * 
 * @see org.apache.lucene.queryParser.standard.config.PositionIncrementsAttribute
 */
public class PositionIncrementsAttributeImpl extends AttributeImpl
				implements PositionIncrementsAttribute {

  private static final long serialVersionUID = -2804763012793049527L;

  private boolean positionIncrementsEnabled = false;

  public PositionIncrementsAttributeImpl() {
	  positionIncrementsEnabled = false; //default in 2.4
  }

  public void setPositionIncrementsEnabled(boolean positionIncrementsEnabled) {
    this.positionIncrementsEnabled = positionIncrementsEnabled;
  }

  public boolean isPositionIncrementsEnabled() {
    return this.positionIncrementsEnabled;
  }

  @Override
  public void clear() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void copyTo(AttributeImpl target) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean equals(Object other) {

    if (other instanceof PositionIncrementsAttributeImpl
        && ((PositionIncrementsAttributeImpl) other).positionIncrementsEnabled == this.positionIncrementsEnabled) {

      return true;

    }

    return false;

  }

  @Override
  public int hashCode() {
    return this.positionIncrementsEnabled ? -1 : Integer.MAX_VALUE;
  }

  @Override
  public String toString() {
    return "<positionIncrements positionIncrementsEnabled="
        + this.positionIncrementsEnabled + "/>";
  }

}
