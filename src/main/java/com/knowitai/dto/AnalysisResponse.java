package com.knowitai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * The structured report returned for an analyzed object.
 * Records are immutable data holders: Java generates the constructor,
 * getters and equals/hashCode for us.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalysisResponse(
        String objectName,
        String category,
        String description,
        Identification identification,
        String howItWorks,
        List<String> advantages,
        List<String> disadvantages,
        List<String> commonUses,
        Safety safety,
        List<String> usageInstructions,
        List<String> materials,
        List<String> interestingFacts,
        List<Source> sources) {

    /** How sure the identification is. This is the model's own judgement, NOT a calibrated probability. */
    public enum Confidence { HIGH, MEDIUM, LOW }

    /**
     * UNKNOWN means "not enough evidence to assess". LOW means "lower risk than other items",
     * never "safe". The app must not declare anything safe from a photo alone.
     */
    public enum RiskLevel { UNKNOWN, LOW, MODERATE, HIGH }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Identification(
            Confidence confidence,
            List<String> visibleEvidence,      // what can literally be seen in the photo
            List<String> inferredDetails,      // what the model is guessing from it
            List<String> alternatives,
            List<String> missingInformation) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Safety(
            RiskLevel riskLevel,
            List<String> knownHazards,
            List<String> precautions,
            List<String> limitations,
            boolean professionalHelpRecommended) { }

    /** Only sources that were actually retrieved and checked may appear here. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Source(String title, String url, boolean verified) { }
}
