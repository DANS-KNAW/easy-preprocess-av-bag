package nl.knaw.dans.avbag.core;

import nl.knaw.dans.avbag.core.FileElements.FileElement;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FileElementsTest {

    @Test
    public void should_find_one_file_element() throws Exception {
        // Given
        String xml = "<files xmlns=\"http://easy.dans.knaw.nl/schemas/bag/metadata/files/\" "
            + "              xmlns:dct=\"http://purl.org/dc/terms/\">"
            + "<file filepath=\"path/to/file\">"
            + "   <dct:identifier>easy-file:123</dct:identifier>"
            + "   <accessibleToRights>ANONYMOUS</accessibleToRights>"
            + "   <visibleToRights>NONE</visibleToRights>"
            + "</file>"
            + "</files>";
        Document filesXml = XmlUtil.readXmlFromString(xml);

        // When
        List<FileElement> fileElements = FileElements.read(filesXml);

        // Then
        assertThat(fileElements).hasSize(1);
        assertThat(fileElements.get(0).getFileId()).isEqualTo("easy-file:123");
        assertThat(fileElements.get(0).getFilePath()).isEqualTo("path/to/file");
        assertThat(fileElements.get(0).getAccessibleToRights()).isEqualTo("ANONYMOUS");
        assertThat(fileElements.get(0).getVisibleToRights()).isEqualTo("NONE");
    }


    @Test
    public void should_find_two_file_elements() throws Exception {
        // Given
        String xml = "<files xmlns=\"http://easy.dans.knaw.nl/schemas/bag/metadata/files/\" "
            + "              xmlns:dct=\"http://purl.org/dc/terms/\">"
            + "<file filepath=\"path/to/file\">"
            + "   <dct:identifier>easy-file:456</dct:identifier>"
            + "   <accessibleToRights>ANONYMOUS</accessibleToRights>"
            + "   <visibleToRights>NONE</visibleToRights></file>"
            + "<file filepath=\"path/to/file2\">"
            + "   <dct:identifier>easy-file:789</dct:identifier>"
            + "   <accessibleToRights>KNOWN</accessibleToRights>"
            + "   <visibleToRights>KNOWN</visibleToRights>"
            + "</file>"
            + "</files>";
        Document filesXml = XmlUtil.readXmlFromString(xml);

        // When
        List<FileElement> fileElements = FileElements.read(filesXml);

        // Then
        assertThat(fileElements).hasSize(2);
        assertThat(fileElements.get(0).getFileId()).isEqualTo("easy-file:456");
        assertThat(fileElements.get(0).getFilePath()).isEqualTo("path/to/file");
        assertThat(fileElements.get(0).getAccessibleToRights()).isEqualTo("ANONYMOUS");
        assertThat(fileElements.get(0).getVisibleToRights()).isEqualTo("NONE");
        assertThat(fileElements.get(1).getFileId()).isEqualTo("easy-file:789");
        assertThat(fileElements.get(1).getFilePath()).isEqualTo("path/to/file2");
        assertThat(fileElements.get(1).getAccessibleToRights()).isEqualTo("KNOWN");
        assertThat(fileElements.get(1).getVisibleToRights()).isEqualTo("KNOWN");
    }

    @Test
    public void should_find_no_file_elements() throws Exception {
        // Given
        String xml = "<files xmlns=\"http://easy.dans.knaw.nl/schemas/bag/metadata/files/\" "
            + "              xmlns:dct=\"http://purl.org/dc/terms/\">"
            + "</files>";
        Document filesXml = XmlUtil.readXmlFromString(xml);

        // When
        List<FileElement> fileElements = FileElements.read(filesXml);

        // Then
        assertThat(fileElements).isEmpty();
    }

    @Test
    public void should_throw_exception_when_no_accessibleToRights() throws Exception {
        // Given
        String xml = "<files xmlns=\"http://easy.dans.knaw.nl/schemas/bag/metadata/files/\" "
            + "              xmlns:dct=\"http://purl.org/dc/terms/\">"
            + "<file filepath=\"path/to/file\">"
            + "   <dct:identifier>easy-file:123</dct:identifier>"
            + "   <visibleToRights>NONE</visibleToRights>"
            + "</file>"
            + "</files>";
        Document filesXml = XmlUtil.readXmlFromString(xml);

        // When
        assertThatThrownBy(() -> FileElements.read(filesXml)).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("accessibleToRights is required on every file element");
    }

    @Test
    public void should_throw_exception_when_no_visibleToRights() throws Exception {
        // Given
        String xml = "<files xmlns=\"http://easy.dans.knaw.nl/schemas/bag/metadata/files/\" "
            + "              xmlns:dct=\"http://purl.org/dc/terms/\">"
            + "<file filepath=\"path/to/file\">"
            + "   <dct:identifier>easy-file:123</dct:identifier>"
            + "   <accessibleToRights>NONE</accessibleToRights>"
            + "</file>"
            + "</files>";
        Document filesXml = XmlUtil.readXmlFromString(xml);

        // When
        assertThatThrownBy(() -> FileElements.read(filesXml)).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("visibleToRights is required on every file element");
    }
}
