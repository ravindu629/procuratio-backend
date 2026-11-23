package com.ceybertech.pdfmedemo.template.dto;

import java.time.Instant;
import java.util.UUID;

/** A template version without its design JSON, for lists and the version history. */
public record VersionSummary(
		UUID id,
		int versionNo,
		String status,
		String changeNote,
		String contentHash,
		String pdfmeVersion,
		Instant publishedAt,
		UUID publishedBy,
		Instant createdAt,
		UUID createdBy,
		Instant updatedAt,
		UUID updatedBy,
		int rowVersion) {
}
