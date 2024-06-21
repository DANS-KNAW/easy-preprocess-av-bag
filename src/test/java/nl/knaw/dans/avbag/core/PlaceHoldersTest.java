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
import org.junit.jupiter.api.Test;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Set;

import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.writeString;
import static nl.knaw.dans.avbag.TestUtils.captureLog;
import static nl.knaw.dans.avbag.TestUtils.captureStdout;
import static org.apache.commons.io.file.PathUtils.touch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PlaceHoldersTest extends AbstractTestWithTestDir {

    private final String withPayload = """
        <?xml version='1.0' encoding='UTF-8'?>
        <files xsi:schemaLocation="http://easy.dans.knaw.nl/schemas/bag/metadata/files/ http://easy.dans.knaw.nl/schemas/bag/metadata/files/files.xsd"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xmlns="http://easy.dans.knaw.nl/schemas/bag/metadata/files/"
               xmlns:dct="http://purl.org/dc/terms/">
            <file filepath="data/GV_CaleidoscoopFilm_ingekwartierd_08.pdf">
                <dct:identifier>easy-file:6227174</dct:identifier>
                <dct:source>data/GV_CaleidoscoopFilm_ingekwartierd_08.doc</dct:source>
            </file>
            <file filepath="data/audio-video/GV_Demant_ingekwartierd_08.mp4">
                <dct:identifier>easy-file:5455618</dct:identifier>
                <dct:source>http://legacy-storage.dans.knaw.nl/data/Getuigenverhalen/Caleidoscoop/GV_Demant_Ingekwartierd/GV_Demant_ingekwartierd_08.mp4</dct:source>
            </file>
        </files>""";

    @Test
    void constructor_reports_first_not_existing_file() throws Exception {
        var bagDir = testDir.resolve("7bf09491-54b4-436e-7f59-1027f54cbb0c/bag");
        var filesXml = bagDir.resolve("metadata/files.xml");
        createDirectories(filesXml.getParent());
        writeString(filesXml, withPayload);

        assertThatThrownBy(() -> new PlaceHolders(bagDir, XmlUtil.readXml(filesXml)))
            .isInstanceOf(NoSuchFileException.class)
            .hasMessage("target/test/PlaceHoldersTest/7bf09491-54b4-436e-7f59-1027f54cbb0c/bag/data/GV_CaleidoscoopFilm_ingekwartierd_08.pdf");
    }

    @Test
    void constructor_reports_other_not_existing_file() throws Exception {
        var bagDir = testDir.resolve("7bf09491-54b4-436e-7f59-1027f54cbb0c/bag");
        var filesXml = bagDir.resolve("metadata/files.xml");
        createDirectories(filesXml.getParent());
        writeString(filesXml, withPayload);

        createDirectories(bagDir.resolve("data"));
        touch(bagDir.resolve("data/GV_CaleidoscoopFilm_ingekwartierd_08.pdf"));

        assertThatThrownBy(() -> new PlaceHolders(bagDir, XmlUtil.readXml(filesXml)))
            .isInstanceOf(NoSuchFileException.class)
            .hasMessage("target/test/PlaceHoldersTest/7bf09491-54b4-436e-7f59-1027f54cbb0c/bag/data/audio-video/GV_Demant_ingekwartierd_08.mp4");
    }

    @Test
    void constructor_reports_missing_identifier() throws Exception {
        var bagDir = testDir.resolve("7bf09491-54b4-436e-7f59-1027f54cbb0c/bag");
        var filesXml = bagDir.resolve("metadata/files.xml");
        createDirectories(filesXml.getParent());

        // XML content with missing <dct:identifier>
        String xmlContent = """
            <?xml version='1.0' encoding='UTF-8'?>
            <files xsi:schemaLocation="http://easy.dans.knaw.nl/schemas/bag/metadata/files/ http://easy.dans.knaw.nl/schemas/bag/metadata/files/files.xsd"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xmlns="http://easy.dans.knaw.nl/schemas/bag/metadata/files/"
                   xmlns:dct="http://purl.org/dc/terms/">
                <file filepath="data/GV_CaleidoscoopFilm_ingekwartierd_08.pdf">
                    <dct:source>data/GV_CaleidoscoopFilm_ingekwartierd_08.doc</dct:source>
                </file>
            </files>""";

        writeString(filesXml, xmlContent);

        captureStdout();
        var log = captureLog(Level.ERROR, PlaceHolders.class.getCanonicalName());

        new PlaceHolders(bagDir, XmlUtil.readXml(filesXml));

        var lines = log.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).isEqualTo(
            """
                No <dct:identifier> found: 7bf09491-54b4-436e-7f59-1027f54cbb0c <?xml version="1.0" encoding="UTF-8"?><file filepath="data/GV_CaleidoscoopFilm_ingekwartierd_08.pdf" xmlns="http://easy.dans.knaw.nl/schemas/bag/metadata/files/">
                        <dct:source xmlns:dct="http://purl.org/dc/terms/">data/GV_CaleidoscoopFilm_ingekwartierd_08.doc</dct:source>
                    </file>"""
        );
    }

    @Test
    void contructor_reports_missing_file_attribute() throws Exception {
        var bagDir = testDir.resolve("7bf09491-54b4-436e-7f59-1027f54cbb0c/bag");
        var filesXml = bagDir.resolve("metadata/files.xml");
        createDirectories(filesXml.getParent());

        // XML content with missing filepath attribute
        String xmlContent = """
            <?xml version='1.0' encoding='UTF-8'?>
            <files xsi:schemaLocation="http://easy.dans.knaw.nl/schemas/bag/metadata/files/ http://easy.dans.knaw.nl/schemas/bag/metadata/files/files.xsd"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xmlns="http://easy.dans.knaw.nl/schemas/bag/metadata/files/"
                xmlns:dct="http://purl.org/dc/terms/">
                <file>
                    <dct:identifier>easy-file:6227174</dct:identifier>
                    <dct:source>data/GV_CaleidoscoopFilm_ingekwartierd_08.doc</dct:source>
                </file>
            </files>""";

        writeString(filesXml, xmlContent);

        captureStdout();
        var log = captureLog(Level.ERROR, PlaceHolders.class.getCanonicalName());

        new PlaceHolders(bagDir, XmlUtil.readXml(filesXml));

        var lines = log.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).isEqualTo(
            """
                No filepath attribute found: 7bf09491-54b4-436e-7f59-1027f54cbb0c <?xml version="1.0" encoding="UTF-8"?><file xmlns="http://easy.dans.knaw.nl/schemas/bag/metadata/files/">
                        <dct:identifier xmlns:dct="http://purl.org/dc/terms/">easy-file:6227174</dct:identifier>
                        <dct:source xmlns:dct="http://purl.org/dc/terms/">data/GV_CaleidoscoopFilm_ingekwartierd_08.doc</dct:source>
                    </file>""");
    }

    @Test
    void getDestPath_return_path_attribute() throws Exception {
        var bagDir = testDir.resolve("7bf09491-54b4-436e-7f59-1027f54cbb0c/bag");
        var filesXml = bagDir.resolve("metadata/files.xml");
        createDirectories(filesXml.getParent());
        writeString(filesXml, withPayload);

        createDirectories(bagDir.resolve("data"));
        touch(bagDir.resolve("data/GV_CaleidoscoopFilm_ingekwartierd_08.pdf"));
        touch(bagDir.resolve("data/audio-video/GV_Demant_ingekwartierd_08.mp4"));
        var placeHolders = new PlaceHolders(bagDir, XmlUtil.readXml(filesXml));

        assertThat(placeHolders.getDestPath("easy-file:6227174")).isEqualTo("data/GV_CaleidoscoopFilm_ingekwartierd_08.pdf");
    }

    @Test
    void hasSameFileIds_is_happy() throws Exception {
        var bagDir = testDir.resolve("7bf09491-54b4-436e-7f59-1027f54cbb0c/bag");
        var filesXml = bagDir.resolve("metadata/files.xml");
        createDirectories(filesXml.getParent());
        writeString(filesXml, withPayload);

        createDirectories(bagDir.resolve("data"));
        writeString(bagDir.resolve("data/GV_CaleidoscoopFilm_ingekwartierd_08.pdf"), "content");
        touch(bagDir.resolve("data/audio-video/GV_Demant_ingekwartierd_08.mp4"));
        var placeHolders = new PlaceHolders(bagDir, XmlUtil.readXml(filesXml));

        var pseudoFileSources = new PseudoFileSourcesConfig();
        var springfieldDir = "src/test/resources/integration/springfield-dir";
        pseudoFileSources.setSpringfieldDir(Path.of(springfieldDir));
        pseudoFileSources.setDarkarchiveDir(Path.of("src/test/resources/integration/av-dir"));
        pseudoFileSources.setPath(Path.of("src/test/resources/integration/mapping.csv"));
        var sources = new PseudoFileSources(pseudoFileSources);

        assertTrue(placeHolders.hasSameFileIds(sources));
    }

    @Test
    void hasSameFileIds_logs_file_missing_in_PseudoFileSources() throws Exception {
        var bagDir = testDir.resolve("7bf09491-54b4-436e-7f59-1027f54cbb0c/bag");
        var filesXml = bagDir.resolve("metadata/files.xml");
        createDirectories(filesXml.getParent());
        writeString(filesXml, withPayload);

        createDirectories(bagDir.resolve("data"));
        touch(bagDir.resolve("data/GV_CaleidoscoopFilm_ingekwartierd_08.pdf"));
        touch(bagDir.resolve("data/audio-video/GV_Demant_ingekwartierd_08.mp4"));
        var placeHolders = new PlaceHolders(bagDir, XmlUtil.readXml(filesXml));

        var pseudoFileSources = new PseudoFileSourcesConfig();
        var springfieldDir = "src/test/resources/integration/springfield-dir";
        pseudoFileSources.setSpringfieldDir(Path.of(springfieldDir));
        pseudoFileSources.setDarkarchiveDir(Path.of("src/test/resources/integration/av-dir"));
        pseudoFileSources.setPath(Path.of("src/test/resources/integration/mapping.csv"));
        var sources = new PseudoFileSources(pseudoFileSources);

        captureStdout();
        var log = captureLog(Level.ERROR, PlaceHolders.class.getCanonicalName());

        assertFalse(placeHolders.hasSameFileIds(sources));
        assertThat(log.list.stream().map(ILoggingEvent::getFormattedMessage).toList())
            .containsExactlyInAnyOrderElementsOf(Set.of(
                bagDir.getParent().getFileName() +
                " files having <dct:source> and length zero but not in PseudoFileSources: [easy-file:6227174]"
            ));
    }

    @Test
    void hasSameFileIds_logs_file_missing_in_files_xml() throws Exception {
        var bagDir = testDir.resolve("7bf09491-54b4-436e-7f59-1027f54cbb0c/bag");
        var filesXml = bagDir.resolve("metadata/files.xml");
        createDirectories(filesXml.getParent());
        var withoutPayLoad = """
            <?xml version='1.0' encoding='UTF-8'?>
            <files xsi:schemaLocation="http://easy.dans.knaw.nl/schemas/bag/metadata/files/ http://easy.dans.knaw.nl/schemas/bag/metadata/files/files.xsd"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xmlns="http://easy.dans.knaw.nl/schemas/bag/metadata/files/"
                   xmlns:dct="http://purl.org/dc/terms/">
            </files>""";
        writeString(filesXml, withoutPayLoad);

        createDirectories(bagDir.resolve("data"));
        touch(bagDir.resolve("data/GV_CaleidoscoopFilm_ingekwartierd_08.pdf"));
        touch(bagDir.resolve("data/audio-video/GV_Demant_ingekwartierd_08.mp4"));
        var placeHolders = new PlaceHolders(bagDir, XmlUtil.readXml(filesXml));

        var pseudoFileSources = new PseudoFileSourcesConfig();
        var springfieldDir = "src/test/resources/integration/springfield-dir";
        pseudoFileSources.setSpringfieldDir(Path.of(springfieldDir));
        pseudoFileSources.setDarkarchiveDir(Path.of("src/test/resources/integration/av-dir"));
        pseudoFileSources.setPath(Path.of("src/test/resources/integration/mapping.csv"));
        var sources = new PseudoFileSources(pseudoFileSources);

        captureStdout();
        var log = captureLog(Level.ERROR, PlaceHolders.class.getCanonicalName());

        assertFalse(placeHolders.hasSameFileIds(sources));
        assertThat(log.list.stream().map(ILoggingEvent::getFormattedMessage).toList())
            .containsExactlyInAnyOrderElementsOf(Set.of(
                bagDir.getParent().getFileName() +
                " files in PseudoFileSources but not having <dct:source> and length zero: [easy-file:5455618]"
            ));
    }
}
