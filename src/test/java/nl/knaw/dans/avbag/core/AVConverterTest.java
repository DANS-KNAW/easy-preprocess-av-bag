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

import ch.qos.logback.classic.Level;
import nl.knaw.dans.avbag.AbstractTestWithTestDir;
import nl.knaw.dans.avbag.config.PseudoFileSourcesConfig;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;

import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.readAllLines;
import static java.nio.file.Files.writeString;
import static nl.knaw.dans.avbag.TestUtils.assumeNotYetFixed;
import static nl.knaw.dans.avbag.TestUtils.captureLog;
import static nl.knaw.dans.avbag.TestUtils.captureStdout;
import static org.assertj.core.api.Assertions.assertThat;

public class AVConverterTest extends AbstractTestWithTestDir {

    @Test
    public void integration_should_be_happy() throws Exception {
        var stdout = captureStdout();
        captureLog(Level.INFO, "nl.knaw.dans.avbag");

        var integration = Path.of("src/test/resources/integration");
        var inputBags = integration.resolve("input-bags");
        var mutableInput = testDir.resolve("input-bags");
        var convertedBags = createDirectories(testDir.resolve("converted-bags"));
        var stagedBags = createDirectories(testDir.resolve("staged-bags"));

        FileUtils.copyDirectory(inputBags.toFile(), mutableInput.toFile());
        var pseudoFileSources = new PseudoFileSourcesConfig();
        pseudoFileSources.setDarkarchiveDir(integration.resolve("av-dir"));
        pseudoFileSources.setSpringfieldDir(integration.resolve("springfield-dir"));
        pseudoFileSources.setPath(integration.resolve("mapping.csv"));

        new AVConverter(mutableInput, convertedBags, stagedBags, new PseudoFileSources(pseudoFileSources))
            .convertAll();

        writeString(testDir.resolve("log.txt"), stdout.toString());
        assertThat(mutableInput).isEmptyDirectory();
        assertThat(stagedBags).isEmptyDirectory();

        // all manifest-sha1.txt files should be unique
        var manifests = new ArrayList<>();
        collectManifests(manifests, inputBags);
        collectManifests(manifests, convertedBags);
        assertThat(new HashSet<>(manifests))
            .containsExactlyInAnyOrderElementsOf(manifests);
    }

    @Test
    public void integration_should_not_create_empty_bags_because_of_missing_rights() throws Exception {
        assumeNotYetFixed("What is the difference with ignored SpringfieldFilesTest?");
        var stdout = captureStdout();
        captureLog(Level.INFO, "nl.knaw.dans.avbag");

        var integration = Path.of("src/test/resources/integration");
        var inputBags = integration.resolve("input-bags");
        var mutableInput = testDir.resolve("input-bags");
        var convertedBags = createDirectories(testDir.resolve("converted-bags"));
        var stagedBags = createDirectories(testDir.resolve("staged-bags"));

        FileUtils.copyDirectory(inputBags.toFile(), mutableInput.toFile());

        // remove all rights elements from one of the files.xml files
        var bagParent = "7bf09491-54b4-436e-7f59-1027f54cbb0c";
        var filesXmlFile = mutableInput.resolve(bagParent + "/a5ad806e-d5c4-45e6-b434-f42324d4e097/metadata/files.xml");
        var xmlLines = readAllLines(filesXmlFile).stream()
            .filter(line -> !line.contains("ToRights"))
            .toList();
        writeString(filesXmlFile, String.join("\n", xmlLines));

        var pseudoFileSources = new PseudoFileSourcesConfig();
        pseudoFileSources.setDarkarchiveDir(integration.resolve("av-dir"));
        pseudoFileSources.setSpringfieldDir(integration.resolve("springfield-dir"));
        pseudoFileSources.setPath(integration.resolve("mapping.csv"));

        new AVConverter(mutableInput, convertedBags, stagedBags, new PseudoFileSources(pseudoFileSources))
            .convertAll();

        writeString(testDir.resolve("log.txt"), stdout.toString());

        var manifests = new ArrayList<>();
        collectManifests(manifests, convertedBags);
        assertThat(new HashSet<>(manifests)).doesNotContain("");
    }

    private void collectManifests(ArrayList<Object> manifests, Path convertedBags) throws IOException {
        try (var files = Files.walk(convertedBags, 3)) {
            files.filter(path -> path.getFileName().toString().equals("manifest-sha1.txt"))
                .forEach(path -> manifests.add(readSorted(path)));
        }
    }

    private String readSorted(Path path) {
        try {
            return Files.readAllLines(path).stream().sorted().reduce("", (a, b) -> a + b + "\n");
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
