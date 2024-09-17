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

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
public class FileElements {
    @Value
    static class FileElement {
        String fileId;
        String filePath;
        String accessibleToRights;
        String visibleToRights;
    }

    static List<FileElement> read(Document filesXml) {
        NodeList fileElements = filesXml.getElementsByTagName("file");
        List<FileElement> fileElementList = new ArrayList<>();
        for (int i = 0; i < fileElements.getLength(); i++) {
            Element fileElement = (Element) fileElements.item(i);
            fileElementList.add(getFileElement(fileElement));
        }
        return fileElementList;
    }

    private static FileElement getFileElement(@NonNull Element fileElement) {
        if (fileElement.getElementsByTagName("accessibleToRights").getLength() == 0) {
            throw new IllegalArgumentException("accessibleToRights is required on every file element");
        }
        if (fileElement.getElementsByTagName("visibleToRights").getLength() == 0) {
            throw new IllegalArgumentException("visibleToRights is required on every file element");
        }
        Element accessibleToRights = (Element) fileElement.getElementsByTagName("accessibleToRights").item(0);
        Element visibleToRights = (Element) fileElement.getElementsByTagName("visibleToRights").item(0);
        return new FileElement(
            fileElement.getElementsByTagName("dct:identifier").item(0).getTextContent(),
            fileElement.getAttribute("filepath"),
            StringUtils.trim(accessibleToRights.getTextContent()),
            StringUtils.trim(visibleToRights.getTextContent())
        );
    }
}
