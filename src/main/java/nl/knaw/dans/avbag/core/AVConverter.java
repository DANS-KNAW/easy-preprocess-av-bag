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
import org.apache.commons.io.FileUtils;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.UUID;
import java.util.stream.Stream;

import static java.text.MessageFormat.format;
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
        System.out.println(format("Conversion finished. Bags processed={6}, failed={7}, created={8}. In directories: {3}={0}, {4}={1}, {5}={2}",
            getSubdirCount(inputDir),
            getSubdirCount(stagingDir),
            getSubdirCount(outputDir),
            inputDir,
            stagingDir,
            outputDir,
            processed,
            failedBags,
            createdBags
        ));
    }

    private long getSubdirCount(Path inputDir) throws IOException {
        try (Stream<Path> list = Files.list(inputDir)) {
            return list.count();
        }
    }

    private boolean notSelfOrChild(Path path) {
        return !path.getParent().equals(inputDir) && !path.equals(inputDir);
    }

    private void convertOne(Path inputBag) {
        Path bagParent = inputBag.getParent().getFileName();
        if (outputDir.resolve(bagParent).toFile().exists()) {
            throw new IllegalStateException(format("Output directory already exists: {0}", outputDir.resolve(bagParent)));
        }
        try {
            PlaceHolders ph = new PlaceHolders(inputBag);
            if (ph.hasSameFileIds(pseudoFileSources)) {
                createOutputBags(inputBag, ph);
            }
        }
        catch (Exception e) {
            log.error(MessageFormat.format(
                "{0} failed, it may or may not have (incomplete) bags in {1}",
                inputBag.getParent().getFileName(),
                stagingDir
            ), e);
            failedBags++;
        }
    }

    private void createOutputBags(Path inputBagDir, PlaceHolders placeHolders)
        throws IOException, TransformerException, MaliciousPathException, UnparsableVersionException, UnsupportedAlgorithmException,
        InvalidBagitFileFormatException, NoSuchAlgorithmException, ParserConfigurationException, SAXException {
        SpringfieldFiles springfieldFiles = new SpringfieldFiles(inputBagDir, pseudoFileSources);

        String inputBagParentName = inputBagDir.getParent().getFileName().toString();
        Path outputBagRevision1 = stagingDir.resolve(inputBagParentName).resolve(inputBagDir.getFileName());
        Path outputBagRevision2 = stagingDir.resolve(UUID.randomUUID().toString()).resolve(UUID.randomUUID().toString());

        log.info("Creating revision 1: {} ### {}", inputBagParentName, outputBagRevision1.getParent().getFileName());
        copyDirectory(inputBagDir.toFile(), outputBagRevision1.toFile());
        new FileRemover(outputBagRevision1).removeFiles(new NoneNoneAndPlaceHolderFilter(placeHolders));

        log.info("Creating revision 2: {} ### {}", inputBagParentName, outputBagRevision2.getParent().getFileName());
        copyDirectory(outputBagRevision1.toFile(), outputBagRevision2.toFile());

        if (springfieldFiles.hasFilesToAdd()) {
            springfieldFiles.checkFilesXmlContainsAllSpringfieldFileIdsFromSources(outputBagRevision2);
            springfieldFiles.addFiles(placeHolders, outputBagRevision2, outputBagRevision1);
        }

        // Move the bags to the output directory
        moveFromStagingToOutputDir(outputBagRevision1);
        createdBags++;
        if (springfieldFiles.hasFilesToAdd()) {
            moveFromStagingToOutputDir(outputBagRevision2);
            createdBags++;
        }
        processed++;
        if (!keepInput) {
            FileUtils.deleteDirectory(inputBagDir.getParent().toFile());
        }
    }

    private void moveFromStagingToOutputDir(Path bagDir) throws IOException {
        Path source = bagDir.getParent();
        Path destination = outputDir.resolve(source.getFileName());
        FileUtils.moveDirectory(source.toFile(), destination.toFile());
    }
}
