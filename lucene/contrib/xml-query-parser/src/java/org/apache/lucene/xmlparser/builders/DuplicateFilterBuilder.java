/*
 * Created on 25-Jan-2006
 */
package org.apache.lucene.xmlparser.builders;

import org.apache.lucene.search.DuplicateFilter;
import org.apache.lucene.search.Filter;
import org.apache.lucene.xmlparser.DOMUtils;
import org.apache.lucene.xmlparser.FilterBuilder;
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
public class DuplicateFilterBuilder implements FilterBuilder {
	

	public Filter getFilter(Element e) throws ParserException {
        String fieldName=DOMUtils.getAttributeWithInheritanceOrFail(e,"fieldName");
		DuplicateFilter df=new DuplicateFilter(fieldName);
		String keepMode=DOMUtils.getAttribute(e,"keepMode","first");
		if(keepMode.equalsIgnoreCase("first"))
		{
			df.setKeepMode(DuplicateFilter.KM_USE_FIRST_OCCURRENCE);
		}
		else
			if(keepMode.equalsIgnoreCase("last"))
			{
				df.setKeepMode(DuplicateFilter.KM_USE_LAST_OCCURRENCE);
			}
			else
			{
				throw new ParserException("Illegal keepMode attribute in DuplicateFilter:"+keepMode);
			}
		String processingMode=DOMUtils.getAttribute(e,"processingMode","full");
		if(processingMode.equalsIgnoreCase("full"))
		{
			df.setProcessingMode(DuplicateFilter.PM_FULL_VALIDATION);
		}
		else
			if(processingMode.equalsIgnoreCase("fast"))
			{
				df.setProcessingMode(DuplicateFilter.PM_FAST_INVALIDATION);
			}
			else
			{
				throw new ParserException("Illegal processingMode attribute in DuplicateFilter:"+processingMode);
			}
					
		return df;
	}

}
