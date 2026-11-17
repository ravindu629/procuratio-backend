package com.ceybertech.pdfmedemo.template.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One item in GET /api/templates. No template JSON, so the list stays small and fast. */
public record TemplateSummary(
		UUID id,
		String name,
		String description,
		List<String> tags,
		int version,
		Instant createdAt,
		Instant updatedAt) {
}
