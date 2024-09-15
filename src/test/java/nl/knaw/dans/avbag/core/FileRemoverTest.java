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

import nl.knaw.dans.avbag.AbstractTestWithTestDir;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.createDirectories;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FileRemoverTest extends AbstractTestWithTestDir {

    @Test
    public void should_throw_because_a_file_to_delete_does_not_exist() throws Exception {
        Path bagDir = testDir.resolve("1234/5678");
        Path filesXml = bagDir.resolve("metadata/files.xml");
        createDirectories(filesXml.getParent());
        Files.write(filesXml, String.join("\n", String.join("\n", Arrays.asList(
            "<?xml version='1.0' encoding='UTF-8'?>",
            "<files xsi:schemaLocation='http://easy.dans.knaw.nl/schemas/bag/metadata/files/ http://easy.dans.knaw.nl/schemas/bag/metadata/files/files.xsd'",
            "       xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'",
            "       xmlns='http://easy.dans.knaw.nl/schemas/bag/metadata/files/'",
            "       xmlns:dct='http://purl.org/dc/terms/'>",
            "    <file filepath='data/some.txt'>",
            "        <dct:identifier>easy-file:123</dct:identifier>",
            "        <accessibleToRights>NONE</accessibleToRights>",
            "        <visibleToRights>NONE</visibleToRights>",
            "    </file>",
            "</files>"
        ))).getBytes(UTF_8));

        FileRemover noneNoneFiles = new FileRemover(bagDir);
        Document filesXmlDoc = XmlUtil.readXml(filesXml);

        assertThatThrownBy(() -> noneNoneFiles
            .removeFiles(filesXmlDoc, new FilesToBeRemovedFilter(Arrays.asList()))
        ).isInstanceOf(IOException.class)
            .hasMessage("1234: Could not delete %s/data/some.txt", bagDir);
    }

    @Test
    public void should_return_removed_files() throws Exception {
        Path bagDir = testDir.resolve("1234/5678");
        Path filesXml = bagDir.resolve("metadata/files.xml");
        createDirectories(filesXml.getParent());
        createDirectories(bagDir.resolve("data"));
        Files.createFile(bagDir.resolve("data/some.txt"));
        Files.createFile(bagDir.resolve("data/some2.txt"));

        Files.write(filesXml, String.join("\n", String.join("\n", Arrays.asList(
            "<?xml version='1.0' encoding='UTF-8'?>",
            "<files xsi:schemaLocation='http://easy.dans.knaw.nl/schemas/bag/metadata/files/ http://easy.dans.knaw.nl/schemas/bag/metadata/files/files.xsd'",
            "       xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'",
            "       xmlns='http://easy.dans.knaw.nl/schemas/bag/metadata/files/'",
            "       xmlns:dct='http://purl.org/dc/terms/'>",
            "    <file filepath='data/some.txt'>",
            "        <dct:identifier>easy-file:123</dct:identifier>",
            "        <accessibleToRights>NONE</accessibleToRights>",
            "        <visibleToRights>NONE</visibleToRights>",
            "    </file>",
            "    <file filepath='data/some2.txt'>",
            "        <dct:identifier>easy-file:123</dct:identifier>",
            "    </file>",
            "    <file filepath='data/another.txt'>",
            "        <dct:identifier>easy-file:123</dct:identifier>",
            "        <accessibleToRights>ANONYMOUS</accessibleToRights>",
            "        <visibleToRights>NONE</visibleToRights>",
            "    </file>",
            "    <file filepath='data/a-third.txt'>",
            "        <dct:identifier>easy-file:123</dct:identifier>",
            "        <accessibleToRights>NONE</accessibleToRights>",
            "        <visibleToRights>ANONYMOUS</visibleToRights>",
            "    </file>",
            "</files>"
        ))).getBytes(UTF_8));

        FileRemover noneNoneFiles = new FileRemover(bagDir);
        Document filesXmlDoc = XmlUtil.readXml(filesXml);

        assertThat(noneNoneFiles.removeFiles(filesXmlDoc, new FilesToBeRemovedFilter(Arrays.asList())))
            .containsExactlyInAnyOrderElementsOf(Arrays.asList(
                Paths.get("data/some.txt"),
                Paths.get("data/some2.txt")
            ));
    }

    @Test
    public void empty_files_xml_should_return_empty_list() throws Exception {
        Path bagDir = testDir.resolve("1234/5678");
        Path filesXml = bagDir.resolve("metadata/files.xml");
        createDirectories(filesXml.getParent());
        Files.write(filesXml, String.join("\n", String.join("\n", Arrays.asList(
            "<?xml version='1.0' encoding='UTF-8'?>",
            "<files xsi:schemaLocation='http://easy.dans.knaw.nl/schemas/bag/metadata/files/ http://easy.dans.knaw.nl/schemas/bag/metadata/files/files.xsd'",
            "       xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'",
            "       xmlns='http://easy.dans.knaw.nl/schemas/bag/metadata/files/'",
            "       xmlns:dct='http://purl.org/dc/terms/'>",
            "</files>"
        ))).getBytes(UTF_8));

        FileRemover noneNoneFiles = new FileRemover(bagDir);
        Document filesXmlDoc = XmlUtil.readXml(filesXml);

        assertThat(noneNoneFiles.removeFiles(filesXmlDoc, new FilesToBeRemovedFilter(Arrays.asList())))
            .isEmpty();
    }
}
