package com.ceybertech.pdfmedemo.template.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One item of GET /api/templates. Versions are summaries, so the list stays small. */
public record TemplateSummary(
		UUID id,
		String name,
		String description,
		List<String> tags,
		DocumentTypeRef documentType,
		String status,
		VersionSummary currentVersion,
		VersionSummary draftVersion,
		UUID ownerUserId,
		Instant createdAt,
		UUID createdBy,
		Instant updatedAt,
		UUID updatedBy,
		int rowVersion) {
}
