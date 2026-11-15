package com.ceybertech.pdfmedemo.template;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ceybertech.pdfmedemo.common.ApiException;
import com.ceybertech.pdfmedemo.template.dto.TemplateRecord;
import com.ceybertech.pdfmedemo.template.dto.TemplateRequest;
import com.ceybertech.pdfmedemo.template.dto.TemplateSummary;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The rules for saving templates: validation, "not found", and the version check that stops
 * two people overwriting each other's changes.
 */
@Service
@Transactional(readOnly = true)
public class TemplateService {

	private final TemplateRepository repository;

	private final JsonMapper json;

	public TemplateService(TemplateRepository repository, JsonMapper json) {
		this.repository = repository;
		this.json = json;
	}

	public List<TemplateSummary> list() {
		return repository.findAllByOrderByUpdatedAtDesc()
			.stream()
			.map(v -> new TemplateSummary(v.getId(), v.getName(), v.getDescription(), v.getTags(), v.getVersion(),
					v.getCreatedAt(), v.getUpdatedAt()))
			.toList();
	}

	public TemplateRecord get(UUID id) {
		return toRecord(findOrThrow(id));
	}

	@Transactional
	public TemplateRecord create(TemplateRequest request) {
		PdfTemplate entity = new PdfTemplate();
		apply(entity, request);
		return toRecord(repository.saveAndFlush(entity));
	}

	@Transactional
	public TemplateRecord update(UUID id, TemplateRequest request) {
		if (request.version() == null) {
			throw ApiException.badRequest("Version is required when updating a template.");
		}
		PdfTemplate entity = findOrThrow(id);
		// The user edited an older version: someone else has saved since then.
		if (!entity.getVersion().equals(request.version())) {
			throw ApiException.conflict("This template was changed somewhere else. Reload it before saving.");
		}
		apply(entity, request);
		// saveAndFlush writes now, so the new version number and updatedAt are in the response.
		return toRecord(repository.saveAndFlush(entity));
	}

	@Transactional
	public void delete(UUID id) {
		repository.delete(findOrThrow(id));
	}

	// ---------- helpers ----------

	private PdfTemplate findOrThrow(UUID id) {
		return repository.findById(id)
			.orElseThrow(() -> ApiException.notFound("Template not found. It may have been deleted."));
	}

	/** Copies the request into the entity after checking the JSON. */
	private void apply(PdfTemplate entity, TemplateRequest request) {
		validateTemplate(request.template());
		JsonNode inputs = request.sampleInputs() == null ? json.createArrayNode() : request.sampleInputs();
		validateInputs(inputs);

		entity.setName(request.name().trim());
		entity.setDescription(request.description() == null ? "" : request.description().trim());
		entity.setTags(request.tags() == null ? List.of() : request.tags().stream().map(String::trim).distinct().toList());
		entity.setTemplate(json.writeValueAsString(request.template()));
		entity.setSampleInputs(json.writeValueAsString(inputs));
	}

	/** A pdfme template must be an object with "basePdf" and a "schemas" list of pages. */
	private void validateTemplate(JsonNode template) {
		if (!template.isObject()) {
			throw ApiException.badRequest("Template must be a JSON object.");
		}
		JsonNode basePdf = template.get("basePdf");
		if (basePdf == null || basePdf.isNull()) {
			throw ApiException.badRequest("Template must have a basePdf.");
		}
		JsonNode schemas = template.get("schemas");
		if (schemas == null || !schemas.isArray()) {
			throw ApiException.badRequest("Template must have a schemas list.");
		}
		for (JsonNode page : schemas) {
			if (!page.isArray()) {
				throw ApiException.badRequest("Each page in schemas must be a list of fields.");
			}
		}
	}

	/** pdfme inputs: a list of objects whose values are all text. */
	private void validateInputs(JsonNode inputs) {
		if (!inputs.isArray()) {
			throw ApiException.badRequest("sampleInputs must be a list.");
		}
		for (JsonNode row : inputs) {
			if (!row.isObject()) {
				throw ApiException.badRequest("Each item in sampleInputs must be an object.");
			}
			for (var field : row.properties()) {
				if (!field.getValue().isString()) {
					throw ApiException.badRequest("sampleInputs value '" + field.getKey() + "' must be text.");
				}
			}
		}
	}

	private TemplateRecord toRecord(PdfTemplate e) {
		return new TemplateRecord(e.getId(), e.getName(), e.getDescription(), e.getTags(), e.getVersion(),
				e.getCreatedAt(), e.getUpdatedAt(), json.readTree(e.getTemplate()), json.readTree(e.getSampleInputs()));
	}

}
