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

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static org.apache.commons.io.FilenameUtils.getExtension;
import static org.apache.commons.io.FilenameUtils.removeExtension;

@Slf4j
public class SpringfieldFiles {

    private final Document filesXml;
    private final Map<String, Path> springfieldFiles;
    private final Map<String, Element> idToElement = new HashMap<>();

    public SpringfieldFiles(Document filesXml, Map<String, Path> springfieldFiles) {
        this.filesXml = filesXml;
        this.springfieldFiles = springfieldFiles;
        NodeList idElems = filesXml.getElementsByTagName("dct:identifier");
        for (int i = 0; i < idElems.getLength(); i++) {
            Element idElem = (Element) idElems.item(i);
            if (springfieldFiles.containsKey(idElem.getTextContent())) {
                idToElement.put(idElem.getTextContent(), (Element) idElem.getParentNode());
            }
        }
        if (idToElement.size() != springfieldFiles.size()) {
            // adding the bagParent is of no use as the bag is not yet created
            throw new IllegalStateException("Not all springfield files in the mapping are present in the second bag");
        }
    }

    public boolean hasFilesToAdd() {
        return !idToElement.isEmpty();
    }

    public void addFiles(PlaceHolders placeHolders, Path bagDir) throws IOException {
        List<Node> newFileList = new ArrayList<>();
        for (Map.Entry<String, Element> entry : idToElement.entrySet()) {
            String fileId = entry.getKey();
            String added = addPayloadFile(springfieldFiles.get(fileId), placeHolders.getDestPath(fileId), bagDir);
            Element newFileElement = newFileElement(added, entry.getValue());
            newFileList.add(newFileElement);
        }
        // separate loops to not interfere prematurely
        for (Node newFile : newFileList) {
            filesXml.getElementsByTagName("files").item(0)
                .appendChild(newFile);
        }
    }

    private String addPayloadFile(Path source, String placeHolder, Path bagDir) throws IOException {
        String sourceExtension = getExtension(source.toString());
        String placeHolderExtension = getExtension(placeHolder);
        String newExtension = sourceExtension.equals(placeHolderExtension)
            ? "-streaming." + sourceExtension
            : "." + sourceExtension;

        String destination = removeExtension(placeHolder) + newExtension;
        FileUtils.copyFile(
            source.toFile(),
            bagDir.resolve(destination).toFile(),
            true
        );
        return destination;
    }

    private Element newFileElement(String addedFilePath, Element oldFileElement) {
        Element newElement = filesXml.createElement("file");
        newElement.setAttribute("filepath", addedFilePath);
        newElement.appendChild(newRightsElement("accessibleToRights", oldFileElement));
        newElement.appendChild(newRightsElement("visibleToRights", oldFileElement));
        return newElement;
    }

    private Element newRightsElement(String tag, Element oldFileElement) {
        Element oldRights = (Element) oldFileElement.getElementsByTagName(tag).item(0);
        Element rightsElement = filesXml.createElement(oldRights.getTagName());
        rightsElement.setTextContent(oldRights.getTextContent());
        return rightsElement;
    }
}
