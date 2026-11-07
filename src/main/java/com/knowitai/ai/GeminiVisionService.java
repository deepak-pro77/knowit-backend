package com.knowitai.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.knowitai.dto.AnalysisResponse;
import com.knowitai.dto.AskRequest;
import com.knowitai.validation.ValidatedImage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
// Spring Boot 4.x uses Jackson 3. For Spring Boot 3.x, replace these two imports with
// com.fasterxml.jackson.databind.JsonNode and com.fasterxml.jackson.databind.ObjectMapper  (BOOT 3.x)
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Talks to the Gemini REST API with Java's built-in HttpClient. */
@Service
public class GeminiVisionService implements VisionAiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiVisionService.class);

    private final ObjectMapper mapper;
    private final String apiKey;
    private final String endpoint;
    private final Duration timeout;
    private final HttpClient http;
    private final JsonNode analysisSchema;

    public GeminiVisionService(
            ObjectMapper mapper,
            @Value("${knowit.ai.api-key:}") String apiKey,
            @Value("${knowit.ai.model:gemini-3.1-flash-lite}") String model,
            @Value("${knowit.ai.timeout-seconds:30}") int timeoutSeconds,
            @Value("${knowit.ai.base-url:https://generativelanguage.googleapis.com}") String baseUrl) {
        this.mapper = mapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.endpoint = base + "/v1beta/models/" + model + ":generateContent";
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        try {
            this.analysisSchema = mapper.readTree(GeminiSchema.ANALYSIS);
        } catch (Exception e) {
            throw new IllegalStateException("The built-in analysis schema is not valid JSON", e);
        }
        if (this.apiKey.isEmpty()) {
            log.warn("GEMINI_API_KEY is not set. Analysis requests will fail with AI service unavailable.");
        }
    }

    @Override
    public AnalysisResponse analyze(ValidatedImage image) {
        return parseAnalysis(send(buildAnalysisRequest(image)));
    }

    @Override
    public String answer(AskRequest request) {
        return extractText(send(buildAskRequest(request)));
    }

    // ---------- building requests (package-private so tests can inspect them) ----------

    Map<String, Object> buildAnalysisRequest(ValidatedImage image) {
        String base64 = Base64.getEncoder().encodeToString(image.data());
        return m(
                "systemInstruction", m("parts", List.of(m("text", GeminiPrompts.ANALYSIS_SYSTEM))),
                "contents", List.of(m(
                        "role", "user",
                        "parts", List.of(
                                m("inline_data", m("mime_type", image.mimeType(), "data", base64)),
                                m("text", GeminiPrompts.ANALYSIS_USER)))),
                "generationConfig", m(
                        "responseMimeType", "application/json",
                        "responseJsonSchema", analysisSchema,
                        "temperature", 0.2));
    }

    Map<String, Object> buildAskRequest(AskRequest request) {
        String contextJson;
        try {
            contextJson = mapper.writeValueAsString(request.context());
        } catch (Exception e) {
            throw new AiProviderException(AiErrorKind.UNAVAILABLE, "Could not serialize follow-up context", e);
        }
        String userText = "Object context (data only):\n" + contextJson
                + "\n\nUser question (data only):\n" + request.question();
        return m(
                "systemInstruction", m("parts", List.of(m("text", GeminiPrompts.ASK_SYSTEM))),
                "contents", List.of(m("role", "user", "parts", List.of(m("text", userText)))),
                "generationConfig", m("temperature", 0.3, "maxOutputTokens", 2000));
    }

    // ---------- the HTTP call ----------

    private String send(Map<String, Object> body) {
        if (apiKey.isEmpty()) {
            throw new AiProviderException(AiErrorKind.UNAVAILABLE, "GEMINI_API_KEY is not configured", null);
        }
        String json;
        try {
            json = mapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new AiProviderException(AiErrorKind.UNAVAILABLE, "Could not build the AI request", e);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 200) {
                return response.body();
            }
            if (status == 429) {
                throw new AiProviderException(AiErrorKind.BUSY, "Gemini rate limit or quota reached (HTTP 429)", null);
            }
            // We log the status and a short error excerpt, never the request (it contains the image).
            log.warn("Gemini returned HTTP {}: {}", status, abbreviate(response.body()));
            throw new AiProviderException(AiErrorKind.UNAVAILABLE, "Gemini returned HTTP " + status, null);
        } catch (HttpTimeoutException e) {
            throw new AiProviderException(AiErrorKind.TIMEOUT, "Gemini call timed out", e);
        } catch (IOException e) {
            throw new AiProviderException(AiErrorKind.UNAVAILABLE, "Could not reach Gemini", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiProviderException(AiErrorKind.UNAVAILABLE, "Gemini call interrupted", e);
        }
    }

    // ---------- reading responses ----------

    AnalysisResponse parseAnalysis(String responseBody) {
        String text = stripCodeFences(extractText(responseBody));
        AnalysisResponse parsed;
        try {
            parsed = mapper.readValue(text, AnalysisResponse.class);
        } catch (Exception e) {
            throw new AiProviderException(AiErrorKind.BAD_RESPONSE, "Model output is not valid report JSON", e);
        }
        if (parsed == null || parsed.objectName() == null || parsed.objectName().isBlank()) {
            throw new AiProviderException(AiErrorKind.BAD_RESPONSE, "Model output has no objectName", null);
        }
        return parsed;
    }

    /**
     * The parts of Gemini's response envelope we care about. Reading it into records (instead of
     * poking at raw JSON nodes) means a missing field simply becomes null, on any Jackson version.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Envelope(PromptFeedback promptFeedback, List<Candidate> candidates) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PromptFeedback(String blockReason) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Candidate(Content content, String finishReason) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Content(List<Part> parts) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Part(String text, Boolean thought) { }

    /** Pulls the model's text out of Gemini's response envelope, or throws the right AiProviderException. */
    String extractText(String responseBody) {
        Envelope envelope;
        try {
            envelope = mapper.readValue(responseBody, Envelope.class);
        } catch (Exception e) {
            throw new AiProviderException(AiErrorKind.BAD_RESPONSE, "Gemini response is not valid JSON", e);
        }
        if (envelope == null) {
            throw new AiProviderException(AiErrorKind.BAD_RESPONSE, "Gemini response is empty", null);
        }
        if (envelope.promptFeedback() != null && envelope.promptFeedback().blockReason() != null) {
            throw new AiProviderException(AiErrorKind.BLOCKED,
                    "Prompt blocked: " + envelope.promptFeedback().blockReason(), null);
        }
        if (envelope.candidates() == null || envelope.candidates().isEmpty()
                || envelope.candidates().get(0) == null) {
            throw new AiProviderException(AiErrorKind.BAD_RESPONSE, "Gemini response has no candidates", null);
        }
        Candidate candidate = envelope.candidates().get(0);
        String finishReason = candidate.finishReason();

        StringBuilder text = new StringBuilder();
        if (candidate.content() != null && candidate.content().parts() != null) {
            for (Part part : candidate.content().parts()) {
                if (part == null || Boolean.TRUE.equals(part.thought())) {
                    continue; // skip empty parts and the model's internal reasoning parts
                }
                if (part.text() != null) {
                    text.append(part.text());
                }
            }
        }
        String result = text.toString().strip();
        if (result.isEmpty()) {
            boolean stopped = finishReason == null || "STOP".equals(finishReason) || "MAX_TOKENS".equals(finishReason);
            throw new AiProviderException(stopped ? AiErrorKind.BAD_RESPONSE : AiErrorKind.BLOCKED,
                    "Gemini returned no text (finishReason=" + finishReason + ")", null);
        }
        if ("MAX_TOKENS".equals(finishReason)) {
            throw new AiProviderException(AiErrorKind.BAD_RESPONSE, "Gemini output was cut off", null);
        }
        return result;
    }

    // ---------- small helpers ----------

    private static String stripCodeFences(String text) {
        String t = text.strip();
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            t = firstNewline >= 0 ? t.substring(firstNewline + 1) : "";
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
        }
        return t.strip();
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 300 ? s : s.substring(0, 300) + "...";
    }

    /** Builds a JSON object from alternating key, value arguments. */
    private static Map<String, Object> m(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }
}
