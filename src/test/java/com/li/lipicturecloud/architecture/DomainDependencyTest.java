package com.li.lipicturecloud.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DomainDependencyTest {

    private static final List<String> FORBIDDEN_IMPORTS = List.of(
            "org.springframework", "com.baomidou", "jakarta.servlet", "com.li.lipicturecloud.model.entity");

    @Test
    void domainLayerDoesNotDependOnFrameworkOrPersistenceTypes() throws IOException {
        Path domainRoot = Path.of("src/main/java/com/li/lipicturecloud/domain");
        try (var files = Files.walk(domainRoot)) {
            List<String> violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> forbiddenImports(path).stream())
                    .toList();

            assertThat(violations).isEmpty();
        }
    }

    private List<String> forbiddenImports(Path path) {
        try {
            String source = Files.readString(path);
            return FORBIDDEN_IMPORTS.stream()
                    .filter(source::contains)
                    .map(dependency -> path + " -> " + dependency)
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取领域源码: " + path, exception);
        }
    }
}
