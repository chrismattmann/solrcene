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

import java.io.BufferedReader;
import java.util.Hashtable;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.ContentStream;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.update.AddUpdateCommand;
import org.apache.solr.update.CommitUpdateCommand;
import org.apache.solr.update.processor.UpdateRequestProcessor;

/**
 * This class parses geo data from geonames.org's data dump and adds
 * it to the Solr index
 */
public class GeonamesLoader extends ContentStreamLoader {

	/* 
	 * Field names corresponding to geonames.org's data. These fields must be added to
	 * Solr's schema.xml before indexing will work properly.
	 */
	private static final String[] FIELD_NAMES = { "id", "geoname", "asciiname",
		"alternatenames", "latitude", "longitude", "featureclass",
		"featurecode", "countrycode", "cc2", "admin1code", "admin2code",
		"admin3code", "admin4code", "population", "elevation", "gtopo30",
		"timezone", "modificationdate" };
	
	/*
	 * Default values for geonames data. These values are used if an invalid or
	 * non-existent value is provided during indexing.
	 */
	private static final Hashtable<String, String> DEFAULT_VALUES = new Hashtable<String, String>();
	static {
		DEFAULT_VALUES.put(FIELD_NAMES[0], "-1");
		DEFAULT_VALUES.put(FIELD_NAMES[1], "NULL");
		DEFAULT_VALUES.put(FIELD_NAMES[2], "NULL");
		DEFAULT_VALUES.put(FIELD_NAMES[3], "NULL");
		DEFAULT_VALUES.put(FIELD_NAMES[4], "0");
		DEFAULT_VALUES.put(FIELD_NAMES[5], "0");
		DEFAULT_VALUES.put(FIELD_NAMES[6], "S");
		DEFAULT_VALUES.put(FIELD_NAMES[7], "ll");
		DEFAULT_VALUES.put(FIELD_NAMES[8], "NULL");
		DEFAULT_VALUES.put(FIELD_NAMES[9], "NULL");
		DEFAULT_VALUES.put(FIELD_NAMES[10], "00");
		DEFAULT_VALUES.put(FIELD_NAMES[11], "00");
		DEFAULT_VALUES.put(FIELD_NAMES[12], "00");
		DEFAULT_VALUES.put(FIELD_NAMES[13], "00");
		DEFAULT_VALUES.put(FIELD_NAMES[14], "0");
		DEFAULT_VALUES.put(FIELD_NAMES[15], "0");
		DEFAULT_VALUES.put(FIELD_NAMES[16], "0");
		DEFAULT_VALUES.put(FIELD_NAMES[17], "GMT");
		DEFAULT_VALUES.put(FIELD_NAMES[18], "1970-01-01");
	}
	
	protected UpdateRequestProcessor processor;
	public static final String COMMIT_COMMAND = "commit";
	
	/**
	 * Constructor
	 * @param processor the UpdateRequestProcessor to send the update command to
	 */
	public GeonamesLoader(UpdateRequestProcessor processor) {
		this.processor = processor;
	}
	
	@Override
	public void load(SolrQueryRequest req, SolrQueryResponse rsp,
			ContentStream stream) throws Exception {
		SolrInputDocument doc = new SolrInputDocument();
		BufferedReader br = new BufferedReader(stream.getReader());
		
		String line = br.readLine();
		
		//geonames data dump is tab-delimited and each line represents a record
		//iterate through the lines and split columns by tab (\t)
		while(line != null)
		{
			//if the request is to commit, issue the commit command and return
			if(line.equalsIgnoreCase(COMMIT_COMMAND))
			{
				CommitUpdateCommand cmd = new CommitUpdateCommand( true );
				processor.processCommit( cmd );
				return;
			}
			
			//otherwise, parse the line and create a SolrInputDocument from the
			//data
			String[] tokens = line.split("\t");
			for(int i = 0; i < tokens.length; i++)
			{
				String value = "";
				tokens[i] = tokens[i].trim();
				if (tokens[i] == null || tokens[i].equalsIgnoreCase(""))
					value = DEFAULT_VALUES.get(FIELD_NAMES[i]);
				else
					value = StringEscapeUtils.escapeXml(tokens[i]);
				
				doc.setField(FIELD_NAMES[i], value.toLowerCase());
			}
			
			//issue add command to processor
			AddUpdateCommand addCmd = new AddUpdateCommand();
			addCmd.solrDoc = doc;
			processor.processAdd(addCmd);
			
			//read next line
			line = br.readLine();
		}
	}

}
