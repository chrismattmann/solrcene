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

package org.apache.solr.handler;

import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.update.processor.UpdateRequestProcessor;

/**
 * This class handles the update requests for inserting Geonames data.
 */
public class GeonamesUpdateRequestHandler extends ContentStreamHandlerBase {

	public static final String UPDATE_PROCESSOR = "update.processor";

	@Override
	protected ContentStreamLoader newLoader(SolrQueryRequest req,
			UpdateRequestProcessor processor) {
		return new GeonamesLoader(processor);
	}

	////////////////////////SolrInfoMBeans methods //////////////////////

	  @Override
	  public String getDescription() {
	    return "Add documents with geonames data";
	  }

	  @Override
	  public String getVersion() {
	    return "$Revision: 1 $";
	  }

	  @Override
	  public String getSourceId() {
	    return "";
	  }

	  @Override
	  public String getSource() {
	    return "";
	  }

}
