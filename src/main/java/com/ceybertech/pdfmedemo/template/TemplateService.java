package com.ceybertech.pdfmedemo.template;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ceybertech.pdfmedemo.audit.CurrentUser;
import com.ceybertech.pdfmedemo.common.ApiException;
import com.ceybertech.pdfmedemo.template.dto.CreateTemplateRequest;
import com.ceybertech.pdfmedemo.template.dto.DocumentTypeDto;
import com.ceybertech.pdfmedemo.template.dto.DocumentTypeRef;
import com.ceybertech.pdfmedemo.template.dto.PublishRequest;
import com.ceybertech.pdfmedemo.template.dto.SaveDraftRequest;
import com.ceybertech.pdfmedemo.template.dto.TemplateDetail;
import com.ceybertech.pdfmedemo.template.dto.TemplateSummary;
import com.ceybertech.pdfmedemo.template.dto.UpdateTemplateRequest;
import com.ceybertech.pdfmedemo.template.dto.VersionRecord;
import com.ceybertech.pdfmedemo.template.dto.VersionSummary;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The template rules: drafts, publishing, version history, names, archive and soft delete.
 * The API contract is the frontend's src/pdfme-demo/api/API_CONTRACT.md.
 */
@Service
@Transactional(readOnly = true)
public class TemplateService {

	static final String TEMPLATE_CHANGED = "This template was changed somewhere else. Reload it before saving.";

	static final String DRAFT_CHANGED = "This draft was changed somewhere else. Reload it before saving.";

	private final TemplateRepository templates;

	private final TemplateVersionRepository versions;

	private final DocumentTypeRepository documentTypes;

	private final CurrentUser currentUser;

	private final JsonMapper json;

	public TemplateService(TemplateRepository templates, TemplateVersionRepository versions,
			DocumentTypeRepository documentTypes, CurrentUser currentUser, JsonMapper json) {
		this.templates = templates;
		this.versions = versions;
		this.documentTypes = documentTypes;
		this.currentUser = currentUser;
		this.json = json;
	}

	// ---------- reading ----------

	public List<DocumentTypeDto> documentTypes() {
		return documentTypes.findByActiveTrueOrderByName()
			.stream()
			.map(t -> new DocumentTypeDto(t.getId(), t.getCode(), t.getName(), t.getCategory(), t.isActive()))
			.toList();
	}

	/** Templates for My Workspace, newest first. All filters are optional. */
	public List<TemplateSummary> list(UUID documentTypeId, String tag, UUID ownerUserId, boolean includeArchived) {
		List<Template> found = templates.findAllNotDeleted()
			.stream()
			.filter(t -> includeArchived || t.getStatus() != TemplateStatus.ARCHIVED)
			.filter(t -> documentTypeId == null || t.getDocumentType().getId().equals(documentTypeId))
			.filter(t -> ownerUserId == null || t.getOwnerUserId().equals(ownerUserId))
			.filter(t -> tag == null || tag.isBlank() || t.getTags().stream().anyMatch(x -> x.equalsIgnoreCase(tag.trim())))
			.toList();
		if (found.isEmpty()) {
			return List.of();
		}

		// Two queries for all the versions the list needs, instead of two per template.
		List<UUID> currentIds = found.stream().map(Template::getCurrentVersionId).filter(Objects::nonNull).toList();
		Map<UUID, VersionSummaryView> currentById = versions.findByIdIn(currentIds)
			.stream()
			.collect(Collectors.toMap(VersionSummaryView::getId, Function.identity()));
		Map<UUID, VersionSummaryView> draftByTemplate = versions
			.findByTemplateIdInAndStatus(found.stream().map(Template::getId).toList(), VersionStatus.DRAFT)
			.stream()
			.collect(Collectors.toMap(VersionSummaryView::getTemplateId, Function.identity()));

		return found.stream()
			.map(t -> new TemplateSummary(t.getId(), t.getName(), t.getDescription(), t.getTags(), typeRef(t),
					t.getStatus().name(), summary(currentById.get(t.getCurrentVersionId())),
					summary(draftByTemplate.get(t.getId())), t.getOwnerUserId(), t.getCreatedAt(), t.getCreatedBy(),
					t.getUpdatedAt(), t.getUpdatedBy(), t.getRowVersion()))
			.toList();
	}

	public TemplateDetail get(UUID id) {
		return detail(findTemplate(id));
	}

	public List<VersionSummary> versions(UUID templateId) {
		findTemplate(templateId);
		return versions.findByTemplateIdOrderByVersionNoDesc(templateId).stream().map(this::summary).toList();
	}

	public VersionRecord version(UUID templateId, int versionNo) {
		findTemplate(templateId);
		return versions.findByTemplateIdAndVersionNo(templateId, versionNo)
			.map(this::record)
			.orElseThrow(() -> ApiException.notFound("Version " + versionNo + " not found."));
	}

	// ---------- creating and changing templates ----------

