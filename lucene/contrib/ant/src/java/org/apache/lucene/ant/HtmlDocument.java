package org.apache.lucene.ant;

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

import org.apache.lucene.document.Field;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.w3c.tidy.Tidy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

/**
 *  The <code>HtmlDocument</code> class creates a Lucene {@link
 *  org.apache.lucene.document.Document} from an HTML document. <P>
 *
 *  It does this by using JTidy package. It can take input input
 *  from {@link java.io.File} or {@link java.io.InputStream}.
 *
 */
public class HtmlDocument {
    private Element rawDoc;


    //-------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------

    /**
     *  Constructs an <code>HtmlDocument</code> from a {@link
     *  java.io.File}.
     *
     *@param  file             the <code>File</code> containing the
     *      HTML to parse
     *@exception  IOException  if an I/O exception occurs
     */
    public HtmlDocument(File file) throws IOException {
        Tidy tidy = new Tidy();
        tidy.setQuiet(true);
        tidy.setShowWarnings(false);
        org.w3c.dom.Document root = null;
        InputStream is = new FileInputStream(file);
        try {
          root =  tidy.parseDOM(is, null);
        } finally {
          is.close();
        }
        rawDoc = root.getDocumentElement();
    }


    /**
     *  Constructs an <code>HtmlDocument</code> from an {@link
     *  java.io.InputStream}.
     *
     *@param  is               the <code>InputStream</code>
     *      containing the HTML
     */
    public HtmlDocument(InputStream is) {
        Tidy tidy = new Tidy();
        tidy.setQuiet(true);
        tidy.setShowWarnings(false);
        org.w3c.dom.Document root = tidy.parseDOM(is, null);
        rawDoc = root.getDocumentElement();
    }


    /**
     *  Constructs an <code>HtmlDocument</code> from a
     *  {@link java.io.File}.
     *  @param  file the <code>File</code> containing the
     *   HTML to parse
     *  @param  tidyConfigFile   the <code>String</code>
     *   containing the full path to the Tidy config file
     *  @exception  IOException  if an I/O exception occurs */
    public HtmlDocument(File file, String tidyConfigFile) throws IOException {
        Tidy tidy = new Tidy();
        tidy.setConfigurationFromFile(tidyConfigFile);
        tidy.setQuiet(true);
        tidy.setShowWarnings(false);
        org.w3c.dom.Document root =
                tidy.parseDOM(new FileInputStream(file), null);
        rawDoc = root.getDocumentElement();
    }

    /**
     *  Creates a Lucene <code>Document</code> from a
     *  {@link java.io.File}.
     *  @param  file 
     *  @param  tidyConfigFile the full path to the Tidy
     *   config file
     *  @exception  IOException */
    public static org.apache.lucene.document.Document
        Document(File file, String tidyConfigFile) throws IOException {

        HtmlDocument htmlDoc = new HtmlDocument(file, tidyConfigFile);

        org.apache.lucene.document.Document luceneDoc = new org.apache.lucene.document.Document();

        luceneDoc.add(new Field("title", htmlDoc.getTitle(), Field.Store.YES, Field.Index.ANALYZED));
        luceneDoc.add(new Field("contents", htmlDoc.getBody(), Field.Store.YES, Field.Index.ANALYZED));

        String contents = null;
        BufferedReader br =
            new BufferedReader(new FileReader(file));
        StringWriter sw = new StringWriter();
        String line = br.readLine();
        while (line != null) {
            sw.write(line);
            line = br.readLine();
        }
        br.close();
        contents = sw.toString();
        sw.close();

        luceneDoc.add(new Field("rawcontents", contents, Field.Store.YES, Field.Index.NO));

        return luceneDoc;
    }

