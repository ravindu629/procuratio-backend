package com.ceybertech.pdfmedemo.template.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of PATCH /api/templates/{id}. Fields left out (null) are not changed.
 * {@code rowVersion} must be the template's rowVersion that the user loaded.
 */
public record UpdateTemplateRequest(
		@NotNull(message = "rowVersion is required") Integer rowVersion,
		@Size(max = 200, message = "Name must be 200 characters or fewer") String name,
		@Size(max = 2000, message = "Description must be 2000 characters or fewer") String description,
		@Size(max = 20, message = "A template can have at most 20 tags") List<@Size(max = 50, message = "Tags must be 50 characters or fewer") String> tags,
		UUID documentTypeId,
		String status) {
}
