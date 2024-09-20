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
import nl.knaw.dans.avbag.TestUtils;
import nl.knaw.dans.avbag.config.PseudoFileSourcesConfig;
import org.assertj.core.api.AbstractThrowableAssert;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.createDirectories;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

public class PseudoFileSourcesTest extends AbstractTestWithTestDir {

    @Test
    public void should_abort_when_configured_dirs_do_not_exist() {
        PseudoFileSourcesConfig pseudoFileSources = new PseudoFileSourcesConfig(
            testDir.resolve("darkArchiveDir"),
            testDir.resolve("springfieldDir"),
            testDir.resolve("not-existing-sources.csv"));
        assertThatThrownBy(() ->
            new PseudoFileSources(pseudoFileSources)
        ).isInstanceOf(IOException.class)
            .hasMessage(
                "Not existing or not a directory: [target/test/PseudoFileSourcesTest/darkArchiveDir, target/test/PseudoFileSourcesTest/springfieldDir]");
    }

    @Test
    public void should_abort_when_csv_does_not_exist() throws IOException {
        PseudoFileSourcesConfig pseudoFileSources = new PseudoFileSourcesConfig(
            createDirectories(testDir.resolve("darkArchiveDir")),
            createDirectories(testDir.resolve("springfieldDir")),
            testDir.resolve("sources.csv")
        );
        assertThatThrownBy(() ->
            new PseudoFileSources(pseudoFileSources)
        ).isInstanceOf(IOException.class)
            .hasMessage("Does not exist or is not a file: target/test/PseudoFileSourcesTest/sources.csv");
    }

    @Test
    public void should_abort_when_csv_is_a_dir() throws IOException {
        PseudoFileSourcesConfig pseudoFileSources = new PseudoFileSourcesConfig(
            createDirectories(testDir.resolve("darkArchiveDir")),
            createDirectories(testDir.resolve("springfieldDir")),
            Paths.get("src/test/resources/integration")
        );
        AbstractThrowableAssert<?, ? extends Throwable> abstractThrowableAssert = assertThatThrownBy(() ->
            new PseudoFileSources(pseudoFileSources)
        );
        abstractThrowableAssert.isInstanceOf(IOException.class)
            .hasMessageStartingWith("Does not exist or is not a file: src/test/resources/integration");
    }

//    @Test
//    public void should_abort_when_files_in_csv_do_not_exist() throws IOException {
//        PseudoFileSourcesConfig pseudoFileSources = new PseudoFileSourcesConfig(
//            createDirectories(Paths.get("darkarchive")),
//            createDirectories(Paths.get("springfield")),
//            Paths.get("src/test/resources/integration/sources.csv")
//        );
//        assertThatThrownBy(() ->
//            new PseudoFileSources(pseudoFileSources)
//        ).isInstanceOf(IOException.class)
//            .hasMessageStartingWith("Not existing files: [springfield/")
//            .hasMessageContaining("] [darkarchive/");
//    }

    @Test
    public void should_abort_when_springfield_files_in_csv_do_not_exist() throws IOException {
        PseudoFileSourcesConfig pseudoFileSources = new PseudoFileSourcesConfig(
            createDirectories(Paths.get("src/test/resources/integration/darkarchive")),
            createDirectories(Paths.get("springfield")),
            Paths.get("src/test/resources/integration/sources.csv")
        );
        assertThatThrownBy(() ->
            new PseudoFileSources(pseudoFileSources)
        ).isInstanceOf(IOException.class)
            .hasMessageStartingWith("Not existing files: [springfield/")
            .hasMessageEndingWith("]");
        // the second list is empty
    }

//    @Test
//    public void should_abort_when_darkarchive_files_in_csv_do_not_exist() throws IOException {
//        PseudoFileSourcesConfig pseudoFileSources = new PseudoFileSourcesConfig(
//            createDirectories(Paths.get("darkarchive")),
//            createDirectories(Paths.get("src/test/resources/integration/springfield")),
//            Paths.get("src/test/resources/integration/sources.csv")
//        );
//        assertThatThrownBy(() ->
//            new PseudoFileSources(pseudoFileSources)
//        ).isInstanceOf(IOException.class)
//            .hasMessageStartingWith("Not existing files: [] [darkarchive")
//            .hasMessageNotContaining("springfield/");
//        // the first list is empty
//    }

    @Test
    public void should_warn_empty_av_column() throws IOException {
        Path csv = testDir.resolve("sources.csv");
        PseudoFileSourcesConfig pseudoFileSourcesConfig = new PseudoFileSourcesConfig(
            Paths.get("src/test/resources/integration/darkarchive"),
            Paths.get("src/test/resources/integration/springfield"),
            csv
        );
        createDirectories(testDir);
        Files.write(csv, String.join("\n", new String[] {
            "easy_file_id,dataset_id,path_in_AV_dir,path_in_springfield_dir",
            "easy-file:7296382,easy-dataset:112582,,eaa33307-4795-40a3-9051-e7d91a21838e/bag/data/ICA_DeJager_KroniekvaneenBazenbondje_Interview_Peter_Essenberg_1.pdf,"
        }).getBytes(UTF_8));

        TestUtils.captureStdout();
        ListAppender<ILoggingEvent> log = TestUtils.captureLog(Level.INFO, "nl.knaw.dans.avbag");

        assertThatThrownBy(() -> new PseudoFileSources(pseudoFileSourcesConfig))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("1 records have missing values. See warnings.");

        assertThat(log.list.get(0).getFormattedMessage())
            .startsWith("No value in column path_in_AV_dir and/or easy_file_id: CSVRecord [comment='null', recordNumber=1, values=[easy-file:7296382, easy-dataset:112582, , eaa33307");
    }

