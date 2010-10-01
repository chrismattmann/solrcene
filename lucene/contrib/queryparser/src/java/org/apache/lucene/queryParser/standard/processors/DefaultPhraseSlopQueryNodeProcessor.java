package org.apache.lucene.queryParser.standard.processors;

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

import java.util.List;

import org.apache.lucene.queryParser.core.QueryNodeException;
import org.apache.lucene.queryParser.core.config.QueryConfigHandler;
import org.apache.lucene.queryParser.core.nodes.QueryNode;
import org.apache.lucene.queryParser.core.nodes.SlopQueryNode;
import org.apache.lucene.queryParser.core.nodes.TokenizedPhraseQueryNode;
import org.apache.lucene.queryParser.core.processors.QueryNodeProcessorImpl;
import org.apache.lucene.queryParser.standard.config.DefaultPhraseSlopAttribute;
import org.apache.lucene.queryParser.standard.nodes.MultiPhraseQueryNode;

/**
 * This processor verifies if the attribute {@link DefaultPhraseSlopAttribute}
 * is defined in the {@link QueryConfigHandler}. If it is, it looks for every
 * {@link TokenizedPhraseQueryNode} and {@link MultiPhraseQueryNode} that does
 * not have any {@link SlopQueryNode} applied to it and creates an
 * {@link SlopQueryNode} and apply to it. The new {@link SlopQueryNode} has the
 * same slop value defined in the attribute. <br/>
 * 
 * @see SlopQueryNode
 * @see DefaultPhraseSlopAttribute
 */
public class DefaultPhraseSlopQueryNodeProcessor extends QueryNodeProcessorImpl {

  private boolean processChildren = true;

  private int defaultPhraseSlop;

  public DefaultPhraseSlopQueryNodeProcessor() {
    // empty constructor
  }

  @Override
  public QueryNode process(QueryNode queryTree) throws QueryNodeException {
    QueryConfigHandler queryConfig = getQueryConfigHandler();

    if (queryConfig != null) {

      if (queryConfig.hasAttribute(DefaultPhraseSlopAttribute.class)) {
        this.defaultPhraseSlop = queryConfig.getAttribute(
            DefaultPhraseSlopAttribute.class).getDefaultPhraseSlop();

        return super.process(queryTree);

      }

    }

    return queryTree;

  }

  @Override
  protected QueryNode postProcessNode(QueryNode node) throws QueryNodeException {

    if (node instanceof TokenizedPhraseQueryNode
        || node instanceof MultiPhraseQueryNode) {

      return new SlopQueryNode(node, this.defaultPhraseSlop);

    }

    return node;

  }

  @Override
  protected QueryNode preProcessNode(QueryNode node) throws QueryNodeException {

    if (node instanceof SlopQueryNode) {
      this.processChildren = false;

    }

    return node;

  }

  @Override
  protected void processChildren(QueryNode queryTree) throws QueryNodeException {

    if (this.processChildren) {
      super.processChildren(queryTree);

    } else {
      this.processChildren = true;
    }

  }

  @Override
  protected List<QueryNode> setChildrenOrder(List<QueryNode> children)
      throws QueryNodeException {

    return children;

  }

}
