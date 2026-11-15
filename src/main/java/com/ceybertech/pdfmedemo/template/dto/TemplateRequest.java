package com.ceybertech.pdfmedemo.template.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

/**
 * Body of POST /api/templates (create) and PUT /api/templates/{id} (update).
 * For updates, {@code version} must be the version the user loaded.
 */
public record TemplateRequest(
		@NotBlank(message = "Name is required") @Size(max = 200, message = "Name must be 200 characters or fewer") String name,
		@Size(max = 2000, message = "Description must be 2000 characters or fewer") String description,
		@Size(max = 20, message = "A template can have at most 20 tags") List<@NotBlank @Size(max = 50) String> tags,
		@NotNull(message = "Template is required") JsonNode template,
		JsonNode sampleInputs,
		Integer version) {
}
