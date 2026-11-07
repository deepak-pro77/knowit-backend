package com.knowitai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** A follow-up question plus a SMALL, size-limited summary of the analyzed object. */
public record AskRequest(
        @NotBlank @Size(max = 500) String question,
        @NotNull @Valid AskContext context) {

    public record AskContext(
            @NotBlank @Size(max = 200) String objectName,
            @Size(max = 200) String category,
            @Size(max = 1000) String description,
            @Size(max = 15) List<@Size(max = 300) String> knownHazards,
            @Size(max = 15) List<@Size(max = 300) String> precautions) { }
}
