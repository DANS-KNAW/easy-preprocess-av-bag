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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.readAllLines;
import static java.nio.file.Files.writeString;
import static java.text.MessageFormat.format;
import static nl.knaw.dans.avbag.TestUtils.captureLog;
import static nl.knaw.dans.avbag.TestUtils.captureStdout;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.apache.commons.io.file.PathUtils.touch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class IntegrationTest extends AbstractTestWithTestDir {
    private final Path integration = Path.of("src/test/resources/integration");
    private final Path inputBags = integration.resolve("input-bags");
    private final Path mutableInput = testDir.resolve("input-bags");
    private final Path convertedBags = testDir.resolve("converted-bags");
    private final Path stagedBags = testDir.resolve("staged-bags");

    private static final Path reportDir = Path.of("target/test")
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
            writeString(reportDir.resolve(testName).resolve("events.log"), result);
        }
    }

    @Test
    public void complains_about_not_existing_staging() throws Exception {
        FileUtils.copyDirectory(inputBags.toFile(), mutableInput.toFile());
        deleteDirectory(stagedBags.toFile());

        assertThatThrownBy(() -> new AVConverter(mutableInput, convertedBags, stagedBags, getPseudoFileSources()).convertAll())
            .isInstanceOf(NoSuchFileException.class)
            .hasMessage("target/test/IntegrationTest/staged-bags");
    }

    @Test
    public void complains_about_content_in_staging() throws Exception {
        FileUtils.copyDirectory(inputBags.toFile(), mutableInput.toFile());
        touch(stagedBags.resolve("some-file"));

        assertThatThrownBy(() -> new AVConverter(mutableInput, convertedBags, stagedBags, getPseudoFileSources()).convertAll())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("The staging directory is not empty. Please empty the directory and try again.");
    }

    @Test
    public void complains_about_already_converted_bag() throws Exception {
        FileUtils.copyDirectory(inputBags.toFile(), mutableInput.toFile());
        String uuid = "7bf09491-54b4-436e-7f59-1027f54cbb0c";
        touch(convertedBags.resolve(uuid));

        new AVConverter(mutableInput, convertedBags, stagedBags, getPseudoFileSources()).convertAll();

        assertThat(loggedEvents.list.stream().map(ILoggingEvent::getFormattedMessage).toList())
            .contains(format("Skipped {0}, it exists in {1}", uuid, convertedBags));
    }

    @Test
    public void is_happy() throws Exception {
        FileUtils.copyDirectory(inputBags.toFile(), mutableInput.toFile());

        new AVConverter(mutableInput, convertedBags, stagedBags, getPseudoFileSources()).convertAll();

        assertThat(mutableInput).isEmptyDirectory();
        assertThat(stagedBags).isEmptyDirectory();

        // all manifest-sha1.txt files should be unique
        ArrayList<Object> manifests = new ArrayList<>();
        collectManifests(manifests, inputBags);
        collectManifests(manifests, convertedBags);
        assertThat(new HashSet<>(manifests))
            .containsExactlyInAnyOrderElementsOf(manifests);
    }

    @Test
    public void creates_an_empty_second_bag_with_all_files_none_none() throws Exception {
        String bagParent = "7bf09491-54b4-436e-7f59-1027f54cbb0c";

        FileUtils.copyDirectory(
            integration.resolve("input-bags").resolve(bagParent).toFile(),
            mutableInput.resolve(bagParent).toFile()
        );

        // removing all rights elements from files.xml defaults to None/None
        Path filesXmlFile = mutableInput.resolve(bagParent + "/a5ad806e-d5c4-45e6-b434-f42324d4e097/metadata/files.xml");
        List<String> xmlLines = readAllLines(filesXmlFile).stream()
            .filter(line -> !line.contains("ToRights"))
            .toList();
        writeString(filesXmlFile, String.join("\n", xmlLines));

        new AVConverter(mutableInput, convertedBags, stagedBags, getPseudoFileSources()).convertAll();

        List<String> logLines = loggedEvents.list.stream().map(ILoggingEvent::getFormattedMessage).toList();

        // third bag aborted
        ILoggingEvent last = loggedEvents.list.get(logLines.size() - 1);
        assertThat(last.getFormattedMessage()).endsWith(bagParent + " failed, it may or may not have (incomplete) bags in target/test/IntegrationTest/staged-bags");
        assertThat(last.getLevel()).isEqualTo(Level.ERROR);
        assertThat(last.getThrowableProxy().getMessage()).endsWith("Not all springfield files in the mapping are present in the second bag");

        String secondBagParent = loggedEvents.list.get(logLines.size() - 2).getFormattedMessage().split("## ")[1];
        Path secondBag = Files.list(stagedBags.resolve(secondBagParent)).toList().get(0);
        assertThat(secondBag.resolve("manifest-sha1.txt"))
            .isEmptyFile();
        assertThat(secondBag.resolve("data"))
            .doesNotExist();
    }

    @Test
    public void reports_failing_bags_because_of_not_expected_exception() throws Exception {

        FileUtils.copyDirectory(
            integration.resolve("input-bags").toFile(),
            mutableInput.toFile()
        );

        // second bag will fail when a none/none without dct:source does not exist

        String bagParent1 = "89e54b08-5f1f-452c-a551-0d35f75a3939";
        Path filesXmlFile1 = mutableInput.resolve(bagParent1 + "/dba86e2b-0665-4324-a401-3f5a24a7a2ab/metadata/files.xml");
        List<String> xmlLines1 = readAllLines(filesXmlFile1).stream()
            .filter(line -> !line.contains("dct:source"))
            .toList();
        writeString(filesXmlFile1, String.join("\n", xmlLines1));

        // second bag will fail when a none/none without dct:source does not exist

        String bagParent2 = "7bf09491-54b4-436e-7f59-1027f54cbb0c";
        FileUtils.delete(mutableInput.resolve(bagParent2 + "/a5ad806e-d5c4-45e6-b434-f42324d4e097/data/original/bijlage Gb interviewer 08.pdf").toFile());
        Path filesXmlFile2 = mutableInput.resolve(bagParent2 + "/a5ad806e-d5c4-45e6-b434-f42324d4e097/metadata/files.xml");
        List<String> xmlLines2 = readAllLines(filesXmlFile2).stream()
            .map(line -> !line.contains("visibleToRights") ? line : "<visibleToRights>NONE</visibleToRights>")
            .toList();
        writeString(filesXmlFile2, String.join("\n", xmlLines2));

        // third bag will fail when visibleToRights is missing for a springfield file

        String bagParent3 = "993ec2ee-b716-45c6-b9d1-7190f98a200a";
        Path filesXmlFile3 = mutableInput.resolve(bagParent3 + "/e50fe0a3-554e-49a4-98f8-f4a32f19def9/metadata/files.xml");
        List<String> xmlLines3 = readAllLines(filesXmlFile3).stream()
            .filter(line -> !line.contains("visibleToRights"))
            .toList();
        writeString(filesXmlFile3, String.join("\n", xmlLines3));

        // the test

        new AVConverter(mutableInput, convertedBags, stagedBags, getPseudoFileSources()).convertAll();

        // assert which bags succeeded

        assertHasLogMessageStartingWith("Finished eaa");
        assertHasLogMessageStartingWith("Finished 54c");
        assertNoLogMessageStartingWith("Finished: " + bagParent1);
        assertNoLogMessageStartingWith("Finished: " + bagParent2);
        assertNoLogMessageStartingWith("Finished " + bagParent3);

        // assert failure on first bag

        assertNoLogMessageStartingWith("Creating revision 1: " + bagParent1);
        assertHasLogMessageStartingWith(format("{0} files in PseudoFileSources but not having <dct:source> and length zero: [easy-file:7728890, easy-file:7728889, easy-file:7728888]", bagParent1));

        // assert failure on second bag

        assertHasLogMessageStartingWith("Creating revision 2: " + bagParent2);
        assertNoLogMessageStartingWith("Creating revision 3: " + bagParent2);
        IThrowableProxy bagEvent2 = getThrowableProxyWithLogMessageEqualTo(format(
            "{0} failed, it may or may not have (incomplete) bags in {1}", bagParent2, stagedBags
        ));
        assertThat(bagEvent2.getClassName()).isEqualTo(IOException.class.getCanonicalName());
        assertThat(bagEvent2.getMessage()).endsWith("interviewer 08.pdf");

        // assert failure on third bag

        assertHasLogMessageStartingWith("Creating revision 3: " + bagParent3);
        IThrowableProxy bagEvent3 = getThrowableProxyWithLogMessageEqualTo(format(
            "{0} failed, it may or may not have (incomplete) bags in {1}", bagParent3, stagedBags
        ));
        assertThat(bagEvent3.getMessage()).isEqualTo("""
            Cannot invoke "org.w3c.dom.Element.getTagName()" because "oldRights" is null"""
        );
    }

    private IThrowableProxy getThrowableProxyWithLogMessageEqualTo(String msg) {
        return loggedEvents.list.stream().filter(e ->
                e.getFormattedMessage().equals(msg)
            ).map(ILoggingEvent::getThrowableProxy)
            .findFirst().orElseThrow(() -> new AssertionError("no message found equal to: " + msg));
    }

    private void assertHasLogMessageStartingWith(String msg) {
        assertThat(loggedEvents.list.stream().map(ILoggingEvent::getFormattedMessage))
            .anyMatch(s -> s.startsWith(msg));
    }

    private void assertNoLogMessageStartingWith(String msg) {
        boolean hasMessage = loggedEvents.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .anyMatch(s -> s.startsWith(msg));

        if (hasMessage) {
            throw new AssertionError("Should not have found a log message found starting with: " + msg);
        }
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
