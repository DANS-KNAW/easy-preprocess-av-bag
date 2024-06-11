package nl.knaw.dans.avbag.core;

import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;

@Slf4j
public class PlaceHolders {

    private final Path bagDir;
    private final Path bagParent;
    private final Document filesXml;
    private final Map<String, Path> identifierToDestMap = new HashMap<>();

    /**
     * @param bagDir   the directory of the bag
     * @param filesXml the files.xml document of the bag, which will be modified by removing <dct:source> elements
     * @throws IOException if the files.xml document cannot be read
     */
    public PlaceHolders(Path bagDir, Document filesXml) throws IOException {
        this.filesXml = filesXml;
        this.bagDir = bagDir;
        bagParent = bagDir.getParent().getFileName();
        find();
    }

    public boolean hasSameFileIds(PseudoFileSources pseudoFileSources) {
        var mappedFileIds = pseudoFileSources.getDarkArchiveFiles(bagParent.toString()).keySet();
        var replacedFileIds = identifierToDestMap.keySet();

        // Find the differences
        Set<String> onlyInMapping = new HashSet<>(mappedFileIds);
        onlyInMapping.removeAll(replacedFileIds);
        Set<String> onlyInReplaced = new HashSet<>(replacedFileIds);
        onlyInReplaced.removeAll(mappedFileIds);

        // Log the differences
        if (!onlyInMapping.isEmpty())
            log.error("Files in PseudoFileSources but not having <dct:source> and length zero: {} {}", bagParent, onlyInMapping);
        if (!onlyInReplaced.isEmpty())
            log.error("Files having <dct:source> and length zero but not in PseudoFileSources: {} {}", bagParent, onlyInReplaced);

        return onlyInReplaced.isEmpty() && onlyInMapping.isEmpty();
    }

    public String getDestPath(String identifier) {
        var path = identifierToDestMap.get(identifier);
        return (path != null) ? path.toString() : null;
    }

    private void find() throws IOException {

        var fileNodes = filesXml.getElementsByTagName("file");
        for (int i = 0; i < fileNodes.getLength(); i++) {
            var fileElement = (Element) fileNodes.item(i);
            var sourceNode = fileElement.getElementsByTagName("dct:source");
            if (sourceNode.getLength() > 0) {
                NodeList identifierNodes = fileElement.getElementsByTagName("dct:identifier");
                if (identifierNodes.getLength() == 0) {
                    log.error("No <dct:identifier> found: {} {}", bagParent, XmlUtil.serializeNode(fileElement));
                }
                else {
                    var filePath = fileElement.getAttribute("filepath");
                    if (isEmpty(filePath)) {
                        log.error("No filepath attribute found: {} {}", bagParent, XmlUtil.serializeNode(fileElement));
                    }
                    else if (0 == Files.size(bagDir.resolve(filePath))) {
                        var identifier = identifierNodes.item(0).getTextContent();
                        identifierToDestMap.put(identifier, Path.of(filePath));
                    }
                }
                fileElement.removeChild(sourceNode.item(0));
            }
        }
    }
}
