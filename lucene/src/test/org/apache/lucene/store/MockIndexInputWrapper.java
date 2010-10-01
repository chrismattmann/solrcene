package org.apache.lucene.store;

import java.io.IOException;
import java.util.Map;

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
 * Used by MockDirectoryWrapper to create an input stream that
 * keeps track of when it's been closed.
 */

public class MockIndexInputWrapper extends IndexInput {
  private MockDirectoryWrapper dir;
  private String name;
  private IndexInput delegate;
  private boolean isClone;

  /** Construct an empty output buffer. 
   * @throws IOException */
  public MockIndexInputWrapper(MockDirectoryWrapper dir, String name, IndexInput delegate) {
    this.name = name;
    this.dir = dir;
    this.delegate = delegate;
  }

  @Override
  public void close() throws IOException {
    delegate.close();
    // Pending resolution on LUCENE-686 we may want to
    // remove the conditional check so we also track that
    // all clones get closed:
    if (!isClone) {
      synchronized(dir) {
        Integer v = dir.openFiles.get(name);
        // Could be null when MockRAMDirectory.crash() was called
        if (v != null) {
          if (v.intValue() == 1) {
            dir.openFiles.remove(name);
          } else {
            v = Integer.valueOf(v.intValue()-1);
            dir.openFiles.put(name, v);
          }
        }
        dir.files.remove(this);
      }
    }
  }

  @Override
  public Object clone() {
    IndexInput iiclone = (IndexInput) delegate.clone();
    MockIndexInputWrapper clone = new MockIndexInputWrapper(dir, name, iiclone);
    clone.isClone = true;
    // Pending resolution on LUCENE-686 we may want to
    // uncomment this code so that we also track that all
    // clones get closed:
    /*
    synchronized(dir.openFiles) {
      if (dir.openFiles.containsKey(name)) {
        Integer v = (Integer) dir.openFiles.get(name);
        v = Integer.valueOf(v.intValue()+1);
        dir.openFiles.put(name, v);
      } else {
        throw new RuntimeException("BUG: cloned file was not open?");
      }
    }
    */
    return clone;
  }

  @Override
  public long getFilePointer() {
    return delegate.getFilePointer();
  }

  @Override
  public void seek(long pos) throws IOException {
    delegate.seek(pos);
  }

  @Override
  public long length() {
    return delegate.length();
  }

  @Override
  public byte readByte() throws IOException {
    return delegate.readByte();
  }

  @Override
  public void readBytes(byte[] b, int offset, int len) throws IOException {
    delegate.readBytes(b, offset, len);
  }

  @Override
  public void copyBytes(IndexOutput out, long numBytes) throws IOException {
    delegate.copyBytes(out, numBytes);
  }

  @Override
  public void readBytes(byte[] b, int offset, int len, boolean useBuffer)
      throws IOException {
    delegate.readBytes(b, offset, len, useBuffer);
  }

  @Override
  public short readShort() throws IOException {
    return delegate.readShort();
  }

  @Override
  public int readInt() throws IOException {
    return delegate.readInt();
  }

  @Override
  public int readVInt() throws IOException {
    return delegate.readVInt();
  }

  @Override
  public long readLong() throws IOException {
    return delegate.readLong();
  }

  @Override
  public long readVLong() throws IOException {
    return delegate.readVLong();
  }

  @Override
  public String readString() throws IOException {
    return delegate.readString();
  }

  @Override
  public Map<String,String> readStringStringMap() throws IOException {
    return delegate.readStringStringMap();
  }
}
