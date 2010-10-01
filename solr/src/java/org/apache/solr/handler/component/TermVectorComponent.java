package org.apache.solr.handler.component;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.document.SetBasedFieldSelector;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermVectorMapper;
import org.apache.lucene.index.TermVectorOffsetInfo;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.params.TermVectorParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.StrUtils;
import org.apache.solr.core.SolrCore;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.DocList;
import org.apache.solr.search.DocListAndSet;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.util.SolrPluginUtils;
import org.apache.solr.util.plugin.SolrCoreAware;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * Return term vectors for the documents in a query result set.
 * <p/>
 * Info available:
 * term, frequency, position, offset, IDF.
 * <p/>
 * <b>Note</b> Returning IDF can be expensive.
 */
public class TermVectorComponent extends SearchComponent implements SolrCoreAware {


  public static final String COMPONENT_NAME = "tv";

  protected NamedList initParams;
  public static final String TERM_VECTORS = "termVectors";


  public void process(ResponseBuilder rb) throws IOException {
    SolrParams params = rb.req.getParams();
    if (!params.getBool(COMPONENT_NAME, false)) {
      return;
    }

    NamedList termVectors = new NamedList();
    rb.rsp.add(TERM_VECTORS, termVectors);
    FieldOptions allFields = new FieldOptions();
    //figure out what options we have, and try to get the appropriate vector
    allFields.termFreq = params.getBool(TermVectorParams.TF, false);
    allFields.positions = params.getBool(TermVectorParams.POSITIONS, false);
    allFields.offsets = params.getBool(TermVectorParams.OFFSETS, false);
    allFields.docFreq = params.getBool(TermVectorParams.DF, false);
    allFields.tfIdf = params.getBool(TermVectorParams.TF_IDF, false);
    //boolean cacheIdf = params.getBool(TermVectorParams.IDF, false);
    //short cut to all values.
    boolean all = params.getBool(TermVectorParams.ALL, false);
    if (all == true) {
      allFields.termFreq = true;
      allFields.positions = true;
      allFields.offsets = true;
      allFields.docFreq = true;
      allFields.tfIdf = true;
    }

    String fldLst = params.get(TermVectorParams.FIELDS);
    if (fldLst == null) {
      fldLst = params.get(CommonParams.FL);
    }

    //use this to validate our fields
    IndexSchema schema = rb.req.getSchema();
    //Build up our per field mapping
    Map<String, FieldOptions> fieldOptions = new HashMap<String, FieldOptions>();
    NamedList warnings = new NamedList();
    List<String>  noTV = new ArrayList<String>();
    List<String>  noPos = new ArrayList<String>();
    List<String>  noOff = new ArrayList<String>();

    //we have specific fields to retrieve
    if (fldLst != null) {
      String [] fields = SolrPluginUtils.split(fldLst);
      for (String field : fields) {
        SchemaField sf = schema.getFieldOrNull(field);
        if (sf != null) {
          if (sf.storeTermVector()) {
            FieldOptions option = fieldOptions.get(field);
            if (option == null) {
              option = new FieldOptions();
              option.fieldName = field;
              fieldOptions.put(field, option);
            }
            //get the per field mappings
            option.termFreq = params.getFieldBool(field, TermVectorParams.TF, allFields.termFreq);
            option.docFreq = params.getFieldBool(field, TermVectorParams.DF, allFields.docFreq);
            option.tfIdf = params.getFieldBool(field, TermVectorParams.TF_IDF, allFields.tfIdf);
            //Validate these are even an option
            option.positions = params.getFieldBool(field, TermVectorParams.POSITIONS, allFields.positions);
            if (option.positions == true && sf.storeTermPositions() == false){
              noPos.add(field);
            }
            option.offsets = params.getFieldBool(field, TermVectorParams.OFFSETS, allFields.offsets);
            if (option.offsets == true && sf.storeTermOffsets() == false){
              noOff.add(field);
            }
          } else {//field doesn't have term vectors
            noTV.add(field);
          }
        } else {
          //field doesn't exist
          throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "undefined field: " + field);
        }
      }
    } //else, deal with all fields
    boolean hasWarnings = false;
    if (noTV.isEmpty() == false) {
      warnings.add("noTermVectors", noTV);
      hasWarnings = true;
    }
    if (noPos.isEmpty() == false) {
      warnings.add("noPositions", noPos);
      hasWarnings = true;
    }
    if (noOff.isEmpty() == false) {
      warnings.add("noOffsets", noOff);
      hasWarnings = true;
    }
    if (hasWarnings == true) {
      termVectors.add("warnings", warnings);
    }

