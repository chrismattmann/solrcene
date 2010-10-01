package org.apache.lucene.index.codecs.mockintblock;

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

import java.io.IOException;
import java.util.Set;

import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.codecs.Codec;
import org.apache.lucene.index.codecs.FieldsConsumer;
import org.apache.lucene.index.codecs.FieldsProducer;
import org.apache.lucene.index.codecs.sep.IntStreamFactory;
import org.apache.lucene.index.codecs.sep.IntIndexInput;
import org.apache.lucene.index.codecs.sep.IntIndexOutput;
import org.apache.lucene.index.codecs.sep.SepPostingsReaderImpl;
import org.apache.lucene.index.codecs.sep.SepPostingsWriterImpl;
import org.apache.lucene.index.codecs.intblock.VariableIntBlockIndexInput;
import org.apache.lucene.index.codecs.intblock.VariableIntBlockIndexOutput;
import org.apache.lucene.index.codecs.standard.SimpleStandardTermsIndexReader;
import org.apache.lucene.index.codecs.standard.SimpleStandardTermsIndexWriter;
import org.apache.lucene.index.codecs.standard.StandardPostingsWriter;
import org.apache.lucene.index.codecs.standard.StandardPostingsReader;
import org.apache.lucene.index.codecs.standard.StandardTermsDictReader;
import org.apache.lucene.index.codecs.standard.StandardTermsDictWriter;
import org.apache.lucene.index.codecs.standard.StandardTermsIndexReader;
import org.apache.lucene.index.codecs.standard.StandardTermsIndexWriter;
import org.apache.lucene.index.codecs.standard.StandardCodec;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.BytesRef;

/**
 * A silly test codec to verify core support for variable
 * sized int block encoders is working.  The int encoder
 * used here writes baseBlockSize ints at once, if the first
 * int is <= 3, else 2*baseBlockSize.
 */

public class MockVariableIntBlockCodec extends Codec {
  private final int baseBlockSize;

  public MockVariableIntBlockCodec(int baseBlockSize) {
    name = "MockVariableIntBlock";
    this.baseBlockSize = baseBlockSize;
  }

  @Override
  public String toString() {
    return name + "(baseBlockSize="+ baseBlockSize + ")";
  }

  private class MockIntFactory extends IntStreamFactory {

    @Override
    public IntIndexInput openInput(Directory dir, String fileName, int readBufferSize) throws IOException {
      final IndexInput in = dir.openInput(fileName, readBufferSize);
      final int baseBlockSize = in.readInt();
      return new VariableIntBlockIndexInput(in) {

        @Override
        protected BlockReader getBlockReader(final IndexInput in, final int[] buffer) throws IOException {
          return new BlockReader() {
            public void seek(long pos) {}
            public int readBlock() throws IOException {
              buffer[0] = in.readVInt();
              final int count = buffer[0] <= 3 ? baseBlockSize-1 : 2*baseBlockSize-1;
              assert buffer.length >= count: "buffer.length=" + buffer.length + " count=" + count;
              for(int i=0;i<count;i++) {
                buffer[i+1] = in.readVInt();
              }
              return 1+count;
            }
          };
        }
      };
    }

    @Override
    public IntIndexOutput createOutput(Directory dir, String fileName) throws IOException {
      final IndexOutput out = dir.createOutput(fileName);
      out.writeInt(baseBlockSize);
      return new VariableIntBlockIndexOutput(out, 2*baseBlockSize) {

        int pendingCount;
        final int[] buffer = new int[2+2*baseBlockSize];

        @Override
        protected int add(int value) throws IOException {
          buffer[pendingCount++] = value;
          // silly variable block length int encoder: if
          // first value <= 3, we write N vints at once;
          // else, 2*N
          final int flushAt = buffer[0] <= 3 ? baseBlockSize : 2*baseBlockSize;

          // intentionally be non-causal here:
          if (pendingCount == flushAt+1) {
            for(int i=0;i<flushAt;i++) {
              out.writeVInt(buffer[i]);
            }
            buffer[0] = buffer[flushAt];
            pendingCount = 1;
            return flushAt;
          } else {
            return 0;
          }
        }
      };
    }
  }

  @Override
  public FieldsConsumer fieldsConsumer(SegmentWriteState state) throws IOException {
    StandardPostingsWriter postingsWriter = new SepPostingsWriterImpl(state, new MockIntFactory());

    boolean success = false;
    StandardTermsIndexWriter indexWriter;
    try {
      indexWriter = new SimpleStandardTermsIndexWriter(state);
      success = true;
    } finally {
      if (!success) {
        postingsWriter.close();
      }
    }

    success = false;
    try {
      FieldsConsumer ret = new StandardTermsDictWriter(indexWriter, state, postingsWriter, BytesRef.getUTF8SortedAsUnicodeComparator());
      success = true;
      return ret;
    } finally {
      if (!success) {
        try {
          postingsWriter.close();
        } finally {
          indexWriter.close();
        }
      }
    }
  }

  @Override
  public FieldsProducer fieldsProducer(SegmentReadState state) throws IOException {
    StandardPostingsReader postingsReader = new SepPostingsReaderImpl(state.dir,
                                                                      state.segmentInfo,
                                                                      state.readBufferSize,
                                                                      new MockIntFactory());

    StandardTermsIndexReader indexReader;
    boolean success = false;
    try {
      indexReader = new SimpleStandardTermsIndexReader(state.dir,
                                                       state.fieldInfos,
                                                       state.segmentInfo.name,
                                                       state.termsIndexDivisor,
                                                       BytesRef.getUTF8SortedAsUnicodeComparator());
      success = true;
    } finally {
      if (!success) {
        postingsReader.close();
      }
    }

    success = false;
    try {
      FieldsProducer ret = new StandardTermsDictReader(indexReader,
                                                       state.dir,
                                                       state.fieldInfos,
                                                       state.segmentInfo.name,
                                                       postingsReader,
                                                       state.readBufferSize,
                                                       BytesRef.getUTF8SortedAsUnicodeComparator(),
                                                       StandardCodec.TERMS_CACHE_SIZE);
      success = true;
      return ret;
    } finally {
      if (!success) {
        try {
          postingsReader.close();
        } finally {
          indexReader.close();
        }
      }
    }
  }

  @Override
  public void files(Directory dir, SegmentInfo segmentInfo, Set<String> files) {
    SepPostingsReaderImpl.files(segmentInfo, files);
    StandardTermsDictReader.files(dir, segmentInfo, files);
    SimpleStandardTermsIndexReader.files(dir, segmentInfo, files);
  }

  @Override
  public void getExtensions(Set<String> extensions) {
    SepPostingsWriterImpl.getExtensions(extensions);
    StandardTermsDictReader.getExtensions(extensions);
    SimpleStandardTermsIndexReader.getIndexExtensions(extensions);
  }
}
