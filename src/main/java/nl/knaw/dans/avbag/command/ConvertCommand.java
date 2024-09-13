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

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.knaw.dans.avbag.config.StreamingCopiesConfig;
import nl.knaw.dans.avbag.core.AVConverter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Slf4j
@Command(
    name = "convert",
    mixinStandardHelpOptions = true,
    description = "Convert the bags.")
@RequiredArgsConstructor
public class ConvertCommand implements Callable<Integer> {
    @NonNull
    private final StreamingCopiesConfig config;
    @NonNull
    private final Path stagingDir;

    @Parameters(index = "0",
                paramLabel = "INPUT_DIR",
                description = "The directory containing the exported AV bags.")
    private Path inputDir;

    @Parameters(index = "1",
                paramLabel = "OUTPUT_DIR",
                description = "The directory where the converted AV bags will be stored.")
    private Path outputDir;

    @Override
    public Integer call() {
        try {
            new AVConverter(inputDir, outputDir, stagingDir, new PseudoFileSources(config))
                .convertAll();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        return 0;
    }
}