	@Transactional
	public TemplateDetail create(CreateTemplateRequest request) {
		DocumentType type = activeType(request.documentTypeId());
		String name = request.name().trim();
		if (templates.existsByDocumentTypeIdAndNameIgnoreCaseAndDeletedAtIsNull(type.getId(), name)) {
			throw nameTaken(name);
		}
		Design design = design(request.template(), request.sampleInputs());

		Template template = templates.saveAndFlush(new Template(type, name, clean(request.description()),
				cleanTags(request.tags()), currentUser.id()));
		TemplateVersion draft = new TemplateVersion(template.getId(), 1);
		draft.setDesign(design.content(), design.hash(), design.pdfmeVersion(), design.sampleInputs());
		versions.saveAndFlush(draft);
		return detail(template);
	}

	@Transactional
	public TemplateDetail update(UUID id, UpdateTemplateRequest request) {
		Template template = findTemplate(id);
		checkRowVersion(template.getRowVersion(), request.rowVersion(), TEMPLATE_CHANGED);

		DocumentType type = request.documentTypeId() == null ? template.getDocumentType()
				: activeType(request.documentTypeId());
		String name = template.getName();
		if (request.name() != null) {
			name = request.name().trim();
			if (name.isEmpty()) {
				throw ApiException.badRequest("Name is required");
			}
		}
		boolean nameOrTypeChanged = !name.equalsIgnoreCase(template.getName())
				|| !type.getId().equals(template.getDocumentType().getId());
		if (nameOrTypeChanged && templates.existsByDocumentTypeIdAndNameIgnoreCaseAndDeletedAtIsNullAndIdNot(
				type.getId(), name, template.getId())) {
			throw nameTaken(name);
		}
		template.setDocumentType(type);
		template.setName(name);
		if (request.description() != null) {
			template.setDescription(clean(request.description()));
		}
		if (request.tags() != null) {
			template.setTags(cleanTags(request.tags()));
		}
		if (request.status() != null) {
			template.setStatus(newStatus(template, request.status()));
		}
		return detail(templates.saveAndFlush(template));
	}

	/** Soft delete: the row and its versions are kept, but the template is gone from the app. */
	@Transactional
	public void delete(UUID id, int rowVersion) {
		Template template = findTemplate(id);
		checkRowVersion(template.getRowVersion(), rowVersion, TEMPLATE_CHANGED);
		template.markDeleted(currentUser.id());
		templates.saveAndFlush(template);
	}

	// ---------- drafts and publishing ----------

	/** Saves the design into the draft, starting a new draft version when there is none. */
	@Transactional
	public TemplateDetail saveDraft(UUID id, SaveDraftRequest request) {
		Template template = findTemplate(id);
		Design design = design(request.template(), request.sampleInputs());

		TemplateVersion draft = versions.findByTemplateIdAndStatus(id, VersionStatus.DRAFT).orElse(null);
		if (draft != null) {
			if (request.draftRowVersion() == null) {
				throw ApiException.conflict("A draft was already started somewhere else. Reload the template before saving.");
			}
			checkRowVersion(draft.getRowVersion(), request.draftRowVersion(), DRAFT_CHANGED);
		}
		else {
			if (request.draftRowVersion() != null) {
				throw ApiException.conflict("The draft no longer exists. Reload the template before saving.");
			}
			draft = new TemplateVersion(id, versions.findMaxVersionNo(id) + 1);
		}
		draft.setDesign(design.content(), design.hash(), design.pdfmeVersion(), design.sampleInputs());
		versions.saveAndFlush(draft);

		template.touch();
		return detail(templates.saveAndFlush(template));
	}

	/** Discards the draft. The template keeps its published version. */
	@Transactional
	public void discardDraft(UUID id, int draftRowVersion) {
		Template template = findTemplate(id);
		TemplateVersion draft = versions.findByTemplateIdAndStatus(id, VersionStatus.DRAFT)
			.orElseThrow(() -> ApiException.notFound("There is no draft to discard."));
		if (template.getCurrentVersionId() == null) {
			throw ApiException.conflict(
					"This template has never been published. Delete the template instead of discarding its only draft.");
		}
		checkRowVersion(draft.getRowVersion(), draftRowVersion, DRAFT_CHANGED);
		versions.delete(draft);
		versions.flush();

		template.touch();
		templates.saveAndFlush(template);
	}

	/** Publishes the draft: it becomes the current version and the previous one is retired. */
	@Transactional
	public TemplateDetail publish(UUID id, PublishRequest request) {
		Template template = findTemplate(id);
		TemplateVersion draft = versions.findByTemplateIdAndStatus(id, VersionStatus.DRAFT)
			.orElseThrow(() -> ApiException.conflict("There is no draft to publish."));
		checkRowVersion(draft.getRowVersion(), request.draftRowVersion(), DRAFT_CHANGED);

		if (template.getCurrentVersionId() != null) {
			TemplateVersion previous = versions.findById(template.getCurrentVersionId()).orElseThrow();
			previous.retire();
			versions.saveAndFlush(previous);
		}
		draft.publish(currentUser.id(), request.changeNote() == null ? null : request.changeNote().trim());
		versions.saveAndFlush(draft);

		template.setCurrentVersionId(draft.getId());
		if (template.getStatus() == TemplateStatus.DRAFT) {
			template.setStatus(TemplateStatus.ACTIVE);
		}
		template.touch();
		return detail(templates.saveAndFlush(template));
	}

