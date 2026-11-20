package com.ceybertech.pdfmedemo.template;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ceybertech.pdfmedemo.template.dto.CreateTemplateRequest;
import com.ceybertech.pdfmedemo.template.dto.PublishRequest;
import com.ceybertech.pdfmedemo.template.dto.SaveDraftRequest;
import com.ceybertech.pdfmedemo.template.dto.TemplateDetail;
import com.ceybertech.pdfmedemo.template.dto.TemplateSummary;
import com.ceybertech.pdfmedemo.template.dto.UpdateTemplateRequest;
import com.ceybertech.pdfmedemo.template.dto.VersionRecord;
import com.ceybertech.pdfmedemo.template.dto.VersionSummary;

import jakarta.validation.Valid;

/**
 * Template endpoints used by Template Studio (see the frontend's API_CONTRACT.md).
 */
@RestController
@RequestMapping("/api/templates")
public class TemplateController {

	private final TemplateService service;

	public TemplateController(TemplateService service) {
		this.service = service;
	}

	/** GET /api/templates: templates for My Workspace, newest first, without designs. */
	@GetMapping
	public List<TemplateSummary> list(@RequestParam(required = false) UUID documentTypeId,
			@RequestParam(required = false) String tag, @RequestParam(required = false) UUID ownerUserId,
			@RequestParam(defaultValue = "false") boolean includeArchived) {
		return service.list(documentTypeId, tag, ownerUserId, includeArchived);
	}

	/** GET /api/templates/{id}: one template with its current and draft versions. */
	@GetMapping("/{id}")
	public TemplateDetail get(@PathVariable UUID id) {
		return service.get(id);
	}

	/** POST /api/templates: a new template with draft version 1. Replies 201 Created. */
	@PostMapping
	public ResponseEntity<TemplateDetail> create(@Valid @RequestBody CreateTemplateRequest request) {
		TemplateDetail created = service.create(request);
		return ResponseEntity.created(URI.create("/api/templates/" + created.id())).body(created);
	}

	/** PATCH /api/templates/{id}: rename, retag, change type, archive or restore. */
	@PatchMapping("/{id}")
	public TemplateDetail update(@PathVariable UUID id, @Valid @RequestBody UpdateTemplateRequest request) {
		return service.update(id, request);
	}

	/** DELETE /api/templates/{id}?rowVersion=n: soft delete. Replies 204 No Content. */
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id, @RequestParam int rowVersion) {
		service.delete(id, rowVersion);
		return ResponseEntity.noContent().build();
	}

	/** PUT /api/templates/{id}/draft: save the design into the draft. */
	@PutMapping("/{id}/draft")
	public TemplateDetail saveDraft(@PathVariable UUID id, @Valid @RequestBody SaveDraftRequest request) {
		return service.saveDraft(id, request);
	}

	/** DELETE /api/templates/{id}/draft?rowVersion=n: discard the draft. Replies 204 No Content. */
	@DeleteMapping("/{id}/draft")
	public ResponseEntity<Void> discardDraft(@PathVariable UUID id, @RequestParam int rowVersion) {
		service.discardDraft(id, rowVersion);
		return ResponseEntity.noContent().build();
	}

	/** POST /api/templates/{id}/publish: the draft becomes the current version. */
	@PostMapping("/{id}/publish")
	public TemplateDetail publish(@PathVariable UUID id, @Valid @RequestBody PublishRequest request) {
		return service.publish(id, request);
	}

	/** GET /api/templates/{id}/versions: version history, newest first, without designs. */
	@GetMapping("/{id}/versions")
	public List<VersionSummary> versions(@PathVariable UUID id) {
		return service.versions(id);
	}

	/** GET /api/templates/{id}/versions/{versionNo}: one version with its design. */
	@GetMapping("/{id}/versions/{versionNo}")
	public VersionRecord version(@PathVariable UUID id, @PathVariable int versionNo) {
		return service.version(id, versionNo);
	}

}
