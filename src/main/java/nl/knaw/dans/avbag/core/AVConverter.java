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
import nl.knaw.dans.bagit.exceptions.InvalidBagitFileFormatException;
import nl.knaw.dans.bagit.exceptions.MaliciousPathException;
import nl.knaw.dans.bagit.exceptions.UnparsableVersionException;
import nl.knaw.dans.bagit.exceptions.UnsupportedAlgorithmException;
import nl.knaw.dans.bagit.reader.BagReader;
import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static nl.knaw.dans.avbag.core.BagInfoManager.updateBagVersion;
import static nl.knaw.dans.avbag.core.ManifestManager.removePayloadsFromManifest;
import static nl.knaw.dans.avbag.core.ManifestManager.updateManifests;
import static nl.knaw.dans.avbag.core.XmlUtil.readXml;
import static org.apache.commons.io.FileUtils.copyDirectory;

@Slf4j
public class AVConverter {
    private final Path inputDir;
    private final Path outputDir;
    private final Path stagingDir;
    private final PseudoFileSources pseudoFileSources;

    public AVConverter(Path inputDir, Path outputDir, Path stagingDir, PseudoFileSources pseudoFileSources) {
        this.inputDir = inputDir;
        this.outputDir = outputDir;
        this.stagingDir = stagingDir;
        this.pseudoFileSources = pseudoFileSources;
    }

    public void convertAll() throws IOException {
        try (var pathStream = Files.list(stagingDir)) {
            if (pathStream.findAny().isPresent()) {
                throw new IllegalStateException("The staging directory is not empty. Please empty the directory and try again.");
            }
        }
        try (var pathStream = Files.walk(inputDir, 2)) {
            pathStream.filter(this::notSelfOrChild).forEach(this::convertOne);
        }
    }

    private boolean notSelfOrChild(Path path) {
        return !path.getParent().equals(inputDir) && !path.equals(inputDir);
    }

    private void convertOne(Path bag) {
        var bagParent = bag.getParent().getFileName();
        if (outputDir.resolve(bagParent).toFile().exists()) {
            log.error("{} skipped, it exists in {}", bagParent, outputDir);
            return;
        }
        try {
            var filesXml = readXml(bag.resolve("metadata/files.xml"));
            var ph = new PlaceHolders(bag, filesXml);
            if (ph.hasSameFileIds(pseudoFileSources)) {
                createBags(bag, ph, filesXml);
            }
        }
        catch (Exception e) {
            log.error("%s failed, it may or may not have (incomplete) bags in %s"
                .formatted(bag.getParent().getFileName(), stagingDir), e
            );
        }
    }

    private void createBags(Path inputBagDir, PlaceHolders placeHolders, Document filesXml)
        throws IOException, TransformerException, MaliciousPathException, UnparsableVersionException, UnsupportedAlgorithmException,
        InvalidBagitFileFormatException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {
        var inputBagParentName = inputBagDir.getParent().getFileName().toString();
        var revision1 = stagingDir.resolve(inputBagParentName).resolve(inputBagDir.getFileName());
        var revision2 = stagingDir.resolve(UUID.randomUUID().toString()).resolve(UUID.randomUUID().toString());
        var revision3 = stagingDir.resolve(UUID.randomUUID().toString()).resolve(UUID.randomUUID().toString());

        log.info("Creating revision 1: {} ### {}", inputBagParentName, revision1.getParent().getFileName());
        copyDirectory(inputBagDir.toFile(), revision1.toFile());
        for (var entry : pseudoFileSources.getDarkArchiveFiles(inputBagParentName).entrySet()) {
            replacePayloadFile(revision1, entry.getValue(), placeHolders.getDestPath(entry.getKey()));
        }
        updateManifests(new BagReader().read(revision1));

        log.info("Creating revision 2: {} ### {}", inputBagParentName, revision2.getParent().getFileName());
        copyDirectory(revision1.toFile(), revision2.toFile());
        var removedFiles = new NoneNoneFiles(revision2).removeNoneNone(filesXml);
        XmlUtil.writeFilesXml(revision2, filesXml);
        removePayloadsFromManifest(removedFiles, updateBagVersion(revision2, revision1));

        var springfieldFiles = new SpringfieldFiles(revision3, filesXml, pseudoFileSources.getSpringFieldFiles(inputBagParentName));
        if (springfieldFiles.hasFilesToAdd()) {
            log.info("Creating revision 3: {} ### {}", inputBagParentName, revision3.getParent().getFileName());
            copyDirectory(revision2.toFile(), revision3.toFile());
            springfieldFiles.addFiles(placeHolders);
            XmlUtil.writeFilesXml(revision3, filesXml);
            updateManifests(updateBagVersion(revision3, revision3));
        }

        moveStaged(revision1);
        moveStaged(revision2);
        if (springfieldFiles.hasFilesToAdd()) {
            moveStaged(revision3);
        }
        FileUtils.deleteDirectory(inputBagDir.getParent().toFile());
        log.info("Finished {} ## {} ## {}",
            revision1.getParent().getFileName(),
            revision2.getParent().getFileName(),
            springfieldFiles.hasFilesToAdd()
                ? revision3.getParent().getFileName()
                : ""
        );
    }

    private void moveStaged(Path bagDir) throws IOException {
        var source = bagDir.getParent();
        var destination = outputDir.resolve(source.getFileName());
        FileUtils.moveDirectory(source.toFile(), destination.toFile());
    }

    private static void replacePayloadFile(Path bagDir, Path source, String destination) throws IOException {
        FileUtils.copyFile(
            source.toFile(),
            bagDir.resolve(destination).toFile(),
            true, REPLACE_EXISTING, COPY_ATTRIBUTES
        );
    }
}
