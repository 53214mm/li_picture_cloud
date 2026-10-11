package com.li.lipicturecloud.infrastructure.persistence.airuntime;

import com.li.lipicturecloud.domain.airuntime.VisionSearchPreferenceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VisionSearchPreferencePersistenceIntegrationTest {

    @Autowired
    private VisionSearchPreferenceRepository repository;

    @Test
    void defaultsToOffAndKeepsTheLatestChoicePerUser() {
        assertThat(repository.forcedSearch(9901L)).isFalse();
        repository.save(9901L, true);
        repository.save(9902L, true);
        repository.save(9901L, false);
        assertThat(repository.forcedSearch(9901L)).isFalse();
        assertThat(repository.forcedSearch(9902L)).isTrue();
    }
}
