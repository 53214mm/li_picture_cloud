package com.li.lipicturecloud.infrastructure.companion;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DashScopeTextEmbedderTest {

    private static final URI ENDPOINT = URI.create("https://dashscope.test/compatible-mode/v1/embeddings");

    @Test
    void batchesTextsAndRestoresTheirOrder() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DashScopeTextEmbedder embedder = new DashScopeTextEmbedder(builder.build(), new ObjectMapper(), ENDPOINT,
                "qwen3.7-text-embedding", "sk-test", 2);
        server.expect(requestTo(ENDPOINT))
                .andExpect(header("Authorization", "Bearer sk-test"))
                .andExpect(jsonPath("$.model").value("qwen3.7-text-embedding"))
                .andExpect(jsonPath("$.dimensions").value(2))
                .andExpect(jsonPath("$.input.length()").value(10))
                .andRespond(withSuccess(response(10), MediaType.APPLICATION_JSON));
        server.expect(requestTo(ENDPOINT))
                .andExpect(jsonPath("$.input.length()").value(1))
                .andRespond(withSuccess(response(1), MediaType.APPLICATION_JSON));

        List<float[]> vectors = embedder.embedAll(IntStream.range(0, 11).mapToObj(i -> "文本" + i).toList());

        assertThat(vectors).hasSize(11);
        assertThat(vectors.get(3)).containsExactly(3f, 0.5f);
        server.verify();
    }

    @Test
    void rejectsVectorsOfTheWrongSize() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DashScopeTextEmbedder embedder = new DashScopeTextEmbedder(builder.build(), new ObjectMapper(), ENDPOINT,
                "m", "k", 3);
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess(response(1), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> embedder.embed("x")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void vectorLiteralUsesPgvectorSyntax() {
        assertThat(PgVectorCompanionMemoryIndex.literal(new float[]{0.25f, -1f})).isEqualTo("[0.25,-1.0]");
    }

    /** Items are returned in reverse to prove the index field restores input order. */
    private static String response(int count) {
        StringBuilder data = new StringBuilder();
        for (int i = count - 1; i >= 0; i--) {
            data.append(data.isEmpty() ? "" : ",")
                    .append("{\"index\":").append(i).append(",\"embedding\":[").append(i).append(",0.5]}");
        }
        return "{\"data\":[" + data + "]}";
    }
}
