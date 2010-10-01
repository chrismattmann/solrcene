package org.apache.lucene.util;

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

import java.util.Random;

/*
 * Some of this code came from the excellent Unicode
 * conversion examples from:
 *
 *   http://www.unicode.org/Public/PROGRAMS/CVTUTF
 *
 * Full Copyright for that code follows:
*/

/*
 * Copyright 2001-2004 Unicode, Inc.
 * 
 * Disclaimer
 * 
 * This source code is provided as is by Unicode, Inc. No claims are
 * made as to fitness for any particular purpose. No warranties of any
 * kind are expressed or implied. The recipient agrees to determine
 * applicability of information provided. If this file has been
 * purchased on magnetic or optical media from Unicode, Inc., the
 * sole remedy for any claim will be exchange of defective media
 * within 90 days of receipt.
 * 
 * Limitations on Rights to Redistribute This Code
 * 
 * Unicode, Inc. hereby grants the right to freely use the information
 * supplied in this file in the creation of products supporting the
 * Unicode Standard, and to make copies of this file in any form
 * for internal or external distribution as long as this notice
 * remains attached.
 */

/*
 * Additional code came from the IBM ICU library.
 *
 *  http://www.icu-project.org
 *
 * Full Copyright for that code follows.
 */

/*
 * Copyright (C) 1999-2010, International Business Machines
 * Corporation and others.  All Rights Reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * provided that the above copyright notice(s) and this permission notice appear
 * in all copies of the Software and that both the above copyright notice(s) and
 * this permission notice appear in supporting documentation.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT OF THIRD PARTY RIGHTS.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR HOLDERS INCLUDED IN THIS NOTICE BE
 * LIABLE FOR ANY CLAIM, OR ANY SPECIAL INDIRECT OR CONSEQUENTIAL DAMAGES, OR
 * ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER
 * IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT
 * OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 *
 * Except as contained in this notice, the name of a copyright holder shall not
 * be used in advertising or otherwise to promote the sale, use or other
 * dealings in this Software without prior written authorization of the
 * copyright holder.
 */

public class TestUnicodeUtil extends LuceneTestCase {
  public void testNextValidUTF16String() {
    // valid UTF-16
    assertEquals("dogs", UnicodeUtil.nextValidUTF16String("dogs"));
    assertEquals("dogs\uD802\uDC02", UnicodeUtil
        .nextValidUTF16String("dogs\uD802\uDC02"));
    
    // an illegal combination, where we have not yet enumerated into the supp
    // plane so we increment to H + \uDC00 (the lowest possible trail surrogate)
    assertEquals("dogs\uD801\uDC00", UnicodeUtil
        .nextValidUTF16String("dogs\uD801"));
    assertEquals("dogs\uD801\uDC00", UnicodeUtil
        .nextValidUTF16String("dogs\uD801b"));
    assertEquals("dogs\uD801\uDC00", UnicodeUtil
        .nextValidUTF16String("dogs\uD801\uD800"));
    
    // an illegal combination where we have already enumerated the trail
    // we must increment the lead and start the trail back at the beginning.
    assertEquals("dogs\uD802\uDC00", UnicodeUtil
        .nextValidUTF16String("dogs\uD801\uE001"));
    
    // an illegal combination where we have exhausted the supp plane
    // we must now move to the lower bmp.
    assertEquals("dogs\uE000", UnicodeUtil
        .nextValidUTF16String("dogs\uDBFF\uE001"));

    // an unpaired trail surrogate. this is invalid when not preceded by a lead
    // surrogate. in this case we have to bump to \uE000 (the lowest possible
    // "upper BMP")
    assertEquals("dogs\uE000", UnicodeUtil.nextValidUTF16String("dogs\uDC00"));
    assertEquals("\uE000", UnicodeUtil.nextValidUTF16String("\uDC00dogs"));
  }

  public void testCodePointCount() {
    final Random r = newRandom();
    BytesRef utf8 = new BytesRef(20);
    int num = 50000 * RANDOM_MULTIPLIER;
    for (int i = 0; i < num; i++) {
      final String s = _TestUtil.randomUnicodeString(r);
      UnicodeUtil.UTF16toUTF8(s, 0, s.length(), utf8);
      assertEquals(s.codePointCount(0, s.length()),
                   UnicodeUtil.codePointCount(utf8));
    }
  }

  public void testUTF8toUTF32() {
    final Random r = newRandom();
    BytesRef utf8 = new BytesRef(20);
    IntsRef utf32 = new IntsRef(20);
    int[] codePoints = new int[20];
    int num = 50000 * RANDOM_MULTIPLIER;
    for (int i = 0; i < num; i++) {
      final String s = _TestUtil.randomUnicodeString(r);
      UnicodeUtil.UTF16toUTF8(s, 0, s.length(), utf8);
      UnicodeUtil.UTF8toUTF32(utf8, utf32);
      
      int charUpto = 0;
      int intUpto = 0;
      while(charUpto < s.length()) {
        final int cp = s.codePointAt(charUpto);
        codePoints[intUpto++] = cp;
        charUpto += Character.charCount(cp);
      }
      if (!ArrayUtil.equals(codePoints, 0, utf32.ints, utf32.offset, intUpto)) {
        System.out.println("FAILED");
        for(int j=0;j<s.length();j++) {
          System.out.println("  char[" + j + "]=" + Integer.toHexString(s.charAt(j)));
        }
        System.out.println();
        assertEquals(intUpto, utf32.length);
        for(int j=0;j<intUpto;j++) {
          System.out.println("  " + Integer.toHexString(utf32.ints[j]) + " vs " + Integer.toHexString(codePoints[j]));
        }
        fail("mismatch");
      }
    }
  }

  public void testNewString() {
    final int[] codePoints = {
        Character.toCodePoint(Character.MIN_HIGH_SURROGATE,
            Character.MAX_LOW_SURROGATE),
        Character.toCodePoint(Character.MAX_HIGH_SURROGATE,
            Character.MIN_LOW_SURROGATE), Character.MAX_HIGH_SURROGATE, 'A',
        -1,};

    final String cpString = "" + Character.MIN_HIGH_SURROGATE
        + Character.MAX_LOW_SURROGATE + Character.MAX_HIGH_SURROGATE
        + Character.MIN_LOW_SURROGATE + Character.MAX_HIGH_SURROGATE + 'A';

    final int[][] tests = { {0, 1, 0, 2}, {0, 2, 0, 4}, {1, 1, 2, 2},
        {1, 2, 2, 3}, {1, 3, 2, 4}, {2, 2, 4, 2}, {2, 3, 0, -1}, {4, 5, 0, -1},
        {3, -1, 0, -1}};

    for (int i = 0; i < tests.length; ++i) {
      int[] t = tests[i];
      int s = t[0];
      int c = t[1];
      int rs = t[2];
      int rc = t[3];

      Exception e = null;
      try {
        String str = UnicodeUtil.newString(codePoints, s, c);
        assertFalse(rc == -1);
        assertEquals(cpString.substring(rs, rs + rc), str);
        continue;
      } catch (IndexOutOfBoundsException e1) {
        e = e1;
      } catch (IllegalArgumentException e2) {
        e = e2;
      }
      assertTrue(rc == -1);
    }
  }
}
