package com.ceybertech.pdfmedemo.template;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ceybertech.pdfmedemo.template.dto.TemplateRecord;
import com.ceybertech.pdfmedemo.template.dto.TemplateRequest;
import com.ceybertech.pdfmedemo.template.dto.TemplateSummary;

import jakarta.validation.Valid;

/**
 * REST endpoints used by the pdfme demo frontend (see its API_CONTRACT.md).
 */
@RestController
@RequestMapping("/api/templates")
public class TemplateController {

	private final TemplateService service;

	public TemplateController(TemplateService service) {
		this.service = service;
	}

	/** GET /api/templates: all templates, newest first, without the template JSON. */
	@GetMapping
	public List<TemplateSummary> list() {
		return service.list();
	}

	/** GET /api/templates/{id}: one template with its JSON. */
	@GetMapping("/{id}")
	public TemplateRecord get(@PathVariable UUID id) {
		return service.get(id);
	}

	/** POST /api/templates: save a new template. Replies 201 Created. */
	@PostMapping
	public ResponseEntity<TemplateRecord> create(@Valid @RequestBody TemplateRequest request) {
		TemplateRecord created = service.create(request);
		return ResponseEntity.created(URI.create("/api/templates/" + created.id())).body(created);
	}

	/** PUT /api/templates/{id}: save changes. The body must include the version that was loaded. */
	@PutMapping("/{id}")
	public TemplateRecord update(@PathVariable UUID id, @Valid @RequestBody TemplateRequest request) {
		return service.update(id, request);
	}

	/** DELETE /api/templates/{id}: replies 204 No Content. */
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}

}
