package com.ceybertech.pdfmedemo.template.dto;

import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

/** A template version with its design: the pdfme template and sample inputs. */
public record VersionRecord(
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
		int rowVersion,
		JsonNode template,
		JsonNode sampleInputs) {
}
