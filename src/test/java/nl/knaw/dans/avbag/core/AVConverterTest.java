package nl.knaw.dans.avbag.core;

import ch.qos.logback.classic.Level;
import nl.knaw.dans.avbag.AbstractTestWithTestDir;
import nl.knaw.dans.avbag.config.PseudoFileSourcesConfig;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;

import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.writeString;
import static nl.knaw.dans.avbag.TestUtils.captureLog;
import static nl.knaw.dans.avbag.TestUtils.captureStdout;
import static org.assertj.core.api.Assertions.assertThat;

public class AVConverterTest extends AbstractTestWithTestDir {

    @Test
    public void integration() throws Exception {
        var stdout = captureStdout();
        captureLog(Level.INFO, "nl.knaw.dans.avbag");

        var integration = Path.of("src/test/resources/integration");
        var inputBags = integration.resolve("input-bags");
        var mutableInput = testDir.resolve("input-bags");
        var convertedBags = createDirectories(testDir.resolve("converted-bags"));
        var stagedBags = createDirectories(testDir.resolve("staged-bags"));

        FileUtils.copyDirectory(inputBags.toFile(), mutableInput.toFile());
        var pseudoFileSources = new PseudoFileSourcesConfig();
        pseudoFileSources.setDarkarchiveDir(integration.resolve("av-dir"));
        pseudoFileSources.setSpringfieldDir(integration.resolve("springfield-dir"));
        pseudoFileSources.setPath(integration.resolve("mapping.csv"));

        new AVConverter(mutableInput, convertedBags, stagedBags, new PseudoFileSources(pseudoFileSources))
            .convertAll();

        writeString(testDir.resolve("log.txt"), stdout.toString());
        assertThat(mutableInput).isEmptyDirectory();
        assertThat(stagedBags).isEmptyDirectory();

        // all manifest-sha1.txt files should be unique
        var manifests = new ArrayList<>();
        addManifests(manifests, inputBags);
        addManifests(manifests, convertedBags);
        assertThat(new HashSet<>(manifests))
            .containsExactlyInAnyOrderElementsOf(manifests);
    }

    private void addManifests(ArrayList<Object> manifests, Path convertedBags) throws IOException {
        try (var files = Files.walk(convertedBags, 3)) {
            files.filter(path -> path.getFileName().toString().equals("manifest-sha1.txt"))
                .forEach(path -> manifests.add(readSorted(path)));
        }
    }

    private String readSorted(Path path) {
        try {
            return Files.readAllLines(path).stream().sorted().reduce("", (a, b) -> a + b + "\n");
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