    @Test
    public void should_warn_empty_file_id_column() throws IOException {
        Path csv = testDir.resolve("sources.csv");
        PseudoFileSourcesConfig pseudoFileSources = new PseudoFileSourcesConfig(
            Paths.get("src/test/resources/integration/darkarchive"),
            Paths.get("src/test/resources/integration/springfield"),
            csv);
        createDirectories(testDir);
        Files.write(csv, String.join("\n", new String[] {
            "easy_file_id,dataset_id,path_in_AV_dir,path_in_springfield_dir",
            ",easy-dataset:112582,eaa33307-4795-40a3-9051-e7d91a21838e/bag/data/ICA_DeJager_KroniekvaneenBazenbondje_Interview_Peter_Essenberg_1.pdf,"
        }).getBytes(UTF_8));

        TestUtils.captureStdout();
        ListAppender<ILoggingEvent> log = TestUtils.captureLog(Level.INFO, "nl.knaw.dans.avbag");

        assertThatThrownBy(() -> new PseudoFileSources(pseudoFileSources))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("1 records have missing values. See warnings.");

        assertThat(log.list.get(0).getFormattedMessage())
            .startsWith("No value in column path_in_AV_dir and/or easy_file_id: CSVRecord [comment='null', recordNumber=1, values=[, easy-dataset:112582, eaa33307");
    }

    @Test
    public void should_not_read_csv_without_header() throws IOException {
        createDirectories(testDir);
        Path csv = testDir.resolve("sources.csv");
        Files.write(csv, String.join("\n", new String[] {
            "e,d,p,"
        }).getBytes(UTF_8));
        PseudoFileSourcesConfig pseudoFileSources = new PseudoFileSourcesConfig(
            Paths.get("src/test/resources/integration/darkarchive"),
            Paths.get("src/test/resources/integration/springfield"),
            Paths.get(csv.toString())
        );

        assertThatThrownBy(() -> new PseudoFileSources(pseudoFileSources))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageStartingWith("A header name is missing in [e, d, p, ]");
    }

    @Test
    public void should_not_read_csv_with_wrong_header() throws IOException {
        createDirectories(testDir);
        Path csv = testDir.resolve("sources.csv");
        Files.write(csv, String.join("\n", new String[] {
            "a,b,c,d",
            "p,q,r,s"
        }).getBytes(UTF_8));
        PseudoFileSourcesConfig pseudoFileSources = new PseudoFileSourcesConfig(
            Paths.get("src/test/resources/integration/darkarchive"),
            Paths.get("src/test/resources/integration/springfield"),
            Paths.get(csv.toString())
        );

        assertThatThrownBy(() -> new PseudoFileSources(pseudoFileSources))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageStartingWith("path_in_AV_dir not found in actual CSV headers: [a, b, c, d]");
    }

    @Test
    public void should_not_throw() throws IOException {
        String springfieldDir = "src/test/resources/integration/springfield";
        PseudoFileSourcesConfig pseudoFileSources = new PseudoFileSourcesConfig(
            Paths.get("src/test/resources/integration/darkarchive"),
            Paths.get(springfieldDir),
            Paths.get("src/test/resources/integration/sources.csv")
        );

        PseudoFileSources sources = new PseudoFileSources(pseudoFileSources);

        String uuid = "993ec2ee-b716-45c6-b9d1-7190f98a200a";
        assertThat(sources.getDarkArchiveFiles(uuid).keySet())
            .containsExactlyInAnyOrderElementsOf(new HashSet<>(Arrays.asList(
                "easy-file:8322137", "easy-file:8322136", "easy-file:8322141", "easy-file:8322138"
            )));
        assertThat(sources.getSpringFieldFiles(uuid).keySet())
            .containsExactlyInAnyOrderElementsOf(new HashSet<>(Collections.singletonList(
                "easy-file:8322141"
            )));
        assertThat(sources.getSpringFieldFiles(uuid).values())
            .containsExactlyInAnyOrderElementsOf(new HashSet<>(Collections.singletonList(
                Paths.get(springfieldDir + "/domain/dans/user/NIOD/video/148/rawvideo/2/JKKV_2007_Eindpunt_Sobibor_SCHELVIS.mp4")
            )));
    }

    @Test
    public void no_arg_config_constructor() {
        assertThatThrownBy(() -> new PseudoFileSources(new PseudoFileSourcesConfig()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("PseudoFileSourcesConfig is incomplete");
    }
}
