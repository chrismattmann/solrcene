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

import org.apache.lucene.index.codecs.Codec;  // for javadocs

/**
 * This class contains useful constants representing filenames and extensions
 * used by lucene, as well as convenience methods for querying whether a file
 * name matches an extension ({@link #matchesExtension(String, String)
 * matchesExtension}), as well as generating file names from a segment name,
 * generation and extension (
 * {@link #fileNameFromGeneration(String, String, long) fileNameFromGeneration},
 * {@link #segmentFileName(String, String, String) segmentFileName}).
 *
 * <p><b>NOTE</b>: extensions used by codecs are not
 * listed here.  You must interact with the {@link Codec}
 * directly.
 *
 * @lucene.internal
 */

public final class IndexFileNames {

  /** Name of the index segment file */
  public static final String SEGMENTS = "segments";

  /** Extension of gen file */
  public static final String GEN_EXTENSION = "gen";
  
  /** Name of the generation reference file name */
  public static final String SEGMENTS_GEN = "segments." +  GEN_EXTENSION;
  
  /** Extension of norms file */
  public static final String NORMS_EXTENSION = "nrm";

  /** Extension of stored fields index file */
  public static final String FIELDS_INDEX_EXTENSION = "fdx";

  /** Extension of stored fields file */
  public static final String FIELDS_EXTENSION = "fdt";

  /** Extension of vectors fields file */
  public static final String VECTORS_FIELDS_EXTENSION = "tvf";

  /** Extension of vectors documents file */
  public static final String VECTORS_DOCUMENTS_EXTENSION = "tvd";

  /** Extension of vectors index file */
  public static final String VECTORS_INDEX_EXTENSION = "tvx";

  /** Extension of compound file */
  public static final String COMPOUND_FILE_EXTENSION = "cfs";

  /** Extension of compound file for doc store files*/
  public static final String COMPOUND_FILE_STORE_EXTENSION = "cfx";

  /** Extension of deletes */
  public static final String DELETES_EXTENSION = "del";

  /** Extension of field infos */
  public static final String FIELD_INFOS_EXTENSION = "fnm";

  /** Extension of separate norms */
  public static final String SEPARATE_NORMS_EXTENSION = "s";

  /**
   * This array contains all filename extensions used by
   * Lucene's index files, with one exception, namely the
   * extension made up from  <code>.s</code> + a number.
   * Also note that Lucene's <code>segments_N</code> files
   * do not have any filename extension.
   */
  public static final String INDEX_EXTENSIONS[] = new String[] {
    COMPOUND_FILE_EXTENSION,
    FIELD_INFOS_EXTENSION,
    FIELDS_INDEX_EXTENSION,
    FIELDS_EXTENSION,
    DELETES_EXTENSION,
    VECTORS_INDEX_EXTENSION,
    VECTORS_DOCUMENTS_EXTENSION,
    VECTORS_FIELDS_EXTENSION,
    GEN_EXTENSION,
    NORMS_EXTENSION,
    COMPOUND_FILE_STORE_EXTENSION,
  };

  public static final String[] STORE_INDEX_EXTENSIONS = new String[] {
    VECTORS_INDEX_EXTENSION,
    VECTORS_FIELDS_EXTENSION,
    VECTORS_DOCUMENTS_EXTENSION,
    FIELDS_INDEX_EXTENSION,
    FIELDS_EXTENSION
  };

  public static final String[] NON_STORE_INDEX_EXTENSIONS = new String[] {
    FIELD_INFOS_EXTENSION,
    NORMS_EXTENSION
  };
  
  static final String COMPOUND_EXTENSIONS_NOT_CODEC[] = new String[] {
    FIELD_INFOS_EXTENSION,
    FIELDS_INDEX_EXTENSION,
    FIELDS_EXTENSION,
  };
  
  /** File extensions for term vector support */
  public static final String VECTOR_EXTENSIONS[] = new String[] {
    VECTORS_INDEX_EXTENSION,
    VECTORS_DOCUMENTS_EXTENSION,
    VECTORS_FIELDS_EXTENSION
  };

