package com.ceybertech.pdfmedemo.template.dto;

import java.util.UUID;

/** One item of GET /api/document-types. */
public record DocumentTypeDto(UUID id, String code, String name, String category, boolean active) {
}
