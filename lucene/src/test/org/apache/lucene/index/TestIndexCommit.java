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

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCaseJ4;
import org.junit.Test;

public class TestIndexCommit extends LuceneTestCaseJ4 {

  @Test
  public void testEqualsHashCode() throws Exception {
    // LUCENE-2417: equals and hashCode() impl was inconsistent
    final Directory dir = newDirectory(newRandom());
    
    IndexCommit ic1 = new IndexCommit() {
      @Override public String getSegmentsFileName() { return "a"; }
      @Override public long getVersion() { return 12; }
      @Override public Directory getDirectory() { return dir; }
      @Override public Collection<String> getFileNames() throws IOException { return null; }
      @Override public void delete() {}
      @Override public long getGeneration() { return 0; }
      @Override public long getTimestamp() throws IOException { return 1;}
      @Override public Map<String, String> getUserData() throws IOException { return null; }
      @Override public boolean isDeleted() { return false; }
      @Override public boolean isOptimized() { return false; }
    };
    
    IndexCommit ic2 = new IndexCommit() {
      @Override public String getSegmentsFileName() { return "b"; }
      @Override public long getVersion() { return 12; }
      @Override public Directory getDirectory() { return dir; }
      @Override public Collection<String> getFileNames() throws IOException { return null; }
      @Override public void delete() {}
      @Override public long getGeneration() { return 0; }
      @Override public long getTimestamp() throws IOException { return 1;}
      @Override public Map<String, String> getUserData() throws IOException { return null; }
      @Override public boolean isDeleted() { return false; }
      @Override public boolean isOptimized() { return false; }
    };

    assertEquals(ic1, ic2);
    assertEquals("hash codes are not equals", ic1.hashCode(), ic2.hashCode());
    dir.close();
  }
}
