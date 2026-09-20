package com.li.lipicturecloud.deployment;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class LiquibaseReleaseChecklistContractTest {

    private static final Pattern INCLUDE = Pattern.compile(
            "<include\\s+file=\"changes/([^\"]+)\"");
    private static final Pattern CHANGESET = Pattern.compile(
            "<changeSet\\s+id=\"[^\"]+\"\\s+author=\"([^\"]+)\"");

    @Test
    void productionChecklistCoversEveryMasterChangelogAndTheCurrentTotal() throws IOException {
        String master = read("src/main/resources/db/changelog/db.changelog-master.xml");
        String checklist = read(
                "docs/reviews/2026-08-14-production-security-release-checklist.md");
        List<String> includedFiles = captures(INCLUDE, master);

        int total = 0;
        for (String filename : includedFiles) {
            String changelog = read("src/main/resources/db/changelog/changes/" + filename);
            List<String> authors = captures(CHANGESET, changelog);
            assertThat(authors)
                    .as("changeSet authors in %s", filename)
                    .isNotEmpty()
                    .containsOnly("li-picture-cloud");
            total += authors.size();
            assertThat(checklist)
                    .as("release checklist entry for %s", filename)
                    .contains(filename);
        }

        assertThat(checklist).contains("完整执行 " + total + " 个 changeSet");
    }

    private static List<String> captures(Pattern pattern, String input) {
        Matcher matcher = pattern.matcher(input);
        List<String> values = new ArrayList<>();
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }

    private String read(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
