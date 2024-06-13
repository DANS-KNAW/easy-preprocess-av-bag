package nl.knaw.dans.avbag.core;

import nl.knaw.dans.avbag.AbstractTestWithTestDir;
import nl.knaw.dans.avbag.config.PseudoFileSourcesConfig;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static java.nio.file.Files.createDirectories;

public class AVConverterTest extends AbstractTestWithTestDir {

    @Test
    public void integration() throws Exception {
        var integration = Path.of("src/test/resources/integration");
        var mutableInput = testDir.resolve("input-bags");
        FileUtils.copyDirectory(
            integration.resolve("input-bags").toFile(),
            mutableInput.toFile()
        );
        var pseudoFileSources = new PseudoFileSourcesConfig();
        pseudoFileSources.setDarkarchiveDir(integration.resolve("av-dir"));
        pseudoFileSources.setSpringfieldDir(integration.resolve("springfield-dir"));
        pseudoFileSources.setPath(integration.resolve("mapping.csv"));
        new AVConverter(
            mutableInput,
            createDirectories(testDir.resolve("converted-bags")),
            createDirectories(testDir.resolve("staged-bags")),
            new PseudoFileSources(pseudoFileSources)
        ).convertAll();
    }
}