    DocListAndSet listAndSet = rb.getResults();
    List<Integer> docIds = getInts(params.getParams(TermVectorParams.DOC_IDS));
    Iterator<Integer> iter;
    if (docIds != null && docIds.isEmpty() == false) {
      iter = docIds.iterator();
    } else {
      DocList list = listAndSet.docList;
      iter = list.iterator();
    }
    SolrIndexSearcher searcher = rb.req.getSearcher();

    IndexReader reader = searcher.getReader();
    //the TVMapper is a TermVectorMapper which can be used to optimize loading of Term Vectors
    SchemaField keyField = schema.getUniqueKeyField();
    String uniqFieldName = null;
    if (keyField != null) {
      uniqFieldName = keyField.getName();
    }
    //Only load the id field to get the uniqueKey of that field
    SetBasedFieldSelector fieldSelector = new SetBasedFieldSelector(Collections.singleton(uniqFieldName), Collections.<String>emptySet());
    TVMapper mapper = new TVMapper(reader);
    mapper.fieldOptions = allFields; //this will only stay set if fieldOptions.isEmpty() (in other words, only if the user didn't set any fields)
    while (iter.hasNext()) {
      Integer docId = iter.next();
      NamedList docNL = new NamedList();
      mapper.docNL = docNL;
      termVectors.add("doc-" + docId, docNL);

      if (keyField != null) {
        Document document = reader.document(docId, fieldSelector);
        Fieldable uniqId = document.getField(uniqFieldName);
        String uniqVal = null;
        if (uniqId != null) {
          uniqVal = keyField.getType().storedToReadable(uniqId);          
        }
        if (uniqVal != null) {
          docNL.add("uniqueKey", uniqVal);
          termVectors.add("uniqueKeyFieldName", uniqFieldName);
        }
      }
      if (fieldOptions.isEmpty() == false) {
        for (Map.Entry<String, FieldOptions> entry : fieldOptions.entrySet()) {
          mapper.fieldOptions = entry.getValue();
          reader.getTermFreqVector(docId, entry.getKey(), mapper);
        }
      } else {
        //deal with all fields by using the allFieldMapper
        reader.getTermFreqVector(docId, mapper);
      }
    }
  }

  private List<Integer> getInts(String[] vals) {
    List<Integer> result = null;
    if (vals != null && vals.length > 0) {
      result = new ArrayList<Integer>(vals.length);
      for (int i = 0; i < vals.length; i++) {
        try {
          result.add(new Integer(vals[i]));
        } catch (NumberFormatException e) {
          throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, e.getMessage(), e);
        }
      }
    }
    return result;
  }

  @Override
  public int distributedProcess(ResponseBuilder rb) throws IOException {
    int result = ResponseBuilder.STAGE_DONE;
    if (rb.stage == ResponseBuilder.STAGE_GET_FIELDS) {
      //Go ask each shard for it's vectors
      // for each shard, collect the documents for that shard.
      HashMap<String, Collection<ShardDoc>> shardMap = new HashMap<String, Collection<ShardDoc>>();
      for (ShardDoc sdoc : rb.resultIds.values()) {
        Collection<ShardDoc> shardDocs = shardMap.get(sdoc.shard);
        if (shardDocs == null) {
          shardDocs = new ArrayList<ShardDoc>();
          shardMap.put(sdoc.shard, shardDocs);
        }
        shardDocs.add(sdoc);
      }
      // Now create a request for each shard to retrieve the stored fields
      for (Collection<ShardDoc> shardDocs : shardMap.values()) {
        ShardRequest sreq = new ShardRequest();
        sreq.purpose = ShardRequest.PURPOSE_GET_FIELDS;

        sreq.shards = new String[]{shardDocs.iterator().next().shard};

        sreq.params = new ModifiableSolrParams();

        // add original params
        sreq.params.add(rb.req.getParams());
        sreq.params.remove(CommonParams.Q);//remove the query
        ArrayList<String> ids = new ArrayList<String>(shardDocs.size());
        for (ShardDoc shardDoc : shardDocs) {
          ids.add(shardDoc.id.toString());
        }
        sreq.params.add(TermVectorParams.DOC_IDS, StrUtils.join(ids, ','));

        rb.addRequest(this, sreq);
      }
      result = ResponseBuilder.STAGE_DONE;
    }
    return result;
  }

  private static class TVMapper extends TermVectorMapper {
    private IndexReader reader;
    private NamedList docNL;

    //needs to be set for each new field
    FieldOptions fieldOptions;

    //internal vars not passed in by construction
    private boolean useOffsets, usePositions;
    //private Map<String, Integer> idfCache;
    private NamedList fieldNL;
    private Term currentTerm;


    public TVMapper(IndexReader reader) {
      this.reader = reader;
    }

    public void map(BytesRef term, int frequency, TermVectorOffsetInfo[] offsets, int[] positions) {
      NamedList termInfo = new NamedList();
        fieldNL.add(term.utf8ToString(), termInfo);
        if (fieldOptions.termFreq == true) {
          termInfo.add("tf", frequency);
        }
        if (useOffsets == true) {
          NamedList theOffsets = new NamedList();
          termInfo.add("offsets", theOffsets);
          for (int i = 0; i < offsets.length; i++) {
            TermVectorOffsetInfo offset = offsets[i];
            theOffsets.add("start", offset.getStartOffset());
            theOffsets.add("end", offset.getEndOffset());
          }
        }
        if (usePositions == true) {
          NamedList positionsNL = new NamedList();
          for (int i = 0; i < positions.length; i++) {
            positionsNL.add("position", positions[i]);
          }
          termInfo.add("positions", positionsNL);
        }
        if (fieldOptions.docFreq == true) {
          termInfo.add("df", getDocFreq(term));
        }
        if (fieldOptions.tfIdf == true) {
          double tfIdfVal = ((double) frequency) / getDocFreq(term);
          termInfo.add("tf-idf", tfIdfVal);
        }
    }

    private int getDocFreq(BytesRef term) {
      int result = 1;
      currentTerm = currentTerm.createTerm(term);
      try {
        Terms terms = MultiFields.getTerms(reader, currentTerm.field());
        if (terms != null) {
          TermsEnum termsEnum = terms.iterator();
          if (termsEnum.seek(term) == TermsEnum.SeekStatus.FOUND) {
            result = termsEnum.docFreq();
          }
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      return result;
    }

    public void setExpectations(String field, int numTerms, boolean storeOffsets, boolean storePositions) {

      if (fieldOptions.docFreq == true && reader != null) {
        this.currentTerm = new Term(field);
      }
      useOffsets = storeOffsets && fieldOptions.offsets;
      usePositions = storePositions && fieldOptions.positions;
      fieldNL = new NamedList();
      docNL.add(field, fieldNL);
    }

    @Override
    public boolean isIgnoringPositions() {
      return fieldOptions.positions == false;  // if we are not interested in positions, then return true telling Lucene to skip loading them
    }

    @Override
    public boolean isIgnoringOffsets() {
      return fieldOptions.offsets == false;  //  if we are not interested in offsets, then return true telling Lucene to skip loading them
    }
  }

  public void prepare(ResponseBuilder rb) throws IOException {

  }

  //////////////////////// NamedListInitializedPlugin methods //////////////////////

  @Override
  public void init(NamedList args) {
    super.init(args);
    this.initParams = args;
  }

  public void inform(SolrCore core) {

  }

  public String getVersion() {
    return "$Revision$";
  }

  public String getSourceId() {
    return "$Id:$";
  }

  public String getSource() {
    return "$Revision:$";
  }

  public String getDescription() {
    return "A Component for working with Term Vectors";
  }
}

class FieldOptions {
  String fieldName;
  boolean termFreq, positions, offsets, docFreq, tfIdf;
}