	// ---------- helpers ----------

	private Template findTemplate(UUID id) {
		return templates.findByIdAndDeletedAtIsNull(id)
			.orElseThrow(() -> ApiException.notFound("Template not found. It may have been deleted."));
	}

	private DocumentType activeType(UUID id) {
		return documentTypes.findByIdAndActiveTrue(id)
			.orElseThrow(() -> ApiException.badRequest("Unknown or inactive document type."));
	}

	private static void checkRowVersion(Integer stored, Integer sent, String message) {
		if (sent == null || !sent.equals(stored)) {
			throw ApiException.conflict(message);
		}
	}

	private static ApiException nameTaken(String name) {
		return ApiException.conflict("A template named \"" + name + "\" already exists for this document type.");
	}

	private static TemplateStatus newStatus(Template template, String status) {
		return switch (status.trim().toUpperCase(Locale.ROOT)) {
			case "ARCHIVED" -> TemplateStatus.ARCHIVED;
			case "ACTIVE" -> {
				if (template.getCurrentVersionId() == null) {
					throw ApiException.badRequest("Only a template with a published version can be made active.");
				}
				yield TemplateStatus.ACTIVE;
			}
			default -> throw ApiException.badRequest("Status can only be set to ARCHIVED or ACTIVE.");
		};
	}

	private static String clean(String text) {
		return text == null ? "" : text.trim();
	}

	/** Trims tags, drops empty ones and repeats (ignoring case), keeping the first spelling. */
	private static List<String> cleanTags(List<String> tags) {
		if (tags == null) {
			return List.of();
		}
		Map<String, String> unique = new LinkedHashMap<>();
		for (String tag : tags) {
			if (tag != null && !tag.isBlank()) {
				unique.putIfAbsent(tag.trim().toLowerCase(Locale.ROOT), tag.trim());
			}
		}
		return new ArrayList<>(unique.values());
	}

	/** A checked design, ready to store. */
	private record Design(String content, String hash, String pdfmeVersion, String sampleInputs) {
	}

	private Design design(JsonNode template, JsonNode sampleInputs) {
		validateTemplate(template);
		JsonNode inputs = sampleInputs == null || sampleInputs.isNull() ? json.createArrayNode() : sampleInputs;
		validateInputs(inputs);
		String content = json.writeValueAsString(template);
		JsonNode version = template.get("pdfmeVersion");
		String pdfmeVersion = version != null && version.isString() && version.asString().length() <= 20
				? version.asString() : null;
		return new Design(content, sha256(content), pdfmeVersion, json.writeValueAsString(inputs));
	}

	/** A pdfme template must be an object with "basePdf" and a "schemas" list of pages. */
	private static void validateTemplate(JsonNode template) {
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
	private static void validateInputs(JsonNode inputs) {
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

	private static String sha256(String text) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available", e);
		}
	}

	// ---------- mapping to API shapes ----------

	private static DocumentTypeRef typeRef(Template t) {
		DocumentType type = t.getDocumentType();
		return new DocumentTypeRef(type.getId(), type.getCode(), type.getName());
	}

	private TemplateDetail detail(Template t) {
		VersionRecord current = t.getCurrentVersionId() == null ? null
				: versions.findById(t.getCurrentVersionId()).map(this::record).orElse(null);
		VersionRecord draft = versions.findByTemplateIdAndStatus(t.getId(), VersionStatus.DRAFT)
			.map(this::record)
			.orElse(null);
		return new TemplateDetail(t.getId(), t.getName(), t.getDescription(), t.getTags(), typeRef(t),
				t.getStatus().name(), current, draft, t.getOwnerUserId(), t.getCreatedAt(), t.getCreatedBy(),
				t.getUpdatedAt(), t.getUpdatedBy(), t.getRowVersion());
	}

	private VersionSummary summary(VersionSummaryView v) {
		if (v == null) {
			return null;
		}
		return new VersionSummary(v.getId(), v.getVersionNo(), v.getStatus().name(), v.getChangeNote(),
				v.getContentHash(), v.getPdfmeVersion(), v.getPublishedAt(), v.getPublishedBy(), v.getCreatedAt(),
				v.getCreatedBy(), v.getUpdatedAt(), v.getUpdatedBy(), v.getRowVersion());
	}

	private VersionRecord record(TemplateVersion v) {
		return new VersionRecord(v.getId(), v.getVersionNo(), v.getStatus().name(), v.getChangeNote(),
				v.getContentHash(), v.getPdfmeVersion(), v.getPublishedAt(), v.getPublishedBy(), v.getCreatedAt(),
				v.getCreatedBy(), v.getUpdatedAt(), v.getUpdatedBy(), v.getRowVersion(), json.readTree(v.getContent()),
				json.readTree(v.getSampleInputs()));
	}

}