    /**
     *  Creates a Lucene <code>Document</code> from an {@link
     *  java.io.InputStream}.
     *
     *@param  is
     */
    public static org.apache.lucene.document.Document
            getDocument(InputStream is) {
        HtmlDocument htmlDoc = new HtmlDocument(is);
        org.apache.lucene.document.Document luceneDoc =
                new org.apache.lucene.document.Document();

        luceneDoc.add(new Field("title", htmlDoc.getTitle(), Field.Store.YES, Field.Index.ANALYZED));
        luceneDoc.add(new Field("contents", htmlDoc.getBody(), Field.Store.YES, Field.Index.ANALYZED));

        return luceneDoc;
    }


    //-------------------------------------------------------------
    // Public methods
    //-------------------------------------------------------------

    /**
     *  Creates a Lucene <code>Document</code> from a {@link
     *  java.io.File}.
     *
     *@param  file
     *@exception  IOException
     */
    public static org.apache.lucene.document.Document
            Document(File file) throws IOException {
        HtmlDocument htmlDoc = new HtmlDocument(file);
        org.apache.lucene.document.Document luceneDoc =
                new org.apache.lucene.document.Document();

        luceneDoc.add(new Field("title", htmlDoc.getTitle(), Field.Store.YES, Field.Index.ANALYZED));
        luceneDoc.add(new Field("contents", htmlDoc.getBody(), Field.Store.YES, Field.Index.ANALYZED));

        String contents = null;
        BufferedReader br =
                new BufferedReader(new FileReader(file));
        StringWriter sw = new StringWriter();
        String line = br.readLine();
        while (line != null) {
            sw.write(line);
            line = br.readLine();
        }
        br.close();
        contents = sw.toString();
        sw.close();

        luceneDoc.add(new Field("rawcontents", contents, Field.Store.YES, Field.Index.NO));

        return luceneDoc;
    }


    //-------------------------------------------------------------
    // Private methods
    //-------------------------------------------------------------

    /**
     *  Runs <code>HtmlDocument</code> on the files specified on
     *  the command line.
     *
     *@param  args           Command line arguments
     *@exception  Exception  Description of Exception
     */
    public static void main(String args[]) throws Exception {
//         HtmlDocument doc = new HtmlDocument(new File(args[0]));
//         System.out.println("Title = " + doc.getTitle());
//         System.out.println("Body  = " + doc.getBody());

        HtmlDocument doc =
                new HtmlDocument(new FileInputStream(new File(args[0])));
        System.out.println("Title = " + doc.getTitle());
        System.out.println("Body  = " + doc.getBody());
    }


    /**
     *  Gets the title attribute of the <code>HtmlDocument</code>
     *  object.
     *
     *@return    the title value
     */
    public String getTitle() {
        if (rawDoc == null) {
            return null;
        }

        String title = "";

        NodeList nl = rawDoc.getElementsByTagName("title");
        if (nl.getLength() > 0) {
            Element titleElement = ((Element) nl.item(0));
            Text text = (Text) titleElement.getFirstChild();
            if (text != null) {
                title = text.getData();
            }
        }
        return title;
    }


    /**
     *  Gets the bodyText attribute of the
     *  <code>HtmlDocument</code> object.
     *
     *@return    the bodyText value
     */
    public String getBody() {
        if (rawDoc == null) {
            return null;
        }

        String body = "";
        NodeList nl = rawDoc.getElementsByTagName("body");
        if (nl.getLength() > 0) {
            body = getBodyText(nl.item(0));
        }
        return body;
    }


    /**
     *  Gets the bodyText attribute of the
     *  <code>HtmlDocument</code> object.
     *
     *@param  node  a DOM Node
     *@return       The bodyText value
     */
    private String getBodyText(Node node) {
        NodeList nl = node.getChildNodes();
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < nl.getLength(); i++) {
            Node child = nl.item(i);
            switch (child.getNodeType()) {
                case Node.ELEMENT_NODE:
                    buffer.append(getBodyText(child));
                    buffer.append(" ");
                    break;
                case Node.TEXT_NODE:
                    buffer.append(((Text) child).getData());
                    break;
            }
        }
        return buffer.toString();
    }
}

