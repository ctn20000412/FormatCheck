package com.formatcheck.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.storage.root=target/test-storage/sessions")
@AutoConfigureMockMvc
class BackendApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateRuleExtractionSessionAndExtractRules() throws Exception {
        String createResponse = mockMvc.perform(post("/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "RULE_EXTRACTION",
                                  "name": "测试规则抽取"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(createResponse);
        String sessionId = root.path("data").path("sessionId").asText();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "standard.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "mock doc".getBytes()
        );

        mockMvc.perform(multipart("/sessions/{sessionId}/files/standard", sessionId).file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UPLOADED"));

        mockMvc.perform(post("/rules/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "%s",
                                  "schemaVersion": "1.0"
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAITING_CONFIRM"));
    }
}
