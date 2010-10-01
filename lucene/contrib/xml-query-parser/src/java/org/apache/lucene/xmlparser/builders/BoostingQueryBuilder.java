package org.apache.lucene.xmlparser.builders;

import org.apache.lucene.search.BoostingQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.xmlparser.DOMUtils;
import org.apache.lucene.xmlparser.ParserException;
import org.apache.lucene.xmlparser.QueryBuilder;
import org.w3c.dom.Element;
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

/**
 * 
 */
public class BoostingQueryBuilder implements QueryBuilder
{
	
	private QueryBuilder factory;
	float defaultBoost=0.01f;

	public BoostingQueryBuilder (QueryBuilder factory)
	{
		this.factory=factory;
	}

	public Query getQuery(Element e) throws ParserException
	{
		
        Element mainQueryElem=DOMUtils.getChildByTagOrFail(e,"Query");
 		mainQueryElem=DOMUtils.getFirstChildOrFail(mainQueryElem);
  		Query mainQuery=factory.getQuery(mainQueryElem);

 		Element boostQueryElem=DOMUtils.getChildByTagOrFail(e,"BoostQuery");
  		float boost=DOMUtils.getAttribute(boostQueryElem,"boost",defaultBoost);
 		boostQueryElem=DOMUtils.getFirstChildOrFail(boostQueryElem);
  		Query boostQuery=factory.getQuery(boostQueryElem);
  		
  		BoostingQuery bq = new BoostingQuery(mainQuery,boostQuery,boost);

  		bq.setBoost(DOMUtils.getAttribute(e,"boost",1.0f));
		return bq;

	}


}
