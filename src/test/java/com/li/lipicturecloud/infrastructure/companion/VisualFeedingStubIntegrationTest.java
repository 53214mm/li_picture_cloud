package com.li.lipicturecloud.infrastructure.companion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.application.companion.AuthorizedPictureContent;
import com.li.lipicturecloud.application.companion.AuthorizedPictureContentProvider;
import com.li.lipicturecloud.application.companion.AuthorizedPictureRef;
import com.li.lipicturecloud.application.companion.CompanionFeedingCoordinator;
import com.li.lipicturecloud.application.companion.FeedReservation;
import com.li.lipicturecloud.application.companion.PictureNutritionAnalyzer;
import com.li.lipicturecloud.application.companion.PictureObservationProvider;
import com.li.lipicturecloud.application.companion.VisionQuotaGuard;
import com.li.lipicturecloud.application.companion.view.FeedPictureResult;
import com.li.lipicturecloud.domain.companion.Companion;
import com.li.lipicturecloud.domain.companion.CompanionBalance;
import com.li.lipicturecloud.domain.companion.CompanionRepository;
import com.li.lipicturecloud.domain.companion.CompanionSkill;
import com.li.lipicturecloud.domain.companion.FeedingRunRepository;
import com.li.lipicturecloud.domain.companion.GrowthRecordRepository;
import com.li.lipicturecloud.domain.companion.NutritionPolicy;
import com.li.lipicturecloud.domain.companion.PictureNutrition;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 真实 H2 事务加本地 HTTP stub 的视觉喂养闭环。
 *
 * <p>这不是 Live smoke：模型端点由 {@link MockRestServiceServer} 截获，图片内容也是最小 JPEG
 * fixture。因此它可以在 CI 运行，同时证明请求策略、日额度、视觉候选、伙伴平衡、来源审计和
 * 幂等回放确实能跨越各层协作。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class VisualFeedingStubIntegrationTest {

    private static final long SUBJECT_ID = 7_902L;
    private static final long PICTURE_ID = 1_002L;
    private static final String KEY = "vision-stub-feed-0001";
    private static final String FINGERPRINT = "b".repeat(64);
    private static final String CORRELATION = "8c4f8a48-7c86-49a3-9d66-52f759d53d36";
    private static final URI ENDPOINT = URI.create("https://dashscope.test/compatible-mode/v1/chat/completions");

    @Autowired private CompanionRepository companions;
    @Autowired private FeedingRunRepository runs;
    @Autowired private GrowthRecordRepository growth;
    @Autowired private CompanionFeedingCoordinator coordinator;
    @Autowired private VisionQuotaGuard quota;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void localVisionStubGrowsOncePersistsActualProvenanceAndReplaysWithoutAnotherCall() {
        Companion companion = companions.createIfAbsent(SUBJECT_ID, CompanionBalance.v1());
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DashScopeVisionClient client = new DashScopeVisionClient(builder.build(), objectMapper, ENDPOINT,
                "dashscope", "qwen3.6-flash", "stub-key");
        PictureNutritionAnalyzer adapter = new VisualPictureNutritionAdapter(quota,
                privateFixtureContent(), client, new MetadataPictureNutritionAdapter(mock(PictureObservationProvider.class)),
                Clock.fixed(Instant.parse("2026-08-13T08:00:00Z"), ZoneOffset.UTC), 8L * 1024 * 1024, 10);

        try {
            server.expect(requestTo(ENDPOINT))
                    .andExpect(header("Authorization", "Bearer stub-key"))
                    .andExpect(jsonPath("$.model").value("qwen3.6-flash"))
                    .andExpect(jsonPath("$.messages[1].content[1].image_url.url")
                            .value("data:image/jpeg;base64,/9j/"))
                    .andRespond(withSuccess(response("JOYFUL", 2, 3, true, 2, 3, "0.84"),
                            MediaType.APPLICATION_JSON));

            AuthorizationSubject subject = AuthorizationSubject.user(SUBJECT_ID);
            FeedReservation started = coordinator.reserve(companion, subject, PICTURE_ID, KEY,
                    FINGERPRINT, CORRELATION, NutritionPolicy.VISUAL_WITH_METADATA_FALLBACK,
                    "dashscope", "qwen3.6-flash");
            PictureNutrition nutrition = adapter.analyze(new AuthorizedPictureRef(subject, PICTURE_ID));
            FeedPictureResult result = coordinator.complete(started.run(), nutrition);

            assertThat(result.outcome()).isEqualTo("GROWN");
            assertThat(result.companion().lifeExperience()).isEqualTo(45L);
            assertThat(result.companion().traits().curiosity()).isEqualByComparingTo("0.40");
            assertThat(result.companion().skills())
                    .filteredOn(skill -> skill.code().equals(CompanionSkill.IMAGE_OBSERVATION.name()))
                    .singleElement().extracting(skill -> skill.experience()).isEqualTo(24L);
            assertThat(result.growth())
                    .extracting("providerCode", "modelCode", "fallbackReasonCode", "nutritionLabel")
                    .containsExactly("dashscope", "qwen3.6-flash", null,
                            "Qwen 视觉营养 · 已分析图片内容");
            assertThat(result.growth().confidence()).isEqualByComparingTo("0.84");
            assertThat(jdbc.queryForObject("""
                    SELECT attempts FROM companion_vision_usage WHERE subjectId = ?
                    """, Integer.class, SUBJECT_ID)).isEqualTo(1);

            FeedReservation replay = coordinator.reserve(companion, subject, PICTURE_ID, KEY,
                    FINGERPRINT, "should-not-replace-correlation",
                    NutritionPolicy.VISUAL_WITH_METADATA_FALLBACK, "dashscope", "qwen3.6-flash");
            assertThat(replay.kind()).isEqualTo(FeedReservation.Kind.REPLAY);
            assertThat(replay.replay().companion().revision()).isEqualTo(1L);
            assertThat(growth.findRecent(companion.id(), 10)).hasSize(1);
            server.verify();
        } finally {
            cleanup(companion.id());
        }
    }

    private static AuthorizedPictureContentProvider privateFixtureContent() {
        return new AuthorizedPictureContentProvider() {
            @Override
            public AuthorizedPictureContent load(AuthorizedPictureRef reference, long maxBytes) {
                assertPrivateReference(reference);
                return new AuthorizedPictureContent(PICTURE_ID, Instant.parse("2026-08-13T07:59:00Z"),
                        "image/jpeg", "a".repeat(64), new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            }

            @Override
            public void verifyStillAuthorized(AuthorizedPictureRef reference, AuthorizedPictureContent content) {
                assertPrivateReference(reference);
                assertThat(content.pictureId()).isEqualTo(PICTURE_ID);
            }

            private void assertPrivateReference(AuthorizedPictureRef reference) {
                assertThat(reference.subject()).isEqualTo(AuthorizationSubject.user(SUBJECT_ID));
                assertThat(reference.pictureId()).isEqualTo(PICTURE_ID);
            }
        };
    }

    private void cleanup(long companionId) {
        jdbc.update("DELETE FROM companion_growth_record WHERE companionId = ?", companionId);
        jdbc.update("DELETE FROM companion_feed_run WHERE companionId = ?", companionId);
        jdbc.update("DELETE FROM companion_skill WHERE companionId = ?", companionId);
        jdbc.update("DELETE FROM companion WHERE id = ?", companionId);
        jdbc.update("DELETE FROM companion_vision_usage WHERE subjectId = ?", SUBJECT_ID);
    }

    private static String response(String mood, int complexity, int energy, boolean social, int motion,
                                   int creativity, String confidence) {
        return """
                {"choices":[{"message":{"content":"{\\"mood\\":\\"%s\\",\\"sceneComplexity\\":%d,\\"energy\\":%d,\\"socialPresence\\":%s,\\"motionPotential\\":%d,\\"creativity\\":%d,\\"confidence\\":%s}"}}]}
                """.formatted(mood, complexity, energy, social, motion, creativity, confidence);
    }
}
