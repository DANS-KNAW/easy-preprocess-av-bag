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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.lang.String.format;

public class FileRemover {

    private final Path bagDir;

    public FileRemover(Path bagDir) {
        this.bagDir = bagDir;
    }

    public List<Path> removeFiles(Document mutatedFilesXml, Predicate<Element> removeWhen) throws IOException {

        List<Path> filesWithNoneNone = new ArrayList<>();
        NodeList fileList = mutatedFilesXml.getElementsByTagName("file");
        for (int i = 0; i < fileList.getLength(); i++) {
            Element fileElement = (Element) fileList.item(i);
//            if (isNone(fileElement, "accessibleToRights") && isNone(fileElement, "visibleToRights")) {
            if(removeWhen.test(fileElement)) {
                String filepath = fileElement.getAttribute("filepath");
                filesWithNoneNone.add(Paths.get(filepath));
                fileElement.getParentNode().removeChild(fileElement);
                Path file = bagDir.resolve(filepath);
                if (!file.toFile().delete()) {
                    throw new IOException(format("%s: Could not delete %s", bagDir.getParent().getFileName(), file));
                }
                deleteIfEmpty(file.getParent());
                // Since we're modifying the list we're iterating over, decrement i to adjust for the next iteration.
                i--;
            }
        }
        return filesWithNoneNone;
    }

    private static void deleteIfEmpty(Path path) throws IOException {
        try (Stream<Path> list = Files.list(path)) {
            if (!list.iterator().hasNext()) {
                Files.deleteIfExists(path);
                deleteIfEmpty(path.getParent());
            }
        }
    }


}
