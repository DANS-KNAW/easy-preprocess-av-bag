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

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import nl.knaw.dans.avbag.core.FileElements.FileElement;
import nl.knaw.dans.bagit.exceptions.InvalidBagitFileFormatException;
import nl.knaw.dans.bagit.exceptions.MaliciousPathException;
import nl.knaw.dans.bagit.exceptions.UnparsableVersionException;
import nl.knaw.dans.bagit.exceptions.UnsupportedAlgorithmException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static nl.knaw.dans.avbag.core.BagInfoManager.updateBagVersion;
import static nl.knaw.dans.avbag.core.ManifestManager.updateManifests;
import static org.apache.commons.io.FilenameUtils.getExtension;
import static org.apache.commons.io.FilenameUtils.removeExtension;

@Slf4j
public class SpringfieldFiles {
    private final Map<String, Path> springfieldFiles; // easy-file ID -> path to file in springfield dir
    private List<FileElement> filesInInputFilesXml = new ArrayList<>(); // easy-file ID -> <dct:identifier> element in orgFilesXml

    public SpringfieldFiles(Path bagDir, PseudoFileSources pseudoFileSources) throws IOException, ParserConfigurationException, SAXException {
        Document orgFilesXmlCopy = XmlUtil.readXml(bagDir.resolve("metadata/files.xml"));
        this.springfieldFiles = pseudoFileSources.getSpringFieldFiles(bagDir.getParent().getFileName().toString());
        filesInInputFilesXml = FileElements.read(orgFilesXmlCopy);
    }

    public void checkFilesXmlContainsAllSpringfieldFileIdsFromSources(Path modifiedBagDir) throws IOException, ParserConfigurationException, SAXException {
        Document filesXml = XmlUtil.readXml(modifiedBagDir.resolve("metadata/files.xml"));
        List<FileElement> newFileElements = FileElements.read(filesXml);
        if (newFileElements.size() < springfieldFiles.size()) {
            // adding the bagParent is of no use as the bag is not yet created
            throw new IllegalStateException("Not all springfield files in sources.csv have matching easy-file ID in files.xml");
        }
    }

    public boolean hasFilesToAdd() {
        return !filesInInputFilesXml.isEmpty();
    }

    public void addFiles(PlaceHolders placeHolders, Path bagDir, Path bagDirPreviousVersion)
        throws IOException, ParserConfigurationException, SAXException, TransformerException, MaliciousPathException, UnparsableVersionException, UnsupportedAlgorithmException,
        InvalidBagitFileFormatException, NoSuchAlgorithmException {
        Document newFilesXml = XmlUtil.readXml(bagDir.resolve("metadata/files.xml"));
        List<Node> newFileList = new ArrayList<>();
        for (FileElement fileInInputFilesXml : filesInInputFilesXml) {
            String fileId = fileInInputFilesXml.getFileId();
            String added = addPayloadFile(springfieldFiles.get(fileId), placeHolders.getDestPath(fileId), bagDir);
            Element newFileElement = newFileElement(added, fileInInputFilesXml, newFilesXml);
            newFileList.add(newFileElement);
        }
        // separate loops to not interfere prematurely
        for (Node newFile : newFileList) {
            newFilesXml.getElementsByTagName("files").item(0)
                .appendChild(newFile);
        }
        XmlUtil.writeFilesXml(bagDir, newFilesXml);
        updateManifests(updateBagVersion(bagDir, bagDirPreviousVersion));
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

    private Element newFileElement(String addedFilePath, FileElement fileElementInOldFilesXml, Document newFilesXml) {
        Element newElement = newFilesXml.createElement("file");
        newElement.setAttribute("filepath", addedFilePath);
        newElement.appendChild(newRightsElement("accessibleToRights", fileElementInOldFilesXml.getAccessibleToRights(), newFilesXml));
        newElement.appendChild(newRightsElement("visibleToRights", fileElementInOldFilesXml.getVisibleToRights(), newFilesXml));
        return newElement;
    }

    private Element newRightsElement(String tag, String text, Document newFilesXml) {
        Element rightsElement = newFilesXml.createElement(tag);
        rightsElement.setTextContent(text);
        return rightsElement;
    }
}
