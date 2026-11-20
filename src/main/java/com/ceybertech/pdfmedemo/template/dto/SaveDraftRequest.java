package com.ceybertech.pdfmedemo.template.dto;

import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

/**
 * Body of PUT /api/templates/{id}/draft.
 * {@code draftRowVersion} is the draft's rowVersion the user loaded, or null when there was no draft.
 */
public record SaveDraftRequest(
		@NotNull(message = "Template is required") JsonNode template,
		JsonNode sampleInputs,
		Integer draftRowVersion) {
}
