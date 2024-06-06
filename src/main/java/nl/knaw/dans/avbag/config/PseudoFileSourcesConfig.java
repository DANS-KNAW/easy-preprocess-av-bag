package nl.knaw.dans.avbag.config;

import lombok.Data;

import java.nio.file.Path;

@Data
public class PseudoFileSourcesConfig {
    private Path darkarchiveDir;
    private Path springfieldDir;
    private Path path;
}
