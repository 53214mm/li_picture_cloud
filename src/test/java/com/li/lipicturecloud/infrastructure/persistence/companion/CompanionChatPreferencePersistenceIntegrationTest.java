package com.li.lipicturecloud.infrastructure.persistence.companion;

import com.li.lipicturecloud.domain.companion.CompanionChatMode;
import com.li.lipicturecloud.domain.companion.CompanionChatPreferenceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CompanionChatPreferencePersistenceIntegrationTest {

    @Autowired
    private CompanionChatPreferenceRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void savesOneRowPerUserAndOverwritesTheChoice() {
        assertThat(repository.findMode(8801L)).isEmpty();

        repository.saveMode(8801L, CompanionChatMode.MODEL);
        repository.saveMode(8801L, CompanionChatMode.DEMO);
        repository.saveMode(8802L, CompanionChatMode.MODEL);

        assertThat(repository.findMode(8801L)).contains(CompanionChatMode.DEMO);
        assertThat(repository.findMode(8802L)).contains(CompanionChatMode.MODEL);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM companion_chat_preference WHERE subjectId = 8801", Integer.class))
                .isEqualTo(1);
    }
}
