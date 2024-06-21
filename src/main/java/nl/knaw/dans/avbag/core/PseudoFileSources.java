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

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import nl.knaw.dans.avbag.config.PseudoFileSourcesConfig;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.text.MessageFormat.format;
import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;

@Slf4j
public class PseudoFileSources {
    private final Path darkArchiveDir;
    private final Path springfieldDir;
    private final Map<String, Map<String, String>> bagParentToFileToSpringField = new HashMap<>();
    private final Map<String, Map<String, String>> bagParentToFileToAV = new HashMap<>();

    @NonNull
    public Map<String, Path> getSpringFieldFiles(@NonNull String bagParent) {
        return toImmutableWithPathValues(springfieldDir, bagParentToFileToSpringField.get(bagParent));
    }

    @NonNull
    public Map<String, Path> getDarkArchiveFiles(@NonNull String bagParent) {
        return toImmutableWithPathValues(darkArchiveDir, bagParentToFileToAV.get(bagParent));
    }

    private Map<String, Path> toImmutableWithPathValues(Path baseDir, Map<String, String> innerMap) {
        if (innerMap == null) {
            return Collections.emptyMap();
        }
        Map<String, Path> map = new HashMap<>();
        for (var entry : innerMap.entrySet()) {
            map.put(entry.getKey(), baseDir.resolve(entry.getValue()));
        }
        return Collections.unmodifiableMap(map);
    }

    public PseudoFileSources(PseudoFileSourcesConfig pseudoFileSources) throws IOException {
        var dirs = new Path[] { pseudoFileSources.getDarkarchiveDir(), pseudoFileSources.getSpringfieldDir() };
        if (!Arrays.stream(dirs).filter(Objects::isNull).toList().isEmpty()) {
            throw new IllegalArgumentException("PseudoFileSourcesConfig is incomplete");
        }
        var notExisting = Arrays.stream(dirs).filter(dir -> !FileUtils.isDirectory(dir.toFile())).toList();
        if (!notExisting.isEmpty()) {
            throw new IOException("Not existing or not a directory: " + notExisting);
        }
        readCSV(pseudoFileSources.getPath());

        this.darkArchiveDir = pseudoFileSources.getDarkarchiveDir();
        this.springfieldDir = pseudoFileSources.getSpringfieldDir();

        var notExistingSpringfield = bagParentToFileToSpringField.values().stream()
            .flatMap(innerMap -> innerMap.values().stream())
            .map(springfieldDir::resolve)
            .filter(path -> !Files.exists(path)).toList();
        var notExistingAV = bagParentToFileToAV.values().stream()
            .flatMap(innerMap -> innerMap.values().stream())
            .map(darkArchiveDir::resolve)
            .filter(path -> !Files.exists(path)).toList();
        if (!notExistingSpringfield.isEmpty() || !notExistingAV.isEmpty()) {
            throw new IOException("Not existing files: " + notExistingSpringfield + " " + notExistingAV);
        }
    }

    private void readCSV(Path filePath) throws IOException {
        if (!filePath.toFile().isFile()) {
            // The parser is not very informative when the file is a directory
            throw new IOException("Does not exist or is not a file: " + filePath);
        }
        try (CSVParser csvParser = CSVParser.parse(filePath, StandardCharsets.UTF_8, CSVFormat.DEFAULT.withHeader())) {
            var count = 0;
            for (CSVRecord csvRecord : csvParser) {
                var pathInAVdDir = csvRecord.get("path_in_AV_dir");
                var pathInSpringfieldDir = csvRecord.get("path_in_springfield_dir");
                var fileId = csvRecord.get("easy_file_id");
                if (isNotEmpty(pathInAVdDir) && isNotEmpty(fileId)) {
                    var bagParent = Path.of(pathInAVdDir).getName(0).toString();
                    bagParentToFileToAV.computeIfAbsent(
                        bagParent, k -> new HashMap<>()
                    ).put(fileId, pathInAVdDir);
                    if (isNotEmpty(pathInSpringfieldDir)) {
                        bagParentToFileToSpringField.computeIfAbsent(
                            bagParent, k -> new HashMap<>()
                        ).put(fileId, pathInSpringfieldDir);
                    }
                }
                else {
                    count++;
                    log.warn("No value in column path_in_AV_dir and/or easy_file_id: {}", csvRecord);
                }
            }
            if (count > 0) {
                throw new IllegalStateException(format("{0} records have missing values. See warnings.", count));
            }
        }
    }
}
