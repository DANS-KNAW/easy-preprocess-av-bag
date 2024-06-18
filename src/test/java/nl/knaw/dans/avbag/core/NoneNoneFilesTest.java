package nl.knaw.dans.avbag.core;

import nl.knaw.dans.avbag.AbstractTestWithTestDir;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.writeString;
import static org.apache.commons.io.file.PathUtils.touch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class NoneNoneFilesTest extends AbstractTestWithTestDir {

    @Test
    public void should_throw_because_a_file_to_delete_does_not_exist() throws Exception {
        var bagDir = testDir.resolve("1234/5678");
        var filesXml = bagDir.resolve("metadata/files.xml");
        createDirectories(filesXml.getParent());
        writeString(filesXml, """
            <?xml version='1.0' encoding='UTF-8'?>
            <files xsi:schemaLocation="http://easy.dans.knaw.nl/schemas/bag/metadata/files/ http://easy.dans.knaw.nl/schemas/bag/metadata/files/files.xsd"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xmlns="http://easy.dans.knaw.nl/schemas/bag/metadata/files/"
                xmlns:dct="http://purl.org/dc/terms/">
                <file filepath="data/some.txt">
                    <dct:identifier>easy-file:123</dct:identifier>
                    <accessibleToRights>NONE</accessibleToRights>
                    <visibleToRights>NONE</visibleToRights>
                </file>
            </files>""");

        var noneNoneFiles = new NoneNoneFiles(bagDir);
        var filesXmlDoc = XmlUtil.readXml(filesXml);

        assertThatThrownBy(() -> noneNoneFiles
            .removeNoneNone(filesXmlDoc)
        ).isInstanceOf(IOException.class)
            .hasMessage("1234: Could not delete target/test/NoneNoneFilesTest/1234/5678/data/some.txt");
    }

    @Test
    public void should_return_removed_files() throws Exception {
        var bagDir = testDir.resolve("1234/5678");
        var filesXml = bagDir.resolve("metadata/files.xml");
        createDirectories(filesXml.getParent());
        createDirectories(bagDir.resolve("data"));
        touch(bagDir.resolve("data/some.txt"));
        touch(bagDir.resolve("data/some2.txt"));

        writeString(filesXml, """
            <?xml version='1.0' encoding='UTF-8'?>
            <files xsi:schemaLocation="http://easy.dans.knaw.nl/schemas/bag/metadata/files/ http://easy.dans.knaw.nl/schemas/bag/metadata/files/files.xsd"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xmlns="http://easy.dans.knaw.nl/schemas/bag/metadata/files/"
                xmlns:dct="http://purl.org/dc/terms/">
                <file filepath="data/some.txt">
                    <dct:identifier>easy-file:123</dct:identifier>
                    <accessibleToRights>NONE</accessibleToRights>
                    <visibleToRights>NONE</visibleToRights>
                </file>
                <file filepath="data/some2.txt">
                    <dct:identifier>easy-file:123</dct:identifier>
                </file>
                <file filepath="data/another.txt">
                    <dct:identifier>easy-file:123</dct:identifier>
                    <accessibleToRights>ANONYMOUS</accessibleToRights>
                    <visibleToRights>NONE</visibleToRights>
                </file>
                <file filepath="data/a-third.txt">
                    <dct:identifier>easy-file:123</dct:identifier>
                    <accessibleToRights>NONE</accessibleToRights>
                    <visibleToRights>ANONYMOUS</visibleToRights>
                </file>
            </files>""");

        var noneNoneFiles = new NoneNoneFiles(bagDir);
        var filesXmlDoc = XmlUtil.readXml(filesXml);

        assertThat(noneNoneFiles.removeNoneNone(filesXmlDoc))
            .containsExactlyInAnyOrderElementsOf(List.of(Path.of("data/some.txt"), Path.of("data/some2.txt")));
    }

    @Test
    public void empty_files_xml_should_return_empty_list() throws Exception {
        var bagDir = testDir.resolve("1234/5678");
        var filesXml = bagDir.resolve("metadata/files.xml");
        createDirectories(filesXml.getParent());
        writeString(filesXml, """
            <?xml version='1.0' encoding='UTF-8'?>
            <files xsi:schemaLocation="http://easy.dans.knaw.nl/schemas/bag/metadata/files/ http://easy.dans.knaw.nl/schemas/bag/metadata/files/files.xsd"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xmlns="http://easy.dans.knaw.nl/schemas/bag/metadata/files/"
                xmlns:dct="http://purl.org/dc/terms/">
            </files>""");

        var noneNoneFiles = new NoneNoneFiles(bagDir);
        var filesXmlDoc = XmlUtil.readXml(filesXml);

        assertThat(noneNoneFiles.removeNoneNone(filesXmlDoc))
            .isEmpty();
    }
}
