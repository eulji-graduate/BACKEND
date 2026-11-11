package com.module06.backend.capture.infrastructure.ai;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.ObjectMapper;

import com.module06.backend.capture.domain.model.Utterance;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiLayerHttpAdapterTest {

    @Test
    void L3_요청에_시작_발화가_있을_때만_경계_필드를_보낸다() {
        String path = "/internal/layers/l3/summarize-topic";
        String response = """
                {"summary":"","items":[],"usage":{"tokensIn":0,"tokensOut":0},"model":"dry-run","promptVersion":"v2"}
                """;
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(path))
                .andExpect(jsonPath("$.topicStartUtteranceId").value(19))
                .andExpect(jsonPath("$.utterances[0].utteranceId").value(18))
                .andExpect(jsonPath("$.utterances[1].utteranceId").value(19))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        server.expect(requestTo(path))
                .andExpect(jsonPath("$.topicStartUtteranceId").doesNotHaveJsonPath())
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        AiLayerHttpAdapter adapter = new AiLayerHttpAdapter(builder.build(), new ObjectMapper());
        List<Utterance> utterances = List.of(
                new Utterance(18L, null, 0, 1_000, "참고 발화", null),
                new Utterance(19L, null, 1_000, 2_000, "시작 발화", null));

        adapter.summarizeTopic(1L, 2L, 3, "주제", 19L, utterances, List.of());
        adapter.summarizeTopic(1L, 2L, 3, "주제", null, utterances, List.of());

        server.verify();
    }
}
