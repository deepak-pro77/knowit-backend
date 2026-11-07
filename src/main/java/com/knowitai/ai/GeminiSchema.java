package com.knowitai.ai;

/**
 * The JSON Schema that forces the model's output into our AnalysisResponse shape.
 * Note: there is deliberately NO "sources" field. The model is not allowed to supply sources.
 */
final class GeminiSchema {

    private GeminiSchema() { }

    static final String ANALYSIS = """
            {
              "type": "object",
              "properties": {
                "objectName": {"type": "string", "description": "Most likely name of the object, or 'Unidentified object'."},
                "category": {"type": "string"},
                "description": {"type": "string", "description": "One or two plain-language sentences."},
                "identification": {
                  "type": "object",
                  "properties": {
                    "confidence": {"type": "string", "enum": ["HIGH", "MEDIUM", "LOW"]},
                    "visibleEvidence": {"type": "array", "items": {"type": "string"}},
                    "inferredDetails": {"type": "array", "items": {"type": "string"}},
                    "alternatives": {"type": "array", "items": {"type": "string"}},
                    "missingInformation": {"type": "array", "items": {"type": "string"}}
                  },
                  "required": ["confidence", "visibleEvidence", "inferredDetails", "alternatives", "missingInformation"]
                },
                "howItWorks": {"type": "string"},
                "advantages": {"type": "array", "items": {"type": "string"}},
                "disadvantages": {"type": "array", "items": {"type": "string"}},
                "commonUses": {"type": "array", "items": {"type": "string"}},
                "safety": {
                  "type": "object",
                  "properties": {
                    "riskLevel": {"type": "string", "enum": ["UNKNOWN", "LOW", "MODERATE", "HIGH"]},
                    "knownHazards": {"type": "array", "items": {"type": "string"}},
                    "precautions": {"type": "array", "items": {"type": "string"}},
                    "limitations": {"type": "array", "items": {"type": "string"}},
                    "professionalHelpRecommended": {"type": "boolean"}
                  },
                  "required": ["riskLevel", "knownHazards", "precautions", "limitations", "professionalHelpRecommended"]
                },
                "usageInstructions": {"type": "array", "items": {"type": "string"}},
                "materials": {"type": "array", "items": {"type": "string"}},
                "interestingFacts": {"type": "array", "items": {"type": "string"}}
              },
              "required": ["objectName", "category", "description", "identification", "howItWorks",
                           "advantages", "disadvantages", "commonUses", "safety", "usageInstructions",
                           "materials", "interestingFacts"]
            }
            """;
}
