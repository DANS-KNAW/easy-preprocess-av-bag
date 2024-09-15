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

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
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
import java.text.MessageFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static java.text.MessageFormat.format;
import static nl.knaw.dans.avbag.core.BagInfoManager.getBag;
import static nl.knaw.dans.avbag.core.BagInfoManager.updateBagVersion;
import static nl.knaw.dans.avbag.core.ManifestManager.removePayloadsFromManifest;
import static nl.knaw.dans.avbag.core.ManifestManager.updateManifests;
import static nl.knaw.dans.avbag.core.XmlUtil.readXml;
import static org.apache.commons.io.FileUtils.copyDirectory;

@Slf4j
@RequiredArgsConstructor
public class AVConverter {
    @NonNull
    private final Path inputDir;
    @NonNull
    private final Path outputDir;
    @NonNull
    private final Path stagingDir;
    @NonNull
    private final PseudoFileSources pseudoFileSources;
    private final boolean keepInput;

    private long processed = 0L;
    private long createdBags = 0L;
    private long failedBags = 0L;
    private long doneBefore = 0L;

    AVConverter(Path inputDir, Path outputDir, Path stagingDir, PseudoFileSources pseudoFileSources) {
        this(inputDir, outputDir, stagingDir, pseudoFileSources, false);
    }

    public void convertAll() throws IOException {
        try (java.util.stream.Stream<Path> pathStream = Files.list(stagingDir)) {
            if (pathStream.findAny().isPresent()) {
                throw new IllegalStateException("The staging directory is not empty. Please empty the directory and try again.");
            }
        }
        try (java.util.stream.Stream<Path> pathStream = Files.walk(inputDir, 2)) {
            pathStream.filter(this::notSelfOrChild)
                .forEach(this::convertOne);
        }
        System.out.println(format("Conversion finished. Bags processed={6}, failed={7}, created={8}, doneBefore={9}. In directories: {3}={0}, {4}={1}, {5}={2}",
            getCount(inputDir),
            getCount(stagingDir),
            getCount(outputDir),
            inputDir,
            stagingDir,
            outputDir,
            processed,
            failedBags,
            createdBags,
            doneBefore
        ));
    }

    private long getCount(Path inputDir) throws IOException {
        try (Stream<Path> list = Files.list(inputDir)) {
            return list.count();
        }
    }

    private boolean notSelfOrChild(Path path) {
        return !path.getParent().equals(inputDir) && !path.equals(inputDir);
    }

    private void convertOne(Path bag) {
        Path bagParent = bag.getParent().getFileName();
        if (outputDir.resolve(bagParent).toFile().exists()) {
            doneBefore++;
            log.error("Skipped {}, it exists in {}", bagParent, outputDir);
            return;
        }
        try {
            Document filesXml = readXml(bag.resolve("metadata/files.xml"));
            PlaceHolders ph = new PlaceHolders(bag, filesXml);
            if (ph.hasSameFileIds(pseudoFileSources)) {
                createBags(bag, ph, filesXml);
            }
        }
        catch (Exception e) {
            log.error(MessageFormat.format(
                "{0} failed, it may or may not have (incomplete) bags in {1}",
                bag.getParent().getFileName(),
                stagingDir
            ), e);
            failedBags++;
        }
    }

    private void createBags(Path inputBagDir, PlaceHolders placeHolders, Document filesXml)
        throws IOException, TransformerException, MaliciousPathException, UnparsableVersionException, UnsupportedAlgorithmException,
        InvalidBagitFileFormatException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {
        String inputBagParentName = inputBagDir.getParent().getFileName().toString();
        SpringfieldFiles springfieldFiles = new SpringfieldFiles(filesXml, pseudoFileSources.getSpringFieldFiles(inputBagParentName));
        Path revision1 = stagingDir.resolve(inputBagParentName).resolve(inputBagDir.getFileName());
        Path revision2 = stagingDir.resolve(UUID.randomUUID().toString()).resolve(UUID.randomUUID().toString());

        log.info("Creating revision 1: {} ### {}", inputBagParentName, revision1.getParent().getFileName());
        copyDirectory(inputBagDir.toFile(), revision1.toFile());
        List<Path> removedFiles = new FileRemover(revision1).removeFiles(filesXml, new FilesToBeRemovedFilter(placeHolders.getPaths()));
        XmlUtil.writeFilesXml(revision1, filesXml);
        removedFiles.addAll(placeHolders.getPaths()); // So that are also removed from the manifest
        removePayloadsFromManifest(removedFiles, getBag(revision1));
        updateManifests(new BagReader().read(revision1));
        createdBags++;
        log.info("Creating revision 2: {} ### {}", inputBagParentName, revision2.getParent().getFileName());
        copyDirectory(revision1.toFile(), revision2.toFile());

        if (springfieldFiles.hasFilesToAdd()) {
            springfieldFiles.addFiles(placeHolders, revision2);
            XmlUtil.writeFilesXml(revision2, filesXml);
            updateManifests(updateBagVersion(revision2, revision1));
            createdBags++;
        }
        else {
            log.error("No streaming files found for {}", inputBagParentName);
        }
        processed++;

        moveStaged(revision1);
        if (springfieldFiles.hasFilesToAdd()) {
            moveStaged(revision2);
        }
        if (!keepInput) {
            FileUtils.deleteDirectory(inputBagDir.getParent().toFile());
        }
    }

    private void moveStaged(Path bagDir) throws IOException {
        Path source = bagDir.getParent();
        Path destination = outputDir.resolve(source.getFileName());
        FileUtils.moveDirectory(source.toFile(), destination.toFile());
    }

    private static void replacePayloadFile(Path bagDir, Path source, String destination) throws IOException {
        FileUtils.copyFile(
            source.toFile(),
            bagDir.resolve(destination).toFile(),
            true
        );
    }

    private static void removePayloadFile(Path bagDir, String destination) throws IOException {
        Files.deleteIfExists(bagDir.resolve(destination));
    }
}
