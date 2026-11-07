package com.knowitai.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.knowitai.dto.AnalysisResponse;
import com.knowitai.dto.AnalysisResponse.Confidence;
import com.knowitai.validation.ValidatedImage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
// BOOT 3.x: use com.fasterxml.jackson.databind.ObjectMapper and com.fasterxml.jackson.databind.json.JsonMapper
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class GeminiVisionServiceTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final GeminiVisionService service =
            new GeminiVisionService(mapper, "test-key", "test-model", 5, "http://localhost:1");

    private static final String REPORT_JSON = """
            {
              "objectName": "Digital multimeter",
              "category": "Electrical test instrument",
              "description": "Measures voltage, current and resistance.",
              "identification": {
                "confidence": "HIGH",
                "visibleEvidence": ["LCD display", "rotary dial"],
                "inferredDetails": [],
                "alternatives": [],
                "missingInformation": []
              },
              "howItWorks": "It measures electrical signals.",
              "advantages": ["Versatile"],
              "disadvantages": ["Needs some training"],
              "commonUses": ["Testing batteries"],
              "safety": {
                "riskLevel": "MODERATE",
                "knownHazards": ["Shock risk when measuring mains"],
                "precautions": ["Check the CAT rating"],
                "limitations": [],
                "professionalHelpRecommended": false
              },
              "usageInstructions": ["Select the right range first."],
              "materials": [],
              "interestingFacts": []
            }
            """;

    private String envelope(String text, String finishReason) throws Exception {
        return mapper.writeValueAsString(Map.of("candidates", List.of(Map.of(
                "content", Map.of("parts", List.of(Map.of("text", text))),
                "finishReason", finishReason))));
    }

    private AiErrorKind failureKindOf(String geminiResponse) {
        return assertThrows(AiProviderException.class, () -> service.parseAnalysis(geminiResponse)).getKind();
    }

    @Test
    void parsesAValidReport() throws Exception {
        AnalysisResponse report = service.parseAnalysis(envelope(REPORT_JSON, "STOP"));

        assertEquals("Digital multimeter", report.objectName());
        assertEquals(Confidence.HIGH, report.identification().confidence());
        assertEquals("Check the CAT rating", report.safety().precautions().get(0));
    }

    @Test
    void acceptsJsonWrappedInCodeFences() throws Exception {
        AnalysisResponse report = service.parseAnalysis(envelope("```json\n" + REPORT_JSON + "\n```", "STOP"));

        assertEquals("Digital multimeter", report.objectName());
    }

    @Test
    void malformedModelOutputIsBadResponse() throws Exception {
        assertEquals(AiErrorKind.BAD_RESPONSE, failureKindOf(envelope("this is not json at all", "STOP")));
    }

    @Test
    void reportWithoutObjectNameIsBadResponse() throws Exception {
        assertEquals(AiErrorKind.BAD_RESPONSE, failureKindOf(envelope("{\"category\": \"x\"}", "STOP")));
    }

    @Test
    void emptyCandidatesIsBadResponse() {
        assertEquals(AiErrorKind.BAD_RESPONSE, failureKindOf("{\"candidates\": []}"));
    }

    @Test
    void truncatedOutputIsBadResponse() throws Exception {
        assertEquals(AiErrorKind.BAD_RESPONSE, failureKindOf(envelope("{\"objectName\": \"Cut o", "MAX_TOKENS")));
    }

    @Test
    void blockedPromptIsBlocked() {
        assertEquals(AiErrorKind.BLOCKED,
                failureKindOf("{\"promptFeedback\": {\"blockReason\": \"SAFETY\"}}"));
    }

    @Test
    void requestContainsImageAndSchemaButNeverTheApiKey() throws Exception {
        ValidatedImage image = new ValidatedImage(new byte[]{1, 2, 3}, "image/jpeg");
        String json = mapper.writeValueAsString(service.buildAnalysisRequest(image));

        assertTrue(json.contains("\"inline_data\""));
        assertTrue(json.contains("image/jpeg"));
        assertTrue(json.contains("responseJsonSchema"));
        assertFalse(json.contains("test-key"));
    }

    @Test
    void missingApiKeyFailsFastWithoutCallingTheNetwork() {
        GeminiVisionService noKey = new GeminiVisionService(mapper, "", "test-model", 5, "http://localhost:1");
        AiProviderException ex = assertThrows(AiProviderException.class,
                () -> noKey.analyze(new ValidatedImage(new byte[]{1}, "image/jpeg")));

        assertEquals(AiErrorKind.UNAVAILABLE, ex.getKind());
    }
}
