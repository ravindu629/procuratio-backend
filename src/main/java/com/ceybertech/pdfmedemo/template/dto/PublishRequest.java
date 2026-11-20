package com.ceybertech.pdfmedemo.template.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of POST /api/templates/{id}/publish. */
public record PublishRequest(
		@NotNull(message = "draftRowVersion is required") Integer draftRowVersion,
		@Size(max = 500, message = "Change note must be 500 characters or fewer") String changeNote) {
}
