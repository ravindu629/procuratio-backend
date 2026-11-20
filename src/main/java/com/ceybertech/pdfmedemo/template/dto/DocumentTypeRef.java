package com.ceybertech.pdfmedemo.template.dto;

import java.util.UUID;

/** The document type shown inside a template. */
public record DocumentTypeRef(UUID id, String code, String name) {
}
