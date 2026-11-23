package com.ceybertech.pdfmedemo.template.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One template with its current and draft versions, designs included. */
public record TemplateDetail(
		UUID id,
		String name,
		String description,
		List<String> tags,
		DocumentTypeRef documentType,
		String status,
		VersionRecord currentVersion,
		VersionRecord draftVersion,
		UUID ownerUserId,
		Instant createdAt,
		UUID createdBy,
		Instant updatedAt,
		UUID updatedBy,
		int rowVersion) {
}
