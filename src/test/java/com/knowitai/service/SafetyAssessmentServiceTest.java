package com.knowitai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.knowitai.dto.AnalysisResponse;
import com.knowitai.dto.AnalysisResponse.Confidence;
import com.knowitai.dto.AnalysisResponse.Identification;
import com.knowitai.dto.AnalysisResponse.RiskLevel;
import com.knowitai.dto.AnalysisResponse.Safety;
import com.knowitai.dto.AnalysisResponse.Source;
import java.util.List;
import org.junit.jupiter.api.Test;

class SafetyAssessmentServiceTest {

    private final SafetyAssessmentService service = new SafetyAssessmentService();

    private static AnalysisResponse report(String name, Confidence confidence, Safety safety,
                                           List<String> usage, List<Source> sources) {
        return new AnalysisResponse(name, "Category", "A description.",
                new Identification(confidence, List.of("visible thing"), List.of(), List.of(), List.of()),
                "It works somehow.", List.of("good"), List.of("bad"), List.of("use"),
                safety, usage, List.of(), List.of(), sources);
    }

    private static Safety safety(RiskLevel risk, List<String> hazards, List<String> precautions) {
        return new Safety(risk, hazards, precautions, List.of(), false);
    }

    @Test
    void lowConfidenceForcesUnknownRiskAndRemovesInstructions() {
        AnalysisResponse result = service.apply(report("Mystery box", Confidence.LOW,
                safety(RiskLevel.LOW, List.of("none"), List.of("be careful")),
                List.of("Plug it in and press the button."), List.of()));

        assertEquals(RiskLevel.UNKNOWN, result.safety().riskLevel());
        assertTrue(result.usageInstructions().isEmpty());
        assertTrue(result.safety().knownHazards().isEmpty(), "must not invent hazards for an unidentified object");
        assertTrue(result.safety().limitations().contains(SafetyAssessmentService.UNCERTAIN_LIMITATION));
        assertFalse(result.safety().precautions().isEmpty());
        assertFalse(result.identification().missingInformation().isEmpty());
    }

    @Test
    void missingIdentificationIsTreatedAsLowConfidence() {
        AnalysisResponse in = new AnalysisResponse("Thing", null, null, null, null, null, null, null,
                null, null, null, null, null);
        AnalysisResponse result = service.apply(in);

        assertEquals(Confidence.LOW, result.identification().confidence());
        assertEquals(RiskLevel.UNKNOWN, result.safety().riskLevel());
        assertTrue(result.advantages().isEmpty());
    }

    @Test
    void batteryReportedAsLowRiskIsRaisedToModerateWithPrecaution() {
        AnalysisResponse result = service.apply(report("Lithium-ion power bank", Confidence.HIGH,
                safety(RiskLevel.LOW, List.of(), List.of()), List.of("Charge it with its own cable."), List.of()));

        assertEquals(RiskLevel.MODERATE, result.safety().riskLevel());
        assertTrue(result.safety().precautions().stream().anyMatch(p -> p.toLowerCase().contains("swollen")));
    }

    @Test
    void electricalEquipmentGetsNeverAssumeSafeLimitation() {
        AnalysisResponse result = service.apply(report("Extension cord", Confidence.HIGH,
                safety(RiskLevel.LOW, List.of(), List.of()), List.of(), List.of()));

        assertEquals(RiskLevel.MODERATE, result.safety().riskLevel());
        assertTrue(result.safety().limitations().stream()
                .anyMatch(l -> l.contains("must not be assumed safe to operate")));
    }

    @Test
    void harmlessObjectKeepsLowRiskButStillGetsStandingLimitation() {
        AnalysisResponse result = service.apply(report("Wooden spoon", Confidence.HIGH,
                safety(RiskLevel.LOW, List.of(), List.of("Keep it away from open flames.")),
                List.of("Stir food."), List.of()));

        assertEquals(RiskLevel.LOW, result.safety().riskLevel());
        assertTrue(result.safety().limitations().contains(SafetyAssessmentService.STANDING_LIMITATION));
        assertEquals(List.of("Stir food."), result.usageInstructions());
    }

    @Test
    void highRiskRecommendsProfessionalHelp() {
        AnalysisResponse result = service.apply(report("Gas regulator", Confidence.MEDIUM,
                safety(RiskLevel.HIGH, List.of("gas leak"), List.of()), List.of(), List.of()));

        assertTrue(result.safety().professionalHelpRecommended());
    }

    @Test
    void modelSuppliedSourcesAreAlwaysDropped() {
        AnalysisResponse result = service.apply(report("Wooden spoon", Confidence.HIGH,
                safety(RiskLevel.LOW, List.of(), List.of()), List.of(),
                List.of(new Source("Totally real source", "https://example.invalid", true))));

        assertTrue(result.sources().isEmpty());
    }
}
