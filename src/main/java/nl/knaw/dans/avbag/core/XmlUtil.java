/*
 * Copyright (C) 2024 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.avbag.core;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Predicate;

public class XmlUtil {
    public static Document readXml(Path path) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setNamespaceAware(true);

        return factory
            .newDocumentBuilder()
            .parse(path.toFile());
    }

    public static Predicate<Element> hasFilePathIn(List<Path> filepaths) {
        return element -> filepaths.contains(Paths.get(element.getAttribute("filepath")));
    }

    public static boolean isAccessibleToNone(Node fileElement) {
        return isNone(fileElement, "accessibleToRights");
    }

    public static boolean isVisibleToNone(Node fileElement) {
        return isNone(fileElement, "visibleToRights");
    }

    public static boolean isNone(Node fileElement, String tag) {
        NodeList elements = ((Element) fileElement).getElementsByTagName(tag);
        if (elements.getLength() == 0)
            return true;
        return "NONE".equals(elements.item(0).getTextContent());
    }

    public static void writeFilesXml(Path bagDir, Document filesXml) throws IOException, TransformerException {
        Writer writer = new FileWriter(bagDir.resolve("metadata").resolve("files.xml").toFile());
        getTransformer().transform(new DOMSource(filesXml), new StreamResult(writer));
    }

    private static Transformer getTransformer() throws TransformerConfigurationException {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        return transformer;
    }

    public static String serializeNode(Node node) {
        try {
            StringWriter sw = new StringWriter();
            Transformer t = TransformerFactory.newInstance().newTransformer();
            t.transform(new DOMSource(node), new StreamResult(sw));
            return sw.toString();
        }
        catch (Exception e) {
            return e.getMessage();
        }
    }
}
