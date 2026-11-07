package com.knowitai.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knowitai.ai.AiErrorKind;
import com.knowitai.ai.AiProviderException;
import com.knowitai.ai.VisionAiService;
import com.knowitai.dto.AnalysisResponse;
import com.knowitai.dto.AnalysisResponse.Confidence;
import com.knowitai.dto.AnalysisResponse.Identification;
import com.knowitai.dto.AnalysisResponse.RiskLevel;
import com.knowitai.dto.AnalysisResponse.Safety;
import com.knowitai.dto.AskRequest;
import com.knowitai.exception.GlobalExceptionHandler;
import com.knowitai.service.ObjectAnalysisService;
import com.knowitai.service.SafetyAssessmentService;
import com.knowitai.validation.ImageValidator;
import com.knowitai.validation.ValidatedImage;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Tests the HTTP layer with a fake AI, so no network and no Gemini quota is used. */
class ObjectAnalysisControllerTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};

    /** A controllable stand-in for the real AI. */
    static class FakeVision implements VisionAiService {
        AnalysisResponse next;
        RuntimeException failure;

        @Override
        public AnalysisResponse analyze(ValidatedImage image) {
            if (failure != null) {
                throw failure;
            }
            return next;
        }

        @Override
        public String answer(AskRequest request) {
            if (failure != null) {
                throw failure;
            }
            return "Here is a simple explanation.";
        }
    }

    private FakeVision fake;
    private MockMvc mvc;

    private static AnalysisResponse report(Confidence confidence, RiskLevel risk, List<String> usage) {
        return new AnalysisResponse("Wooden spoon", "Kitchen utensil", "A spoon.",
                new Identification(confidence, List.of("bowl shape"), List.of(), List.of(), List.of()),
                "You stir with it.", List.of("Cheap"), List.of("Can stain"), List.of("Stirring"),
                new Safety(risk, List.of(), List.of(), List.of(), false),
                usage, List.of(), List.of(), List.of());
    }

    @BeforeEach
    void setUp() {
        fake = new FakeVision();
        fake.next = report(Confidence.HIGH, RiskLevel.LOW, List.of("Stir food."));
        ObjectAnalysisService service =
                new ObjectAnalysisService(new ImageValidator(1024), fake, new SafetyAssessmentService());
        mvc = MockMvcBuilders.standaloneSetup(new ObjectAnalysisController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private MockMultipartFile image(byte[] bytes) {
        return new MockMultipartFile("image", "photo.jpg", "image/jpeg", bytes);
    }

    @Test
    void analyzeReturnsReportForValidImage() throws Exception {
        mvc.perform(multipart("/api/v1/objects/analyze").file(image(JPEG)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectName").value("Wooden spoon"))
                .andExpect(jsonPath("$.safety.riskLevel").value("LOW"));
    }

    @Test
    void analyzeRejectsFileThatIsNotAnImage() throws Exception {
        mvc.perform(multipart("/api/v1/objects/analyze").file(image("just text".getBytes())))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_TYPE"));
    }

    @Test
    void analyzeRejectsOversizedImage() throws Exception {
        mvc.perform(multipart("/api/v1/objects/analyze").file(image(new byte[2000])))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @Test
    void analyzeRequiresAnImagePart() throws Exception {
        mvc.perform(multipart("/api/v1/objects/analyze"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_IMAGE"));
    }

    @Test
    void aiTimeoutBecomesGatewayTimeout() throws Exception {
        fake.failure = new AiProviderException(AiErrorKind.TIMEOUT, "timed out", null);
        mvc.perform(multipart("/api/v1/objects/analyze").file(image(JPEG)))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("TIMEOUT"));
    }

    @Test
    void malformedAiOutputBecomesBadGateway() throws Exception {
        fake.failure = new AiProviderException(AiErrorKind.BAD_RESPONSE, "not json", null);
        mvc.perform(multipart("/api/v1/objects/analyze").file(image(JPEG)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("BAD_RESPONSE"));
    }

    @Test
    void unexpectedErrorsNeverLeakDetails() throws Exception {
        fake.failure = new IllegalStateException("secret internal detail");
        mvc.perform(multipart("/api/v1/objects/analyze").file(image(JPEG)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Something went wrong. Please try again."));
    }

    @Test
    void lowConfidenceResultIsMadeSafeBeforeItIsReturned() throws Exception {
        fake.next = report(Confidence.LOW, RiskLevel.LOW, List.of("Plug it in."));
        mvc.perform(multipart("/api/v1/objects/analyze").file(image(JPEG)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.safety.riskLevel").value("UNKNOWN"))
                .andExpect(jsonPath("$.usageInstructions").isEmpty());
    }

    @Test
    void askReturnsAnAnswer() throws Exception {
        mvc.perform(post("/api/v1/objects/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "Explain it in simpler words.",
                                 "context": {"objectName": "Wooden spoon", "category": "Kitchen utensil",
                                             "description": "A spoon.", "knownHazards": [], "precautions": []}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Here is a simple explanation."));
    }

    @Test
    void askRejectsBlankQuestion() throws Exception {
        mvc.perform(post("/api/v1/objects/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"  \", \"context\": {\"objectName\": \"Spoon\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void askRejectsOverlongQuestion() throws Exception {
        String longQuestion = "x".repeat(501);
        mvc.perform(post("/api/v1/objects/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"" + longQuestion + "\", \"context\": {\"objectName\": \"Spoon\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void askRejectsMalformedJson() throws Exception {
        mvc.perform(post("/api/v1/objects/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
