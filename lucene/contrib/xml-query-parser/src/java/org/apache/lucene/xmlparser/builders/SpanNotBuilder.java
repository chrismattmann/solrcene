package org.apache.lucene.xmlparser.builders;

import org.apache.lucene.search.spans.SpanNotQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.xmlparser.DOMUtils;
import org.apache.lucene.xmlparser.ParserException;
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
public class SpanNotBuilder extends SpanBuilderBase
{
    
    SpanQueryBuilder factory;    

    /**
     * @param factory
     */
    public SpanNotBuilder(SpanQueryBuilder factory)
    {
        super();
        this.factory = factory;
    }
	public SpanQuery getSpanQuery(Element e) throws ParserException
	{
  	    Element includeElem=DOMUtils.getChildByTagOrFail(e,"Include");
        includeElem=DOMUtils.getFirstChildOrFail(includeElem);

  	    Element excludeElem=DOMUtils.getChildByTagOrFail(e,"Exclude");
        excludeElem=DOMUtils.getFirstChildOrFail(excludeElem);

  	    SpanQuery include=factory.getSpanQuery(includeElem);
  	    SpanQuery exclude=factory.getSpanQuery(excludeElem);
	    
		SpanNotQuery snq = new SpanNotQuery(include,exclude);
		
		snq.setBoost(DOMUtils.getAttribute(e,"boost",1.0f));
		return snq;
	}

}
