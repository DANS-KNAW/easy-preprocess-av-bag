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
import ch.qos.logback.classic.spi.ILoggingEvent;
import nl.knaw.dans.avbag.AbstractTestWithTestDir;
import nl.knaw.dans.avbag.config.PseudoFileSourcesConfig;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;

import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.readAllLines;
import static java.nio.file.Files.writeString;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static nl.knaw.dans.avbag.TestUtils.captureLog;
import static nl.knaw.dans.avbag.TestUtils.captureStdout;
import static org.apache.commons.io.file.PathUtils.touch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AVConverterTest extends AbstractTestWithTestDir {
    Path integration = Path.of("src/test/resources/integration");
    Path inputBags = integration.resolve("input-bags");
    Path mutableInput = testDir.resolve("input-bags");
    Path convertedBags = testDir.resolve("converted-bags");
    Path stagedBags = testDir.resolve("staged-bags");

    @BeforeAll
    public static void beforeAll() {
        System.setProperty("logback.configurationFile", "logback-test.xml");
    }

    @BeforeEach
    public void setup() throws Exception {
        super.setUp();
        createDirectories(mutableInput);
        createDirectories(convertedBags);
        createDirectories(stagedBags);
    }

    @AfterEach
    public void persistLog(TestInfo testInfo) throws Exception {
        String testName = testInfo.getDisplayName().replace("()", "");
        var logFile = testDir.resolve("log.txt");
        if(logFile.toFile().exists()) {
            var savedLog = testDir.getParent().resolve(testDir.getFileName() + "_" + testName + ".log");
            Files.move(logFile, savedLog, REPLACE_EXISTING); // TODO remove in beforeEach
        }
    }

    @Test
    public void integration_should_complain_about_not_existing_staging() throws Exception {
        FileUtils.copyDirectory(inputBags.toFile(), mutableInput.toFile());
        FileUtils.deleteDirectory(stagedBags.toFile());

        assertThatThrownBy(() -> new AVConverter(mutableInput, convertedBags, stagedBags, getPseudoFileSources()).convertAll())
            .isInstanceOf(NoSuchFileException.class)
            .hasMessage("target/test/AVConverterTest/staged-bags");
    }

    @Test
    public void integration_should_complain_about_content_in_staging() throws Exception {
        FileUtils.copyDirectory(inputBags.toFile(), mutableInput.toFile());
        touch(stagedBags.resolve("some-file"));

        assertThatThrownBy(() -> new AVConverter(mutableInput, convertedBags, stagedBags, getPseudoFileSources()).convertAll())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("The staging directory is not empty. Please empty the directory and try again.");
    }

    @Test
    public void integration_should_complain_about_already_converted_bag() throws Exception {
        FileUtils.copyDirectory(inputBags.toFile(), mutableInput.toFile());
        var uuid = "7bf09491-54b4-436e-7f59-1027f54cbb0c";
        touch(convertedBags.resolve(uuid));

        captureStdout();
        var log = captureLog(Level.INFO, "nl.knaw.dans.avbag");

        new AVConverter(mutableInput, convertedBags, stagedBags, getPseudoFileSources()).convertAll();

        assertThat(log.list.stream().map(ILoggingEvent::getFormattedMessage).toList())
            .contains(uuid + " skipped, it exists in " + convertedBags);
    }

    @Test
    public void integration_should_be_happy() throws Exception {
        var stdout = captureStdout();
        captureLog(Level.INFO, "nl.knaw.dans.avbag");

        FileUtils.copyDirectory(inputBags.toFile(), mutableInput.toFile());

        new AVConverter(mutableInput, convertedBags, stagedBags, getPseudoFileSources())
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
    public void integration_should_have_an_empty_second_bag_with_all_files_none_none() throws Exception {
        var stdout = captureStdout();
        var log = captureLog(Level.INFO, "nl.knaw.dans.avbag");

        var bagParent = "7bf09491-54b4-436e-7f59-1027f54cbb0c";

        FileUtils.copyDirectory(
            integration.resolve("input-bags").resolve(bagParent).toFile(),
            mutableInput.resolve(bagParent).toFile()
        );

        // removing all rights elements from files.xml defaults to None/None
        var filesXmlFile = mutableInput.resolve(bagParent + "/a5ad806e-d5c4-45e6-b434-f42324d4e097/metadata/files.xml");
        var xmlLines = readAllLines(filesXmlFile).stream()
            .filter(line -> !line.contains("ToRights"))
            .toList();
        writeString(filesXmlFile, String.join("\n", xmlLines));

        new AVConverter(mutableInput, convertedBags, stagedBags, getPseudoFileSources())
            .convertAll();

        writeString(testDir.resolve("log.txt"), stdout.toString());
        var logLines = log.list.stream().map(ILoggingEvent::getFormattedMessage).toList();

        // no third bag logged
        var last = log.list.get(logLines.size() - 1);
        assertThat(last.getFormattedMessage()).endsWith("## ");
        assertThat(last.getLevel()).isEqualTo(Level.INFO);

        var warning = log.list.get(logLines.size() - 2);
        assertThat(warning.getMessage()).endsWith("Not all springfield files in the mapping are present in the second bag");
        assertThat(warning.getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    public void integration_should_fail_because_of_not_expected_exception() throws Exception {
        var stdout = captureStdout();
        var log = captureLog(Level.INFO, "nl.knaw.dans.avbag");

        var bagParent = "7bf09491-54b4-436e-7f59-1027f54cbb0c";

        FileUtils.copyDirectory(
            integration.resolve("input-bags").resolve(bagParent).toFile(),
            mutableInput.resolve(bagParent).toFile()
        );

        // remove all rights elements from one of the files.xml files
        var filesXmlFile = mutableInput.resolve(bagParent + "/a5ad806e-d5c4-45e6-b434-f42324d4e097/metadata/files.xml");
        var xmlLines = readAllLines(filesXmlFile).stream()
            .filter(line -> !line.contains("visibleToRights"))
            .toList();
        writeString(filesXmlFile, String.join("\n", xmlLines));

        new AVConverter(mutableInput, convertedBags, stagedBags, getPseudoFileSources())
            .convertAll();

        writeString(testDir.resolve("log.txt"), stdout.toString());

        // failure report in logging because there is no visibleToRights element for the added springfield file
        var lastLoggingEvent = log.list.get(log.list.size() - 1);
        assertThat(lastLoggingEvent.getMessage())
            .isEqualTo(bagParent + " failed, it may or may not have (incomplete) bags in " + stagedBags);
        assertThat(lastLoggingEvent.getThrowableProxy().getMessage())
            .isEqualTo("""
                Cannot invoke "org.w3c.dom.Element.getTagName()" because "oldRights" is null""");
    }

    private PseudoFileSources getPseudoFileSources() throws IOException {
        var pseudoFileSourcesConfig = new PseudoFileSourcesConfig();
        pseudoFileSourcesConfig.setDarkarchiveDir(integration.resolve("av-dir"));
        pseudoFileSourcesConfig.setSpringfieldDir(integration.resolve("springfield-dir"));
        pseudoFileSourcesConfig.setPath(integration.resolve("mapping.csv"));
        return new PseudoFileSources(pseudoFileSourcesConfig);
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
