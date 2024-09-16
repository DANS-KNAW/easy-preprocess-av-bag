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
