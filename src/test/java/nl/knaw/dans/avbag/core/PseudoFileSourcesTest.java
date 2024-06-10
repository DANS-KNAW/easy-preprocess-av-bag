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
import nl.knaw.dans.avbag.config.PseudoFileSourcesConfig;
import org.assertj.core.api.AbstractThrowableAssert;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Set;

import static java.nio.file.Files.createDirectories;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

public class PseudoFileSourcesTest extends AbstractTestWithTestDir {
    @Test
    public void should_abort_when_dirs_are_omitted() throws IOException {
        assertThatThrownBy(() ->
            new PseudoFileSources(new PseudoFileSourcesConfig())
        ).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("PseudoFileSourcesConfig is incomplete");
    }

    @Test
    public void should_abort_when_dirs_do_not_exist() throws IOException {
        var pseudoFileSources = new PseudoFileSourcesConfig();
        pseudoFileSources.setDarkarchiveDir(testDir.resolve("darkArchiveDir"));
        pseudoFileSources.setSpringfieldDir(testDir.resolve("springfieldDir"));
        assertThatThrownBy(() ->
            new PseudoFileSources(pseudoFileSources)
        ).isInstanceOf(IOException.class)
            .hasMessage(
                "Not existing or not a directory: [target/test/PseudoFileSourcesTest/darkArchiveDir, target/test/PseudoFileSourcesTest/springfieldDir]");
    }

    @Test
    public void should_abort_when_csv_is_not_specified() throws IOException {
        var pseudoFileSources = new PseudoFileSourcesConfig();
        pseudoFileSources.setDarkarchiveDir(createDirectories(testDir.resolve("darkArchiveDir")));
        pseudoFileSources.setSpringfieldDir(createDirectories(testDir.resolve("springfieldDir")));
        assertThatThrownBy(() ->
            new PseudoFileSources(pseudoFileSources)
        ).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("PseudoFileSources.getPath() == null");
    }

    @Test
    public void should_abort_when_csv_does_not_exist() throws IOException {
        var pseudoFileSources = new PseudoFileSourcesConfig();
        pseudoFileSources.setDarkarchiveDir(createDirectories(testDir.resolve("darkArchiveDir")));
        pseudoFileSources.setSpringfieldDir(createDirectories(testDir.resolve("springfieldDir")));
        pseudoFileSources.setPath(testDir.resolve("mapping.csv"));
        assertThatThrownBy(() ->
            new PseudoFileSources(pseudoFileSources)
        ).isInstanceOf(IOException.class)
            .hasMessage("Does not exist or is not a file: target/test/PseudoFileSourcesTest/mapping.csv");
    }

    @Test
    public void should_abort_when_csv_is_a_dir() throws IOException {
        var pseudoFileSources = new PseudoFileSourcesConfig();
        pseudoFileSources.setDarkarchiveDir(createDirectories(testDir.resolve("darkArchiveDir")));
        pseudoFileSources.setSpringfieldDir(createDirectories(testDir.resolve("springfieldDir")));
        pseudoFileSources.setPath(Path.of("src/test/resources/integration"));
        AbstractThrowableAssert<?, ? extends Throwable> abstractThrowableAssert = assertThatThrownBy(() ->
            new PseudoFileSources(pseudoFileSources)
        );
        abstractThrowableAssert.isInstanceOf(IOException.class)
            .hasMessageStartingWith("Does not exist or is not a file: src/test/resources/integration");
    }

    @Test
    public void should_abort_when_files_in_csv_do_not_exist() throws IOException {
        var pseudoFileSources = new PseudoFileSourcesConfig();
        pseudoFileSources.setDarkarchiveDir(createDirectories(testDir.resolve("darkArchiveDir")));
        pseudoFileSources.setSpringfieldDir(createDirectories(testDir.resolve("springfieldDir")));
        pseudoFileSources.setPath(Path.of("src/test/resources/integration/mapping.csv"));
        assertThatThrownBy(() ->
            new PseudoFileSources(pseudoFileSources)
        ).isInstanceOf(IOException.class)
            .hasMessageStartingWith("Not existing files: [target/test/PseudoFileSourcesTest/springfieldDir/domain/dans/user/nini/video/12/rawvideo/2/NH173.mp4")
            .hasMessageEndingWith("darkArchiveDir/993ec2ee-b716-45c6-b9d1-7190f98a200a/bag/data/JKKV_JBohnen_IV-verklaring_schenkingsovereenkomst_NIOD.pdf]");
    }

    @Test
    public void should_not_throw() throws IOException {
        var pseudoFileSources = new PseudoFileSourcesConfig();
        var springfieldDir = "src/test/resources/integration/springfield-dir";
        pseudoFileSources.setSpringfieldDir(Path.of(springfieldDir));
        pseudoFileSources.setDarkarchiveDir(Path.of("src/test/resources/integration/av-dir"));
        pseudoFileSources.setPath(Path.of("src/test/resources/integration/mapping.csv"));

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
