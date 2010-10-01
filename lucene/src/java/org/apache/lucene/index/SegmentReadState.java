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

import org.apache.lucene.store.Directory;

/**
 * @lucene.experimental
 */
public class SegmentReadState {
  public final Directory dir;
  public final SegmentInfo segmentInfo;
  public final FieldInfos fieldInfos;
  public final int readBufferSize;

  // NOTE: if this is < 0, that means "defer terms index
  // load until needed".  But if the codec must load the
  // terms index on init (preflex is the only once currently
  // that must do so), then it should negate this value to
  // get the app's terms divisor:
  public final int termsIndexDivisor;

  public SegmentReadState(Directory dir,
                          SegmentInfo info,
                          FieldInfos fieldInfos,
                          int readBufferSize,
                          int termsIndexDivisor) {
    this.dir = dir;
    this.segmentInfo = info;
    this.fieldInfos = fieldInfos;
    this.readBufferSize = readBufferSize;
    this.termsIndexDivisor = termsIndexDivisor;
  }
}