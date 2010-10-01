package org.apache.lucene.util.packed;

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

import org.apache.lucene.store.IndexInput;
import org.apache.lucene.util.RamUsageEstimator;

import java.io.IOException;
import java.util.Arrays;

/**
 * Direct wrapping of 8 bit values to a backing array of bytes.
 * @lucene.internal
 */

public class Direct8 extends PackedInts.ReaderImpl
        implements PackedInts.Mutable {
  private byte[] values;
  private static final int BITS_PER_VALUE = 8;

  public Direct8(int valueCount) {
    super(valueCount, BITS_PER_VALUE);
    values = new byte[valueCount];
  }

  public Direct8(IndexInput in, int valueCount)
          throws IOException {
    super(valueCount, BITS_PER_VALUE);
    byte[] values = new byte[valueCount];
    for(int i=0;i<valueCount;i++) {
      values[i] = in.readByte();
    }
    final int mod = valueCount % 8;
    if (mod != 0) {
      final int pad = 8-mod;
      // round out long
      for(int i=0;i<pad;i++) {
        in.readByte();
      }
    }

    this.values = values;
  }

  /**
   * Creates an array backed by the given values.
   * </p><p>
   * Note: The values are used directly, so changes to the given values will
   * affect the structure.
   * @param values used as the internal backing array.
   */
  public Direct8(byte[] values) {
    super(values.length, BITS_PER_VALUE);
    this.values = values;
  }

  public byte[] getArray() {
    return values;
  }

  public long get(final int index) {
    return 0xFFL & values[index];
  }

  public void set(final int index, final long value) {
    values[index] = (byte)(value & 0xFF);
  }

  public long ramBytesUsed() {
    return RamUsageEstimator.NUM_BYTES_ARRAY_HEADER + values.length;
  }

  public void clear() {
    Arrays.fill(values, (byte)0);
  }
}
