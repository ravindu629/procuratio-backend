package com.ceybertech.pdfmedemo.template;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ceybertech.pdfmedemo.template.dto.DocumentTypeDto;

/** GET /api/document-types: the active document types, for the "document type" picker. */
@RestController
@RequestMapping("/api/document-types")
public class DocumentTypeController {

	private final TemplateService service;

	public DocumentTypeController(TemplateService service) {
		this.service = service;
	}

	@GetMapping
	public List<DocumentTypeDto> list() {
		return service.documentTypes();
	}

}
