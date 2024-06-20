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
import java.util.HashSet;
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
        stdout = captureStdout();
        loggedEvents = captureLog(Level.INFO, "nl.knaw.dans.avbag");
    }

    @AfterEach
    public void persist(TestInfo testInfo) throws Exception {
        var testName = testInfo.getDisplayName().replace("()", "");
        try (Stream<Path> paths = Files.walk(testDir, 5)) {
            var iterator = paths.filter(path ->
                path.toString().endsWith("metadata/files.xml")
            ).iterator();
            while (iterator.hasNext()) {
                var path = iterator.next();
                var relativePath = testDir.relativize(path);
                var backupPath = reportDir.resolve(testName).resolve(relativePath);
                Files.createDirectories(backupPath.getParent());
                Files.copy(path, backupPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        var logged = stdout.toString();
        if (!loggedEvents.list.isEmpty()) {
            writeString(reportDir.resolve(testName).resolve("events.log"), logged);
        }
    }

    @Test
    public void should_complain_about_not_existing_staging() throws Exception {
        FileUtils.copyDirectory(inputBags.toFile(), mutableInput.toFile());
        deleteDirectory(stagedBags.toFile());

        assertThatThrownBy(() -> new AVConverter(mutableInput, convertedBags, stagedBags, getPseudoFileSources()).convertAll())
            .isInstanceOf(NoSuchFileException.class)
            .hasMessage("target/test/IntegrationTest/staged-bags");
    }

    @Test
    public void should_complain_about_content_in_staging() throws Exception {
        FileUtils.copyDirectory(inputBags.toFile(), mutableInput.toFile());
        touch(stagedBags.resolve("some-file"));

        assertThatThrownBy(() -> new AVConverter(mutableInput, convertedBags, stagedBags, getPseudoFileSources()).convertAll())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("The staging directory is not empty. Please empty the directory and try again.");
    }

    @Test
    public void should_complain_about_already_converted_bag() throws Exception {
        FileUtils.copyDirectory(inputBags.toFile(), mutableInput.toFile());
        var uuid = "7bf09491-54b4-436e-7f59-1027f54cbb0c";
        touch(convertedBags.resolve(uuid));

        new AVConverter(mutableInput, convertedBags, stagedBags, getPseudoFileSources()).convertAll();

        assertThat(loggedEvents.list.stream().map(ILoggingEvent::getFormattedMessage).toList())
            .contains(format("Skipped {0}, it exists in {1}", uuid, convertedBags));
    }

    @Test
    public void should_be_happy() throws Exception {
        FileUtils.copyDirectory(inputBags.toFile(), mutableInput.toFile());

        new AVConverter(mutableInput, convertedBags, stagedBags, getPseudoFileSources()).convertAll();

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
    public void should_have_an_empty_second_bag_with_all_files_none_none() throws Exception {
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

        new AVConverter(mutableInput, convertedBags, stagedBags, getPseudoFileSources()).convertAll();

        var logLines = loggedEvents.list.stream().map(ILoggingEvent::getFormattedMessage).toList();

        // no third bag logged
        var last = loggedEvents.list.get(logLines.size() - 1);
        assertThat(last.getFormattedMessage()).endsWith("## ");
        assertThat(last.getLevel()).isEqualTo(Level.INFO);

        var warning = loggedEvents.list.get(logLines.size() - 2);
        assertThat(warning.getMessage()).endsWith("Not all springfield files in the mapping are present in the second bag");
        assertThat(warning.getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    public void should_report_failing_bags_because_of_not_expected_exception() throws Exception {

        FileUtils.copyDirectory(
            integration.resolve("input-bags").toFile(),
            mutableInput.toFile()
        );

        // TODO find a none/none without dct:source for a failure on a second bag
        var firstBagParent = "eaa33307-4795-40a3-9051-e7d91a21838e";
        FileUtils.delete(mutableInput.resolve(firstBagParent + "/f0b85307-268a-4238-a813-b361ea93feb1/data/ICA_DeJager_KroniekvaneenBazenbondje_Interview_Peter_Essenberg_1.pdf").toFile());

        // third bag fails when visibleToRights is missing for a springfield file
        var thirdBagParent = "7bf09491-54b4-436e-7f59-1027f54cbb0c";
        var filesXmlFile = mutableInput.resolve(thirdBagParent + "/a5ad806e-d5c4-45e6-b434-f42324d4e097/metadata/files.xml");
        var xmlLines = readAllLines(filesXmlFile).stream()
            .filter(line -> !line.contains("visibleToRights"))
            .toList();
        writeString(filesXmlFile, String.join("\n", xmlLines));

        // the test
        new AVConverter(mutableInput, convertedBags, stagedBags, getPseudoFileSources()).convertAll();

        // assertions

        assertNoLogMessageStartingWith("Creating revision 1: " + firstBagParent);
        var secondBagEvent = getThrowableProxyWithLogMessageEqualTo(format(
            "{0} failed, it may or may not have (incomplete) bags in {1}", firstBagParent, stagedBags
        ));
        assertThat(secondBagEvent.getClassName()).isEqualTo(NoSuchFileException.class.getCanonicalName());
        assertThat(secondBagEvent.getMessage()).endsWith("Essenberg_1.pdf");

        assertHasLogMessageStartingWith("Creating revision 3: " + thirdBagParent);
        var thirdBagEvent = getThrowableProxyWithLogMessageEqualTo(format(
            "{0} failed, it may or may not have (incomplete) bags in {1}", thirdBagParent, stagedBags
        ));
        assertThat(thirdBagEvent.getMessage()).isEqualTo("""
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

        if (!hasMessage) {
            throw new AssertionError("No log message found starting with: " + msg);
        }
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
