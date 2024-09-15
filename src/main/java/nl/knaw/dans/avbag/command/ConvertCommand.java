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
import nl.knaw.dans.avbag.config.PseudoFileSourcesConfig;
import nl.knaw.dans.avbag.core.AVConverter;
import nl.knaw.dans.avbag.core.PseudoFileSources;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import javax.validation.constraints.NotNull;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Slf4j
@Command(
    name = "convert",
    mixinStandardHelpOptions = true,
    description = "Convert the bags.")
public class ConvertCommand implements Callable<Integer> {

    private final Path stagingDir;
    private final PseudoFileSourcesConfig config;

    @CommandLine.Parameters(index = "0",
                            paramLabel = "INPUT_DIR",
                            description = "The directory containing the AV dataset.")
    private Path inputDir;

    @CommandLine.Parameters(index = "1",
                            paramLabel = "OUTPUT_DIR",
                            description = "The directory where the converted dataset will be stored.")
    private Path outputDir;

    @Option(names = "--keep-input, -k",
            description = "Keep the input files after conversion.")
    private boolean keepInput;

    public ConvertCommand(PseudoFileSourcesConfig config, @NotNull Path stagingDir) {
        this.config = config;
        this.stagingDir = stagingDir;
    }

    @Override
    public Integer call() {
        try {
            new AVConverter(inputDir, outputDir, stagingDir, new PseudoFileSources(config), keepInput)
                .convertAll();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        return 0;
    }
}
