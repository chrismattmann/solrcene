package org.apache.lucene.index;

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

import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.store.IndexInput;

import java.io.IOException;

public class TestIndexInput extends LuceneTestCase {
  public void testRead() throws IOException {
    IndexInput is = new MockIndexInput(new byte[] { 
      (byte) 0x80, 0x01,
      (byte) 0xFF, 0x7F,
      (byte) 0x80, (byte) 0x80, 0x01,
      (byte) 0x81, (byte) 0x80, 0x01,
      0x06, 'L', 'u', 'c', 'e', 'n', 'e',

      // 2-byte UTF-8 (U+00BF "INVERTED QUESTION MARK") 
      0x02, (byte) 0xC2, (byte) 0xBF,
      0x0A, 'L', 'u', (byte) 0xC2, (byte) 0xBF, 
            'c', 'e', (byte) 0xC2, (byte) 0xBF, 
            'n', 'e',

      // 3-byte UTF-8 (U+2620 "SKULL AND CROSSBONES") 
      0x03, (byte) 0xE2, (byte) 0x98, (byte) 0xA0,
      0x0C, 'L', 'u', (byte) 0xE2, (byte) 0x98, (byte) 0xA0,
            'c', 'e', (byte) 0xE2, (byte) 0x98, (byte) 0xA0,
            'n', 'e',

      // surrogate pairs
      // (U+1D11E "MUSICAL SYMBOL G CLEF")
      // (U+1D160 "MUSICAL SYMBOL EIGHTH NOTE")
      0x04, (byte) 0xF0, (byte) 0x9D, (byte) 0x84, (byte) 0x9E,
      0x08, (byte) 0xF0, (byte) 0x9D, (byte) 0x84, (byte) 0x9E, 
            (byte) 0xF0, (byte) 0x9D, (byte) 0x85, (byte) 0xA0, 
      0x0E, 'L', 'u',
            (byte) 0xF0, (byte) 0x9D, (byte) 0x84, (byte) 0x9E,
            'c', 'e', 
            (byte) 0xF0, (byte) 0x9D, (byte) 0x85, (byte) 0xA0, 
            'n', 'e',  

      // null bytes
      0x01, 0x00,
      0x08, 'L', 'u', 0x00, 'c', 'e', 0x00, 'n', 'e',
    });
        
    assertEquals(128,is.readVInt());
    assertEquals(16383,is.readVInt());
    assertEquals(16384,is.readVInt());
    assertEquals(16385,is.readVInt());
    assertEquals("Lucene",is.readString());

    assertEquals("\u00BF",is.readString());
    assertEquals("Lu\u00BFce\u00BFne",is.readString());

    assertEquals("\u2620",is.readString());
    assertEquals("Lu\u2620ce\u2620ne",is.readString());

    assertEquals("\uD834\uDD1E",is.readString());
    assertEquals("\uD834\uDD1E\uD834\uDD60",is.readString());
    assertEquals("Lu\uD834\uDD1Ece\uD834\uDD60ne",is.readString());
    
    assertEquals("\u0000",is.readString());
    assertEquals("Lu\u0000ce\u0000ne",is.readString());
  }
}
