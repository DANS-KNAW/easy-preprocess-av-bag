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
import org.junit.jupiter.api.Test;

import java.util.Map;

import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.writeString;
import static nl.knaw.dans.avbag.TestUtils.assumeNotYetFixed;
import static nl.knaw.dans.avbag.TestUtils.captureLog;
import static nl.knaw.dans.avbag.TestUtils.captureStdout;
import static org.apache.commons.io.file.PathUtils.touch;
import static org.assertj.core.api.Assertions.assertThat;

public class SpringfieldFilesTest extends AbstractTestWithTestDir {

    @Test
    void addFiles_should_log_no_file_element_found() throws Exception {
        var bagDir = testDir.resolve("7bf09491-54b4-436e-7f59-1027f54cbb0c/bag");
        var filesXmlFile = bagDir.resolve("metadata/files.xml");
        createDirectories(filesXmlFile.getParent());

        // XML content with missing <dct:identifier>
        String xmlContent = """
            <?xml version='1.0' encoding='UTF-8'?>
            <files xsi:schemaLocation="http://easy.dans.knaw.nl/schemas/bag/metadata/files/ http://easy.dans.knaw.nl/schemas/bag/metadata/files/files.xsd"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xmlns="http://easy.dans.knaw.nl/schemas/bag/metadata/files/"
                   xmlns:dct="http://purl.org/dc/terms/">
                <file filepath="data/some.pdf">
                    <dct:identifier>easy-file:345</dct:identifier>
                </file>
            </files>""";

        writeString(filesXmlFile, xmlContent);

        var path = touch(testDir.resolve("springfield.txt"));

        captureStdout();
        var log = captureLog(Level.ERROR, "nl.knaw.dans.avbag");

        // a springfield file without its ID in files.xml should have been detected by PseudoFileSources
        new SpringfieldFiles(bagDir, XmlUtil.readXml(filesXmlFile))
            .addFiles(Map.of("easy-file:123", path), new PlaceHolders(bagDir, XmlUtil.readXml(filesXmlFile)));

        var lines = log.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).isEqualTo("7bf09491-54b4-436e-7f59-1027f54cbb0c Could not find easy-file:123 in files.xml");
    }

    @Test
    void addFiles_should_report_missing_rights() throws Exception {
        assumeNotYetFixed("Would throw a null pointer exception. What is the difference with the ignored integration test?");
        var bagDir = testDir.resolve("7bf09491-54b4-436e-7f59-1027f54cbb0c/bag");
        var filesXmlFile = bagDir.resolve("metadata/files.xml");
        createDirectories(filesXmlFile.getParent());
        touch(bagDir.resolve("data/some.pdf"));

        // XML content with missing Rights
        String xmlContent = """
            <?xml version='1.0' encoding='UTF-8'?>
            <files xsi:schemaLocation="http://easy.dans.knaw.nl/schemas/bag/metadata/files/ http://easy.dans.knaw.nl/schemas/bag/metadata/files/files.xsd"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xmlns="http://easy.dans.knaw.nl/schemas/bag/metadata/files/"
                   xmlns:dct="http://purl.org/dc/terms/">
                <file filepath="data/some.pdf">
                    <dct:identifier>easy-file:123</dct:identifier>
                    <dct:source>http://legacy-storage.dans.knaw.nl/data/some.pdf</dct:source>
                </file>
            </files>""";

        writeString(filesXmlFile, xmlContent);
        var filesXmlDocument = XmlUtil.readXml(filesXmlFile);

        captureStdout();
        var log = captureLog(Level.ERROR, "nl.knaw.dans.avbag");

        // a springfield file without its ID in files.xml should have been detected by PseudoFileSources
        new SpringfieldFiles(bagDir, filesXmlDocument)
            .addFiles(Map.of("easy-file:123", touch(testDir.resolve("springfield.txt"))), new PlaceHolders(bagDir, filesXmlDocument));

        var lines = log.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).isEqualTo("7bf09491-54b4-436e-7f59-1027f54cbb0c Could not find easy-file:123 in files.xml");
    }
}
