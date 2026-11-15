package com.ceybertech.pdfmedemo.template.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

/** A full saved template, returned by GET /api/templates/{id}, POST and PUT. */
public record TemplateRecord(
		UUID id,
		String name,
		String description,
		List<String> tags,
		int version,
		Instant createdAt,
		Instant updatedAt,
		JsonNode template,
		JsonNode sampleInputs) {
}
