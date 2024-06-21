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
import nl.knaw.dans.avbag.TestUtils;
import nl.knaw.dans.avbag.config.PseudoFileSourcesConfig;
import org.assertj.core.api.AbstractThrowableAssert;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.writeString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

public class PseudoFileSourcesTest extends AbstractTestWithTestDir {

    @Test
    public void should_abort_when_dirs_do_not_exist() {
        var pseudoFileSources = new PseudoFileSourcesConfig(
            testDir.resolve("darkArchiveDir"),
            testDir.resolve("springfieldDir"),
            testDir.resolve("not-existing-mapping.csv"));
        assertThatThrownBy(() ->
            new PseudoFileSources(pseudoFileSources)
        ).isInstanceOf(IOException.class)
            .hasMessage(
                "Not existing or not a directory: [target/test/PseudoFileSourcesTest/darkArchiveDir, target/test/PseudoFileSourcesTest/springfieldDir]");
    }

    @Test
    public void should_abort_when_csv_does_not_exist() throws IOException {
        var pseudoFileSources = new PseudoFileSourcesConfig(
            createDirectories(testDir.resolve("darkArchiveDir")),
            createDirectories(testDir.resolve("springfieldDir")),
            testDir.resolve("mapping.csv")
        );
        assertThatThrownBy(() ->
            new PseudoFileSources(pseudoFileSources)
        ).isInstanceOf(IOException.class)
            .hasMessage("Does not exist or is not a file: target/test/PseudoFileSourcesTest/mapping.csv");
    }

    @Test
    public void should_abort_when_csv_is_a_dir() throws IOException {
        var pseudoFileSources = new PseudoFileSourcesConfig(
            createDirectories(testDir.resolve("darkArchiveDir")),
            createDirectories(testDir.resolve("springfieldDir")),
            Path.of("src/test/resources/integration")
        );
        AbstractThrowableAssert<?, ? extends Throwable> abstractThrowableAssert = assertThatThrownBy(() ->
            new PseudoFileSources(pseudoFileSources)
        );
        abstractThrowableAssert.isInstanceOf(IOException.class)
            .hasMessageStartingWith("Does not exist or is not a file: src/test/resources/integration");
    }

    @Test
    public void should_abort_when_files_in_csv_do_not_exist() throws IOException {
        var pseudoFileSources = new PseudoFileSourcesConfig(
            createDirectories(testDir.resolve("darkArchiveDir")),
            createDirectories(testDir.resolve("springfieldDir")),
            Path.of("src/test/resources/integration/mapping.csv")
        );
        assertThatThrownBy(() ->
            new PseudoFileSources(pseudoFileSources)
        ).isInstanceOf(IOException.class)
            .hasMessageStartingWith("Not existing files: [target/test/PseudoFileSourcesTest/springfieldDir/domain/dans/user/nini/video/12/rawvideo/2/NH173.mp4")
            .hasMessageEndingWith("darkArchiveDir/993ec2ee-b716-45c6-b9d1-7190f98a200a/bag/data/JKKV_JBohnen_IV-verklaring_schenkingsovereenkomst_NIOD.pdf]");
    }

    @Test
    public void should_warn_empty_av_column() throws IOException {
        var csv = testDir.resolve("mapping.csv");
        var pseudoFileSourcesConfig = new PseudoFileSourcesConfig(
            Path.of("src/test/resources/integration/av-dir"),
            Path.of("src/test/resources/integration/springfield-dir"),
            csv
        );
        createDirectories(testDir);
        writeString(csv, """
            easy_file_id,dataset_id,path_in_AV_dir,path_in_springfield_dir
            easy-file:7296382,easy-dataset:112582,,eaa33307-4795-40a3-9051-e7d91a21838e/bag/data/ICA_DeJager_KroniekvaneenBazenbondje_Interview_Peter_Essenberg_1.pdf,""");

        TestUtils.captureStdout();
        var log = TestUtils.captureLog(Level.INFO, "nl.knaw.dans.avbag");

        assertThatThrownBy(() -> new PseudoFileSources(pseudoFileSourcesConfig))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("1 records have missing values. See warnings.");

        assertThat(log.list.get(0).getFormattedMessage())
            .startsWith("No value in column path_in_AV_dir and/or easy_file_id: CSVRecord [comment='null', recordNumber=1, values=[easy-file:7296382, easy-dataset:112582, , eaa33307");
    }

    @Test
    public void should_warn_empty_file_id_column() throws IOException {
        var csv = testDir.resolve("mapping.csv");
        var pseudoFileSources = new PseudoFileSourcesConfig(
            Path.of("src/test/resources/integration/av-dir"),
            Path.of("src/test/resources/integration/springfield-dir"),
            csv);
        createDirectories(testDir);
        writeString(csv, """
            easy_file_id,dataset_id,path_in_AV_dir,path_in_springfield_dir
            ,easy-dataset:112582,eaa33307-4795-40a3-9051-e7d91a21838e/bag/data/ICA_DeJager_KroniekvaneenBazenbondje_Interview_Peter_Essenberg_1.pdf,""");

        TestUtils.captureStdout();
        var log = TestUtils.captureLog(Level.INFO, "nl.knaw.dans.avbag");

        assertThatThrownBy(() -> new PseudoFileSources(pseudoFileSources))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("1 records have missing values. See warnings.");

        assertThat(log.list.get(0).getFormattedMessage())
            .startsWith("No value in column path_in_AV_dir and/or easy_file_id: CSVRecord [comment='null', recordNumber=1, values=[, easy-dataset:112582, eaa33307");
    }

    @Test
    public void should_not_throw() throws IOException {
        var springfieldDir = "src/test/resources/integration/springfield-dir";
        var pseudoFileSources = new PseudoFileSourcesConfig(
            Path.of("src/test/resources/integration/av-dir"),
            Path.of(springfieldDir),
            Path.of("src/test/resources/integration/mapping.csv")
        );

        var sources = new PseudoFileSources(pseudoFileSources);

        assertThat(sources.getDarkArchiveFiles("993ec2ee-b716-45c6-b9d1-7190f98a200a").keySet())
            .containsExactlyInAnyOrderElementsOf(Set.of(
                "easy-file:8322137", "easy-file:8322136", "easy-file:8322141", "easy-file:8322138"
            ));
        assertThat(sources.getSpringFieldFiles("993ec2ee-b716-45c6-b9d1-7190f98a200a").keySet())
            .containsExactlyInAnyOrderElementsOf(Set.of(
                "easy-file:8322141"
            ));
        assertThat(sources.getSpringFieldFiles("993ec2ee-b716-45c6-b9d1-7190f98a200a").values())
            .containsExactlyInAnyOrderElementsOf(Set.of(
                Path.of(springfieldDir + "/domain/dans/user/NIOD/video/148/rawvideo/2/JKKV_2007_Eindpunt_Sobibor_SCHELVIS.mp4")
            ));
    }
}
