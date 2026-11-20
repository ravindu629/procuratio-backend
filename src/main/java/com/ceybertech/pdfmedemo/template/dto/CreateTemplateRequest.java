package com.ceybertech.pdfmedemo.template.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

/** Body of POST /api/templates. Creates the template and its draft version 1. */
public record CreateTemplateRequest(
		@NotNull(message = "Document type is required") UUID documentTypeId,
		@NotBlank(message = "Name is required") @Size(max = 200, message = "Name must be 200 characters or fewer") String name,
		@Size(max = 2000, message = "Description must be 2000 characters or fewer") String description,
		@Size(max = 20, message = "A template can have at most 20 tags") List<@Size(max = 50, message = "Tags must be 50 characters or fewer") String> tags,
		@NotNull(message = "Template is required") JsonNode template,
		JsonNode sampleInputs) {
}