  /**
   * Computes the full file name from base, extension and generation. If the
   * generation is -1, the file name is null. If it's 0, the file name is
   * &lt;base&gt;.&lt;ext&gt;. If it's > 0, the file name is
   * &lt;base&gt;_&lt;gen&gt;.&lt;ext&gt;.<br>
   * <b>NOTE:</b> .&lt;ext&gt; is added to the name only if <code>ext</code> is
   * not an empty string.
   * 
   * @param base main part of the file name
   * @param ext extension of the filename
   * @param gen generation
   */
  public static String fileNameFromGeneration(String base, String ext, long gen) {
    if (gen == SegmentInfo.NO) {
      return null;
    } else if (gen == SegmentInfo.WITHOUT_GEN) {
      return segmentFileName(base, "", ext);
    } else {
      // The '6' part in the length is: 1 for '.', 1 for '_' and 4 as estimate
      // to the gen length as string (hopefully an upper limit so SB won't
      // expand in the middle.
      StringBuilder res = new StringBuilder(base.length() + 6 + ext.length())
          .append(base).append('_').append(Long.toString(gen, Character.MAX_RADIX));
      if (ext.length() > 0) {
        res.append('.').append(ext);
      }
      return res.toString();
    }
  }

  /**
   * Returns true if the provided filename is one of the doc store files (ends
   * with an extension in {@link #STORE_INDEX_EXTENSIONS}).
   */
  public static boolean isDocStoreFile(String fileName) {
    if (fileName.endsWith(COMPOUND_FILE_STORE_EXTENSION))
      return true;
    for (String ext : STORE_INDEX_EXTENSIONS) {
      if (fileName.endsWith(ext))
        return true;
    }
    return false;
  }

  /**
   * Returns a file name that includes the given segment name, your own custom
   * name and extension. The format of the filename is:
   * &lt;segmentName&gt;(_&lt;name&gt;)(.&lt;ext&gt;).
   * <p>
   * <b>NOTE:</b> .&lt;ext&gt; is added to the result file name only if
   * <code>ext</code> is not empty.
   * <p>
   * <b>NOTE:</b> _&lt;name&gt; is added to the result file name only if
   * <code>name</code> is not empty.
   * <p>
   * <b>NOTE:</b> all custom files should be named using this method, or
   * otherwise some structures may fail to handle them properly (such as if they
   * are added to compound files).
   */
  public static String segmentFileName(String segmentName, String name, String ext) {
    if (ext.length() > 0 || name.length() > 0) {
      assert !ext.startsWith(".");
      StringBuilder sb = new StringBuilder(segmentName.length() + 2 + name.length() + ext.length());
      sb.append(segmentName);
      if (name.length() > 0) {
        sb.append('_').append(name);
      }
      if (ext.length() > 0) {
        sb.append('.').append(ext);
      }
      return sb.toString();
    } else {
      return segmentName;
    }
  }
  
  /**
   * Returns true if the given filename ends with the given extension. One
   * should provide a <i>pure</i> extension, withouth '.'.
   */
  public static boolean matchesExtension(String filename, String ext) {
    // It doesn't make a difference whether we allocate a StringBuilder ourself
    // or not, since there's only 1 '+' operator.
    return filename.endsWith("." + ext);
  }

  /**
   * Strips the segment name out of the given file name. If you used
   * {@link #segmentFileName} or {@link #fileNameFromGeneration} to create your
   * files, then this method simply removes whatever comes before the first '.',
   * or the second '_' (excluding both).
   * 
   * @return the filename with the segment name removed, or the given filename
   *         if it does not contain a '.' and '_'.
   */
  public static String stripSegmentName(String filename) {
    // If it is a .del file, there's an '_' after the first character
    int idx = filename.indexOf('_', 1);
    if (idx == -1) {
      // If it's not, strip everything that's before the '.'
      idx = filename.indexOf('.');
    }
    if (idx != -1) {
      filename = filename.substring(idx);
    }
    return filename;
  }
  
}
