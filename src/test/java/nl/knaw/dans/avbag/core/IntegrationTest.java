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
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import nl.knaw.dans.avbag.AbstractTestWithTestDir;
import nl.knaw.dans.avbag.config.PseudoFileSourcesConfig;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.w3c.dom.Document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.readAllLines;
import static java.text.MessageFormat.format;
import static nl.knaw.dans.avbag.TestUtils.captureLog;
import static nl.knaw.dans.avbag.TestUtils.captureStdout;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class IntegrationTest extends AbstractTestWithTestDir {

    private final Path integration = Paths.get("src/test/resources/integration");
    private final Path inputBags = integration.resolve("input-bags");
    private final Path mutableInput = testDir.resolve("input-bags");
    private final Path convertedBags = testDir.resolve("converted-bags");
    private final Path stagedBags = testDir.resolve("staged-bags");

    private static final Path reportDir = Paths.get("target/test")
        .resolve(IntegrationTest.class.getSimpleName() + "_reports");

    private ListAppender<ILoggingEvent> loggedEvents;
    private ByteArrayOutputStream stdout;

    @BeforeAll
    public static void beforeAll() throws IOException {
        deleteDirectory(reportDir.toFile());
        createDirectories(reportDir);
    }

    @BeforeEach
    public void setup() throws Exception {
        super.setUp();

        createDirectories(mutableInput);
        createDirectories(convertedBags);
        createDirectories(stagedBags);
    }

    @BeforeEach
    public void prepareReport() {
        stdout = captureStdout();
        loggedEvents = captureLog(Level.INFO, "nl.knaw.dans.avbag");
    }

    @AfterEach
    public void addToReport(TestInfo testInfo) throws Exception {
        String testName = testInfo.getDisplayName().replace("()", "");
        try (Stream<Path> paths = Files.walk(testDir, 5)) {
            Iterator<Path> iterator = paths.filter(path ->
                path.toString().endsWith("metadata/files.xml") || path.toString().endsWith("manifest-sha1.txt")
            ).iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                Path relativePath = testDir.relativize(path);
                Path backupPath = reportDir.resolve(testName).resolve(relativePath);
                Files.createDirectories(backupPath.getParent());
                Files.copy(path, backupPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        String logged = stdout.toString();
        if (!logged.isEmpty()) {
            String[] lines = logged.split("\n");
            String filteredValue = "at org.junit";
            String result = Arrays.stream(lines).reduce((prev, curr) ->
                    curr.contains(filteredValue)
                        ? prev + "... "
                        : prev + "\n" + curr
                )
                .orElse("    .junit");
            Files.write(reportDir.resolve(testName).resolve("events.log"), result.getBytes(UTF_8));
        }
    }

    @Test
    public void should_complain_about_non_existent_staging_dir() throws Exception {
        FileUtils.copyDirectory(inputBags.toFile(), mutableInput.toFile());
        deleteDirectory(stagedBags.toFile());

        assertThatThrownBy(() -> new AVConverter(mutableInput, convertedBags, stagedBags, getPseudoFileSources()).convertAll())
            .isInstanceOf(NoSuchFileException.class)
            .hasMessage("target/test/IntegrationTest/staged-bags");
    }

    @Test
    public void should_complain_about_content_in_staging_dir() throws Exception {
        FileUtils.copyDirectory(inputBags.toFile(), mutableInput.toFile());
        Files.createFile(stagedBags.resolve("some-file"));

        assertThatThrownBy(() -> new AVConverter(mutableInput, convertedBags, stagedBags, getPseudoFileSources()).convertAll())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("The staging directory is not empty. Please empty the directory and try again.");
    }

    @Test
    public void should_throw_if_bag_was_already_converted() throws Exception {
        FileUtils.copyDirectory(inputBags.toFile(), mutableInput.toFile());
        String uuid = "7bf09491-54b4-436e-7f59-1027f54cbb0c";
        Files.createFile(convertedBags.resolve(uuid));

        assertThatThrownBy(() -> new AVConverter(mutableInput, convertedBags, stagedBags, getPseudoFileSources()).convertAll())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Output directory already exists: %s/%s", convertedBags, uuid);
    }

    @Test
    public void should_create_two_bags_per_input_bag() throws Exception {
        FileUtils.copyDirectory(inputBags.toFile(), mutableInput.toFile());

        ByteArrayOutputStream stdout = captureStdout();
        new AVConverter(mutableInput, convertedBags, stagedBags, getPseudoFileSources()).convertAll();

        assertThat(mutableInput).isEmptyDirectory();
        assertThat(stagedBags).isEmptyDirectory();

        // all manifest-sha1.txt files should be unique
        ArrayList<Object> manifests = new ArrayList<>();
        collectManifests(manifests, inputBags);
        collectManifests(manifests, convertedBags);
        assertThat(new HashSet<>(manifests))
            .containsExactlyInAnyOrderElementsOf(manifests);

        int numberOfInputBags = 5;
        int numberOfOutputBags = numberOfInputBags * 2;
        assertThat(stdout.toString()).contains(String.format("processed=%d, failed=0, created=%d", numberOfInputBags, numberOfOutputBags));
    }

    @Test
    public void should_not_create_springfield_bags() throws Exception {
        FileUtils.copyDirectory(inputBags.toFile(), mutableInput.toFile());

        List<String> lines = readAllLines(integration.resolve("sources.csv")).stream()
            .map(line -> line.replaceAll("domain/.*", ""))
            .collect(Collectors.toList());
        Path csv = testDir.resolve("sources.csv");
        Files.write(csv, String.join("\n", lines).getBytes(UTF_8));

        PseudoFileSources pseudoFileSources = new PseudoFileSources(new PseudoFileSourcesConfig(
            integration.resolve("darkarchive"),
            integration.resolve("springfield"),
            csv
        ));

        new AVConverter(mutableInput, convertedBags, stagedBags, pseudoFileSources).convertAll();

        assertThat(stdout.toString()).contains("processed=5, failed=0, created=10");
    }

    @Test
    public void should_fail_when_all_files_are_none_none() throws Exception {
        String bagParent = "7bf09491-54b4-436e-7f59-1027f54cbb0c";

        FileUtils.copyDirectory(
            integration.resolve("input-bags").resolve(bagParent).toFile(),
            mutableInput.resolve(bagParent).toFile()
        );

        Path bagDir = mutableInput.resolve(bagParent).resolve("a5ad806e-d5c4-45e6-b434-f42324d4e097");
        Path filesXmlFile = bagDir.resolve("metadata/files.xml");

        Document filesXml = XmlUtil.readXml(filesXmlFile);
        XmlUtil.replaceElementTextContent(filesXml, "accessibleToRights", "NONE");
        XmlUtil.replaceElementTextContent(filesXml, "visibleToRights", "NONE");
        XmlUtil.writeFilesXml(bagDir, filesXml);

        new AVConverter(mutableInput, convertedBags, stagedBags, getPseudoFileSources()).convertAll();

        assertThat(loggedEvents.list)
            .anyMatch(event -> event.getFormattedMessage().contains(String.format("%s failed, it may or may not have (incomplete) bags in target/test/IntegrationTest/staged-bags", bagParent)));

        assertThat(stdout.toString()).contains("processed=0, failed=1, created=0");
    }

    private PseudoFileSources getPseudoFileSources() throws IOException {
        return new PseudoFileSources(new PseudoFileSourcesConfig(
            integration.resolve("darkarchive"),
            integration.resolve("springfield"),
            integration.resolve("sources.csv")
        ));
    }

    private void collectManifests(ArrayList<Object> manifests, Path convertedBags) throws IOException {
        try (Stream<Path> files = Files.walk(convertedBags, 3)) {
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
