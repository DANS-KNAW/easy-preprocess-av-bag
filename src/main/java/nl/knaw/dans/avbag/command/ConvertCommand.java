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
package nl.knaw.dans.avbag.command;

import lombok.extern.slf4j.Slf4j;
import nl.knaw.dans.avbag.core.AVConverter;
import nl.knaw.dans.avbag.core.PseudoFileSources;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import javax.validation.constraints.NotNull;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import static nl.knaw.dans.AbstractCommandLineApp.CONFIG_FILE_KEY;
import static nl.knaw.dans.lib.util.AbstractCommandLineApp.EXAMPLE_CONFIG_FILE_KEY;

@Slf4j
@Command(
    name = "convert",
    mixinStandardHelpOptions = true,
    description = "Convert the bags.")
public class ConvertCommand implements Callable<Integer> {

    private final PseudoFileSources pseudoFileSources;
    private final Path stagingDir;

    @CommandLine.Parameters(index = "0",
                            paramLabel = "INPUT_DIR",
                            description = "The directory containing the AV dataset.")
    private Path inputDir;

    @CommandLine.Parameters(index = "1",
                            paramLabel = "OUTPUT_DIR",
                            description = "The directory where the converted dataset will be stored.")
    private Path outputDir;

    public ConvertCommand(PseudoFileSources pseudoFileSources, @NotNull Path stagingDir) {
        this.pseudoFileSources = pseudoFileSources;
        this.stagingDir = stagingDir;
    }

    @Override
    public Integer call() {
        log.warn(System.getProperty(EXAMPLE_CONFIG_FILE_KEY));
        log.warn(System.getProperty(CONFIG_FILE_KEY));
        try {
            new AVConverter(inputDir, outputDir, stagingDir, pseudoFileSources)
                .convertAll();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        return 0;
    }
}
