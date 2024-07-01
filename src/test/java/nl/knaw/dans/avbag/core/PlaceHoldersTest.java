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
import ch.qos.logback.core.read.ListAppender;
import nl.knaw.dans.avbag.AbstractTestWithTestDir;
import nl.knaw.dans.avbag.config.PseudoFileSourcesConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.createDirectories;
import static nl.knaw.dans.avbag.TestUtils.captureLog;
import static nl.knaw.dans.avbag.TestUtils.captureStdout;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PlaceHoldersTest extends AbstractTestWithTestDir {

    private final byte[] withPayload = String.join("\n", new String[] {
        "<?xml version='1.0' encoding='UTF-8'?>",
        "<files xsi:schemaLocation='http://easy.dans.knaw.nl/schemas/bag/metadata/files/ http://easy.dans.knaw.nl/schemas/bag/metadata/files/files.xsd'",
        "       xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'",
        "       xmlns='http://easy.dans.knaw.nl/schemas/bag/metadata/files/'",
        "       xmlns:dct='http://purl.org/dc/terms/'>",
        "    <file filepath='data/GV_CaleidoscoopFilm_ingekwartierd_08.pdf'>",
        "        <dct:identifier>easy-file:6227174</dct:identifier>",
        "        <dct:source>data/GV_CaleidoscoopFilm_ingekwartierd_08.doc</dct:source>",
        "    </file>",
        "    <file filepath='data/audio-video/GV_Demant_ingekwartierd_08.mp4'>",
        "        <dct:identifier>easy-file:5455618</dct:identifier>",
        "        <dct:source>http://legacy-storage.dans.knaw.nl/data/Getuigenverhalen/Caleidoscoop/GV_Demant_Ingekwartierd/GV_Demant_ingekwartierd_08.mp4</dct:source>",
        "    </file>",
        "</files>"
    }).getBytes(UTF_8);

    @Test
    void constructor_reports_first_not_existing_file() throws Exception {
        Path bagDir = testDir.resolve("7bf09491-54b4-436e-7f59-1027f54cbb0c/bag");
        Path filesXml = bagDir.resolve("metadata/files.xml");
        createDirectories(filesXml.getParent());
        Files.write(filesXml, withPayload);

        assertThatThrownBy(() -> new PlaceHolders(bagDir, XmlUtil.readXml(filesXml)))
            .isInstanceOf(NoSuchFileException.class)
            .hasMessage("target/test/PlaceHoldersTest/7bf09491-54b4-436e-7f59-1027f54cbb0c/bag/data/GV_CaleidoscoopFilm_ingekwartierd_08.pdf");
    }

    @Test
    void constructor_reports_other_not_existing_file() throws Exception {
        Path bagDir = testDir.resolve("7bf09491-54b4-436e-7f59-1027f54cbb0c/bag");
        Path filesXml = bagDir.resolve("metadata/files.xml");
        createDirectories(filesXml.getParent());
        Files.write(filesXml, withPayload);

        createDirectories(bagDir.resolve("data"));
        Files.createFile(bagDir.resolve("data/GV_CaleidoscoopFilm_ingekwartierd_08.pdf"));

        assertThatThrownBy(() -> new PlaceHolders(bagDir, XmlUtil.readXml(filesXml)))
            .isInstanceOf(NoSuchFileException.class)
            .hasMessage("target/test/PlaceHoldersTest/7bf09491-54b4-436e-7f59-1027f54cbb0c/bag/data/audio-video/GV_Demant_ingekwartierd_08.mp4");
    }

    @Test
    void constructor_reports_missing_identifier() throws Exception {
        Path bagDir = testDir.resolve("7bf09491-54b4-436e-7f59-1027f54cbb0c/bag");
        Path filesXml = bagDir.resolve("metadata/files.xml");
        createDirectories(filesXml.getParent());

        // XML content with missing <dct:identifier>

        Files.write(filesXml, String.join("\n", new String[] {
            "<?xml version='1.0' encoding='UTF-8'?>",
            "<files xsi:schemaLocation='http://easy.dans.knaw.nl/schemas/bag/metadata/files/ http://easy.dans.knaw.nl/schemas/bag/metadata/files/files.xsd'",
            "       xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'",
            "       xmlns='http://easy.dans.knaw.nl/schemas/bag/metadata/files/'",
            "       xmlns:dct='http://purl.org/dc/terms/'>",
            "    <file filepath='data/GV_CaleidoscoopFilm_ingekwartierd_08.pdf'>",
            "        <dct:source>data/GV_CaleidoscoopFilm_ingekwartierd_08.doc</dct:source>",
            "    </file>",
            "</files>"
        }).getBytes(UTF_8));

        captureStdout();
        ListAppender<ILoggingEvent> log = captureLog(Level.ERROR, PlaceHolders.class.getCanonicalName());

        new PlaceHolders(bagDir, XmlUtil.readXml(filesXml));

        List<String> lines = log.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .collect(Collectors.toList());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).isEqualTo(String.join("\n", new String[] {
            "No <dct:identifier> found: 7bf09491-54b4-436e-7f59-1027f54cbb0c <?xml version='1.0' encoding='UTF-8'?><file filepath='data/GV_CaleidoscoopFilm_ingekwartierd_08.pdf' xmlns='http://easy.dans.knaw.nl/schemas/bag/metadata/files/'>",
            "        <dct:source xmlns:dct='http://purl.org/dc/terms/'>data/GV_CaleidoscoopFilm_ingekwartierd_08.doc</dct:source>",
            "    </file>"
        }).replaceAll("'", "\""));
    }

    @Test
    void contructor_reports_missing_file_attribute() throws Exception {
        Path bagDir = testDir.resolve("7bf09491-54b4-436e-7f59-1027f54cbb0c/bag");
        Path filesXml = bagDir.resolve("metadata/files.xml");
        createDirectories(filesXml.getParent());

        // XML content with missing filepath attribute
        Files.write(filesXml, String.join("\n", new String[] {
            "<?xml version='1.0' encoding='UTF-8'?>",
            "<files xsi:schemaLocation='http://easy.dans.knaw.nl/schemas/bag/metadata/files/ http://easy.dans.knaw.nl/schemas/bag/metadata/files/files.xsd'",
            "       xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'",
            "       xmlns='http://easy.dans.knaw.nl/schemas/bag/metadata/files/'",
            "       xmlns:dct='http://purl.org/dc/terms/'>",
            "    <file>",
            "        <dct:identifier>easy-file:6227174</dct:identifier>",
            "        <dct:source>data/GV_CaleidoscoopFilm_ingekwartierd_08.doc</dct:source>",
            "    </file>",
            "</files>"
        }).getBytes(UTF_8));

        captureStdout();
        ListAppender<ILoggingEvent> log = captureLog(Level.ERROR, PlaceHolders.class.getCanonicalName());

        new PlaceHolders(bagDir, XmlUtil.readXml(filesXml));

        List<String> lines = log.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .collect(Collectors.toList());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).isEqualTo(String.join("\n", new String[] {
            "No filepath attribute found: 7bf09491-54b4-436e-7f59-1027f54cbb0c <?xml version='1.0' encoding='UTF-8'?><file xmlns='http://easy.dans.knaw.nl/schemas/bag/metadata/files/'>",
            "        <dct:identifier xmlns:dct='http://purl.org/dc/terms/'>easy-file:6227174</dct:identifier>",
            "        <dct:source xmlns:dct='http://purl.org/dc/terms/'>data/GV_CaleidoscoopFilm_ingekwartierd_08.doc</dct:source>",
            "    </file>"
        }).replaceAll("'", "\""));
    }

    @Test
    void getDestPath_return_path_attribute() throws Exception {
        Path bagDir = testDir.resolve("7bf09491-54b4-436e-7f59-1027f54cbb0c/bag");
        Path filesXml = bagDir.resolve("metadata/files.xml");
        createDirectories(filesXml.getParent());
        Files.write(filesXml, withPayload);

        createDirectories(bagDir.resolve("data/audio-video"));
        Files.createFile(bagDir.resolve("data/GV_CaleidoscoopFilm_ingekwartierd_08.pdf"));
        Files.createFile(bagDir.resolve("data/audio-video/GV_Demant_ingekwartierd_08.mp4"));
        PlaceHolders placeHolders = new PlaceHolders(bagDir, XmlUtil.readXml(filesXml));

        assertThat(placeHolders.getDestPath("easy-file:6227174")).isEqualTo("data/GV_CaleidoscoopFilm_ingekwartierd_08.pdf");
    }

    @Test
    void hasSameFileIds_is_happy() throws Exception {
        Path bagDir = testDir.resolve("7bf09491-54b4-436e-7f59-1027f54cbb0c/bag");
        Path filesXml = bagDir.resolve("metadata/files.xml");
        createDirectories(filesXml.getParent());
        Files.write(filesXml, withPayload);

        createDirectories(bagDir.resolve("data/audio-video"));
        Files.write(bagDir.resolve("data/GV_CaleidoscoopFilm_ingekwartierd_08.pdf"), "content".getBytes(UTF_8));
        Files.createFile(bagDir.resolve("data/audio-video/GV_Demant_ingekwartierd_08.mp4"));
        PlaceHolders placeHolders = new PlaceHolders(bagDir, XmlUtil.readXml(filesXml));

        PseudoFileSources sources = new PseudoFileSources(getPseudoFileSourcesConfig());

        assertTrue(placeHolders.hasSameFileIds(sources));
    }

    @Test
    void hasSameFileIds_logs_file_missing_in_PseudoFileSources() throws Exception {
        Path bagDir = testDir.resolve("7bf09491-54b4-436e-7f59-1027f54cbb0c/bag");
        Path filesXml = bagDir.resolve("metadata/files.xml");
        createDirectories(filesXml.getParent());
        Files.write(filesXml, withPayload);

        createDirectories(bagDir.resolve("data/audio-video"));
        Files.createFile(bagDir.resolve("data/GV_CaleidoscoopFilm_ingekwartierd_08.pdf"));
        Files.createFile(bagDir.resolve("data/audio-video/GV_Demant_ingekwartierd_08.mp4"));
        PlaceHolders placeHolders = new PlaceHolders(bagDir, XmlUtil.readXml(filesXml));

        PseudoFileSources sources = new PseudoFileSources(getPseudoFileSourcesConfig());

        captureStdout();
        ListAppender<ILoggingEvent> log = captureLog(Level.ERROR, PlaceHolders.class.getCanonicalName());

        assertFalse(placeHolders.hasSameFileIds(sources));
        List<String> result = log.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .collect(Collectors.toList());
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(
            bagDir.getParent().getFileName() +
            " files having <dct:source> and length zero but not in PseudoFileSources: [easy-file:6227174]"
        );
    }

    @Test
    void hasSameFileIds_logs_file_missing_in_files_xml() throws Exception {
        Path bagDir = testDir.resolve("7bf09491-54b4-436e-7f59-1027f54cbb0c/bag");
        Path filesXml = bagDir.resolve("metadata/files.xml");
        createDirectories(filesXml.getParent());
        Files.write(filesXml, String.join("\n", new String[] {
            "<?xml version='1.0' encoding='UTF-8'?>",
            "<files xsi:schemaLocation='http://easy.dans.knaw.nl/schemas/bag/metadata/files/ http://easy.dans.knaw.nl/schemas/bag/metadata/files/files.xsd'",
            "       xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'",
            "       xmlns='http://easy.dans.knaw.nl/schemas/bag/metadata/files/'",
            "       xmlns:dct='http://purl.org/dc/terms/'>",
            "</files>"
        }).getBytes(UTF_8));

        createDirectories(bagDir.resolve("data/audio-video"));
        Files.createFile(bagDir.resolve("data/GV_CaleidoscoopFilm_ingekwartierd_08.pdf"));
        Files.createFile(bagDir.resolve("data/audio-video/GV_Demant_ingekwartierd_08.mp4"));
        PlaceHolders placeHolders = new PlaceHolders(bagDir, XmlUtil.readXml(filesXml));

        PseudoFileSources sources = new PseudoFileSources(getPseudoFileSourcesConfig());

        captureStdout();
        ListAppender<ILoggingEvent> log = captureLog(Level.ERROR, PlaceHolders.class.getCanonicalName());

        assertFalse(placeHolders.hasSameFileIds(sources));
        List<String> result = log.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList());
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(
            bagDir.getParent().getFileName() +
            " files in PseudoFileSources but not having <dct:source> and length zero: [easy-file:5455618]"
        );
    }

    private static PseudoFileSourcesConfig getPseudoFileSourcesConfig() {
        return new PseudoFileSourcesConfig(
            Paths.get("src/test/resources/integration/darkarchive"),
            Paths.get("src/test/resources/integration/springfield"),
            Paths.get("src/test/resources/integration/sources.csv")
        );
    }
}