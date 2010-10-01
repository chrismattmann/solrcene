package org.apache.lucene.analysis.th;

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

import org.apache.lucene.analysis.BaseTokenStreamTestCase;

/**
 * Test case for ThaiAnalyzer, modified from TestFrenchAnalyzer
 *
 * @version   0.1
 */

public class TestThaiAnalyzer extends BaseTokenStreamTestCase {
	
	/* 
	 * testcase for offsets
	 */
	public void testOffsets() throws Exception {
		assertAnalyzesTo(new ThaiAnalyzer(TEST_VERSION_CURRENT), "เดอะนิวยอร์กไทมส์", 
				new String[] { "เด", "อะนิว", "ยอ", "ร์ก", "ไทมส์"},
				new int[] { 0, 2, 7, 9, 12 },
				new int[] { 2, 7, 9, 12, 17});
	}
	
	
	/*
	 * Thai numeric tokens are typed as <ALPHANUM> instead of <NUM>.
	 * This is really a problem with the interaction w/ StandardTokenizer, which is used by ThaiAnalyzer.
	 * 
	 * The issue is this: in StandardTokenizer the entire [:Thai:] block is specified in ALPHANUM (including punctuation, digits, etc)
	 * Fix is easy: refine this spec to exclude thai punctuation and digits.
	 * 
	 * A better fix, that would also fix quite a few other languages would be to remove the thai hack.
	 * Instead, allow the definition of alphanum to include relevant categories like nonspacing marks!
	 */
	public void testBuggyTokenType() throws Exception {
		assertAnalyzesTo(new ThaiAnalyzer(TEST_VERSION_CURRENT), "เดอะนิวยอร์กไทมส์ ๑๒๓", 
				new String[] { "เด", "อะนิว", "ยอ", "ร์ก", "ไทมส์", "๑๒๓" },
				new String[] { "<ALPHANUM>", "<ALPHANUM>", "<ALPHANUM>", "<ALPHANUM>", "<ALPHANUM>", "<ALPHANUM>" });
	}
	
	/* correct testcase
	public void testTokenType() throws Exception {
		assertAnalyzesTo(new ThaiAnalyzer(), "เดอะนิวยอร์กไทมส์ ๑๒๓", 
				new String[] { "เด", "อะนิว", "ยอ", "ร์ก", "ไทมส์", "๑๒๓" },
				new String[] { "<ALPHANUM>", "<ALPHANUM>", "<ALPHANUM>", "<ALPHANUM>", "<ALPHANUM>", "<NUM>" });
	}
	*/

	public void testAnalyzer() throws Exception {
		ThaiAnalyzer analyzer = new ThaiAnalyzer(TEST_VERSION_CURRENT);
	
		assertAnalyzesTo(analyzer, "", new String[] {});

		assertAnalyzesTo(
			analyzer,
			"การที่ได้ต้องแสดงว่างานดี",
			new String[] { "การ", "ที่", "ได้", "ต้อง", "แสดง", "ว่า", "งาน", "ดี"});

		assertAnalyzesTo(
			analyzer,
			"บริษัทชื่อ XY&Z - คุยกับ xyz@demo.com",
			new String[] { "บริษัท", "ชื่อ", "xy&z", "คุย", "กับ", "xyz@demo.com" });

    // English stop words
		assertAnalyzesTo(
			analyzer,
			"ประโยคว่า The quick brown fox jumped over the lazy dogs",
			new String[] { "ประโยค", "ว่า", "quick", "brown", "fox", "jumped", "over", "lazy", "dogs" });
	}
	
	/*
	 * Test that position increments are adjusted correctly for stopwords.
	 */
	public void testPositionIncrements() throws Exception {
	  ThaiAnalyzer analyzer = new ThaiAnalyzer(TEST_VERSION_CURRENT);

	  assertAnalyzesTo(analyzer, "ประโยคว่า the ประโยคว่า",
	          new String[] { "ประโยค", "ว่า", "ประโยค", "ว่า" },
	          new int[] { 0, 6, 14, 20 },
	          new int[] { 6, 9, 20, 23 },
	          new int[] { 1, 1, 2, 1 });
	 
	  // case that a stopword is adjacent to thai text, with no whitespace
	  assertAnalyzesTo(analyzer, "ประโยคว่าtheประโยคว่า",
	      new String[] { "ประโยค", "ว่า", "ประโยค", "ว่า" },
	      new int[] { 0, 6, 12, 18 },
	      new int[] { 6, 9, 18, 21 },
	      new int[] { 1, 1, 2, 1 });
	}
	
	public void testReusableTokenStream() throws Exception {
	  ThaiAnalyzer analyzer = new ThaiAnalyzer(TEST_VERSION_CURRENT);
	  assertAnalyzesToReuse(analyzer, "", new String[] {});

      assertAnalyzesToReuse(
          analyzer,
          "การที่ได้ต้องแสดงว่างานดี",
          new String[] { "การ", "ที่", "ได้", "ต้อง", "แสดง", "ว่า", "งาน", "ดี"});

      assertAnalyzesToReuse(
          analyzer,
          "บริษัทชื่อ XY&Z - คุยกับ xyz@demo.com",
          new String[] { "บริษัท", "ชื่อ", "xy&z", "คุย", "กับ", "xyz@demo.com" });
	}
}
