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
package nl.knaw.dans.avbag;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import static java.nio.file.Files.createDirectories;
import static nl.knaw.dans.lib.util.AbstractCommandLineApp.CONFIG_FILE_KEY;
import static nl.knaw.dans.lib.util.AbstractCommandLineApp.EXAMPLE_CONFIG_FILE_KEY;

// for debugging purposes and code coverage with Intellij
@EnabledIfSystemProperty(named = "idea.test.cyclic.buffer.size", matches = ".*")
public class EasyPreprocessAvBagTest extends AbstractTestWithTestDir {

    @Test
    public void integration() throws Exception {
        Path config = testDir.resolve("config.yml");
        Path mutableInput = testDir.resolve("mutable-input");
        Path staging = createDirectories(testDir.resolve("staging"));
        Path out = createDirectories(testDir.resolve("out"));

        Path integration = Paths.get("src/test/resources/integration");
        FileUtils.copyDirectory(integration.resolve("input-bags").toFile(), mutableInput.toFile());
        Files.write(config, fixConfigValues(staging, integration));

        String[] args = new String[] { "convert", mutableInput.toString(), out.toString() };

        System.setProperty(CONFIG_FILE_KEY, config.toString());
        System.setProperty(EXAMPLE_CONFIG_FILE_KEY, config.toString());
        EasyPreprocessAvBag.main(args);
        // test terminates, no asserts possible
    }

    private List<String> fixConfigValues(Path staging, Path integration) throws IOException {
        return Files.readAllLines(Paths.get("src/test/resources/debug-etc/config.yml"))
            .stream().map(line -> line
                .replaceAll("stagingDir: .*", "stagingDir: " + staging)
                .replace("currentLogFilename: data", "currentLogFilename: " + testDir.resolve("current.log"))
                .replace(": data", ": " + integration)
            ).collect(Collectors.toList());
    }
}