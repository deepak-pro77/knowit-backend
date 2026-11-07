package com.knowitai.service;

import com.knowitai.dto.AnalysisResponse;
import com.knowitai.dto.AnalysisResponse.Confidence;
import com.knowitai.dto.AnalysisResponse.Identification;
import com.knowitai.dto.AnalysisResponse.RiskLevel;
import com.knowitai.dto.AnalysisResponse.Safety;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Enforces KnowIt AI's safety rules on whatever the model returned.
 * The model can suggest; this class decides what is allowed to reach the user.
 */
@Service
public class SafetyAssessmentService {

    static final String STANDING_LIMITATION =
            "This assessment comes from a photo and general knowledge. It cannot confirm that the object is "
                    + "safe. Check its label, manual or manufacturer.";
    static final String UNCERTAIN_LIMITATION =
            "The object could not be identified reliably, so no object-specific safety assessment is given.";
    static final String ASK_FOR_LABEL =
            "A clearer photo, or a photo of its label, model number or markings";

    private static final List<String> CONSERVATIVE_PRECAUTIONS = List.of(
            "Do not operate, plug in, open, taste or smell the object until you have identified it.",
            "Keep it away from children and pets until it is identified.",
            "Look for a label, model number or manufacturer markings and check them against its manual.");

    private record Rule(List<String> keywords, String precaution, String limitation) {
        boolean matches(String text) {
            return keywords.stream().anyMatch(text::contains);
        }
    }

    private static final List<Rule> RULES = List.of(
            new Rule(List.of("electric", "mains", "voltage", "power supply", "charger", "adapter", "appliance",
                    "extension cord", "wiring", "outlet", "power cord", "power plug"),
                    "Unplug it and keep it dry before handling. Do not open it or touch exposed wires or metal "
                            + "contacts while it is connected to power.",
                    "Electrical equipment must not be assumed safe to operate just because it looks fine."),
            new Rule(List.of("battery", "lithium", "li-ion", "power bank"),
                    "Keep batteries away from heat, water and metal objects. Do not charge, crush, puncture or "
                            + "use a battery that is swollen, hot or leaking.",
                    "A photo cannot show a battery's condition; internal damage is not visible."),
            new Rule(List.of("chemical", "cleaner", "bleach", "solvent", "pesticide", "fuel", "paint", "acid",
                    "detergent", "aerosol"),
                    "Do not taste it, mix it with other products, or let it touch skin or eyes. Read the product "
                            + "label and keep it out of reach of children.",
                    "A photo cannot show what a substance contains; follow the label and its safety data sheet."),
            new Rule(List.of("knife", "blade", "saw", "razor", "needle", "scissors", "cutter"),
                    "Cut away from your body, keep fingers clear of the edge, and store it with the edge covered.",
                    null),
            new Rule(List.of("heater", "burner", "open flame", "lighter", "stove", "soldering", "propane", "butane"),
                    "Keep it away from flammable materials, never leave it unattended while hot, and let it cool "
                            + "fully before storing.",
                    null));

    public AnalysisResponse apply(AnalysisResponse in) {
        Identification id = normalizeIdentification(in.identification());
        boolean uncertain = id.confidence() == Confidence.LOW;

        Safety safety;
        List<String> usage;
        if (uncertain) {
            // Rule: never invent a safety assessment for an object we could not identify.
            safety = new Safety(RiskLevel.UNKNOWN, List.of(), CONSERVATIVE_PRECAUTIONS,
                    List.of(UNCERTAIN_LIMITATION, STANDING_LIMITATION), false);
            usage = List.of();
            if (id.missingInformation().isEmpty()) {
                id = new Identification(id.confidence(), id.visibleEvidence(), id.inferredDetails(),
                        id.alternatives(), List.of(ASK_FOR_LABEL));
            }
        } else {
            safety = assess(in, normalizeSafety(in.safety()));
            usage = list(in.usageInstructions());
        }

        return new AnalysisResponse(
                in.objectName(), in.category(), in.description(), id, in.howItWorks(),
                list(in.advantages()), list(in.disadvantages()), list(in.commonUses()),
                safety, usage, list(in.materials()), list(in.interestingFacts()),
                // No source has been retrieved and verified yet (Phase 5), so none may be shown.
                List.of());
    }

    private Safety assess(AnalysisResponse r, Safety s) {
        String text = (nullToEmpty(r.objectName()) + " " + nullToEmpty(r.category()) + " "
                + nullToEmpty(r.description()) + " " + String.join(" ", s.knownHazards()))
                .toLowerCase(Locale.ROOT);

        RiskLevel risk = s.riskLevel();
        List<String> precautions = s.precautions();
        List<String> limitations = s.limitations();
        boolean matched = false;

        for (Rule rule : RULES) {
            if (!rule.matches(text)) {
                continue;
            }
            matched = true;
            if (!mentionsAny(precautions, rule.keywords())) {
                precautions = plus(precautions, rule.precaution());
            }
            if (rule.limitation() != null) {
                limitations = plus(limitations, rule.limitation());
            }
        }
        // A hazardous category can never be reported as merely "low" risk.
        if (matched && risk == RiskLevel.LOW) {
            risk = RiskLevel.MODERATE;
        }
        limitations = plus(limitations, STANDING_LIMITATION);
        boolean professional = s.professionalHelpRecommended() || risk == RiskLevel.HIGH;
        return new Safety(risk, s.knownHazards(), precautions, limitations, professional);
    }

    private static Identification normalizeIdentification(Identification id) {
        if (id == null) {
            return new Identification(Confidence.LOW, List.of(), List.of(), List.of(), List.of());
        }
        return new Identification(
                id.confidence() == null ? Confidence.LOW : id.confidence(),
                list(id.visibleEvidence()), list(id.inferredDetails()),
                list(id.alternatives()), list(id.missingInformation()));
    }

    private static Safety normalizeSafety(Safety s) {
        if (s == null) {
            return new Safety(RiskLevel.UNKNOWN, List.of(), List.of(), List.of(), false);
        }
        return new Safety(
                s.riskLevel() == null ? RiskLevel.UNKNOWN : s.riskLevel(),
                list(s.knownHazards()), list(s.precautions()), list(s.limitations()),
                s.professionalHelpRecommended());
    }

    private static boolean mentionsAny(List<String> lines, List<String> keywords) {
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            for (String keyword : keywords) {
                if (lower.contains(keyword)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<String> list(List<String> in) {
        return in == null ? List.of() : in.stream().filter(Objects::nonNull).toList();
    }

    private static List<String> plus(List<String> base, String item) {
        if (base.contains(item)) {
            return base;
        }
        List<String> out = new ArrayList<>(base);
        out.add(item);
        return List.copyOf(out);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
