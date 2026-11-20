package com.ceybertech.pdfmedemo.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.ceybertech.pdfmedemo.PostgresTestSupport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Calls the real endpoints (through MockMvc) against a real PostgreSQL, checking the rules in the
 * frontend's API_CONTRACT.md. Test names start with the scenario IDs from the "Template Scenarios" doc.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TemplateApiTests extends PostgresTestSupport {

	static final String SYSTEM_USER = "00000000-0000-0000-0000-000000000000";

	static final String LETTER = """
			{ "basePdf": { "width": 210, "height": 297, "padding": [0, 0, 0, 0] },
			  "pdfmeVersion": "6.2.8",
			  "schemas": [[ { "name": "recipientName", "type": "text",
			                  "position": { "x": 20, "y": 50 }, "width": 90, "height": 10,
			                  "content": "Mr James Carter" } ]] }
			""";

	static final String LETTER_V2 = LETTER.replace("Mr James Carter", "Ms Priya Shah");

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Autowired
	JdbcTemplate jdbc;

	// ---------- helpers ----------

	/** Tests share one database, so every template gets a unique name. */
	static String unique(String name) {
		return name + " " + UUID.randomUUID().toString().substring(0, 8);
	}

	JsonNode read(ResultActions result) throws Exception {
		return json.readTree(result.andReturn().getResponse().getContentAsString());
	}

	String documentTypeId(String code) throws Exception {
		for (JsonNode type : read(mvc.perform(get("/api/document-types")))) {
			if (type.get("code").asString().equals(code)) {
				return type.get("id").asString();
			}
		}
		throw new AssertionError("No document type " + code);
	}

	ResultActions postTemplate(String typeCode, String name, String template) throws Exception {
		String typeId = typeCode == null ? null : documentTypeId(typeCode);
		return mvc.perform(post("/api/templates").contentType(MediaType.APPLICATION_JSON).content("""
				{ "documentTypeId": %s, "name": %s, "description": "Test letter", "tags": [" Letter ", "Test", "Letter"],
				  "template": %s, "sampleInputs": [ { "recipientName": "Ms Priya Shah" } ] }
				""".formatted(typeId == null ? "null" : "\"" + typeId + "\"", json.writeValueAsString(name), template)));
	}

	JsonNode create(String name) throws Exception {
		return read(postTemplate("GENERAL_LETTER", name, LETTER).andExpect(status().isCreated()));
	}

	ResultActions saveDraft(String id, String template, Integer draftRowVersion) throws Exception {
		return mvc.perform(put("/api/templates/" + id + "/draft").contentType(MediaType.APPLICATION_JSON).content("""
				{ "template": %s, "sampleInputs": [], "draftRowVersion": %s }
				""".formatted(template, draftRowVersion)));
	}

	ResultActions publish(String id, int draftRowVersion, String changeNote) throws Exception {
		return mvc.perform(post("/api/templates/" + id + "/publish").contentType(MediaType.APPLICATION_JSON).content("""
				{ "draftRowVersion": %d, "changeNote": %s }
				""".formatted(draftRowVersion, changeNote == null ? "null" : json.writeValueAsString(changeNote))));
	}

	ResultActions patchTemplate(String id, String body) throws Exception {
		return mvc.perform(patch("/api/templates/" + id).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	static int draftRow(JsonNode detail) {
		return detail.at("/draftVersion/rowVersion").asInt();
	}

	static int row(JsonNode detail) {
		return detail.get("rowVersion").asInt();
	}

	/** Creates a template and publishes its first draft. */
	JsonNode createPublished(String name) throws Exception {
		JsonNode created = create(name);
		return read(publish(created.get("id").asString(), draftRow(created), "First version").andExpect(status().isOk()));
	}

	// ---------- document types ----------

	@Test
	@DisplayName("DT-01 the 5 seeded document types are listed")
	void documentTypesAreListed() throws Exception {
		mvc.perform(get("/api/document-types"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(5)))
			.andExpect(jsonPath("$[*].code", hasItem("SERVICE_CHARGE_DEMAND")))
			.andExpect(jsonPath("$[*].code", hasItem("PAYMENT_RECEIPT")))
			.andExpect(jsonPath("$[*].code", hasItem("GENERAL_LETTER")))
			.andExpect(jsonPath("$[*].code", hasItem("INSPECTION_REPORT")))
			.andExpect(jsonPath("$[*].code", hasItem("OTHER")));
	}

	@Test
	@DisplayName("DT-02 a template needs a known document type")
	void documentTypeIsRequired() throws Exception {
		postTemplate(null, unique("No type"), LETTER)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Document type is required"));

		mvc.perform(post("/api/templates").contentType(MediaType.APPLICATION_JSON).content("""
				{ "documentTypeId": "6f1c2a4e-0000-0000-0000-000000000000", "name": "Unknown type", "template": %s }
				""".formatted(LETTER)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Unknown or inactive document type."));
	}

	// ---------- creating and saving ----------

	@Test
	@DisplayName("CR-01 creating a template makes a DRAFT with draft version 1, owned by the current user")
	void createMakesADraftVersionOne() throws Exception {
		String name = unique("Riverside letter");
		postTemplate("GENERAL_LETTER", name, LETTER)
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/templates/")))
			.andExpect(jsonPath("$.name").value(name))
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.documentType.code").value("GENERAL_LETTER"))
			.andExpect(jsonPath("$.currentVersion").isEmpty())
			.andExpect(jsonPath("$.draftVersion.versionNo").value(1))
			.andExpect(jsonPath("$.draftVersion.status").value("DRAFT"))
			.andExpect(jsonPath("$.draftVersion.pdfmeVersion").value("6.2.8"))
			.andExpect(jsonPath("$.draftVersion.template.schemas[0][0].name").value("recipientName"))
			.andExpect(jsonPath("$.draftVersion.sampleInputs[0].recipientName").value("Ms Priya Shah"))
			.andExpect(jsonPath("$.draftVersion.contentHash").value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{64}")))
			// US-01: the backend fills in the users
			.andExpect(jsonPath("$.ownerUserId").value(SYSTEM_USER))
			.andExpect(jsonPath("$.createdBy").value(SYSTEM_USER))
			.andExpect(jsonPath("$.draftVersion.createdBy").value(SYSTEM_USER));
	}

	@Test
	@DisplayName("CR-02 CR-03 saving a draft again updates the same draft version")
	void savingADraftKeepsTheSameVersion() throws Exception {
		JsonNode created = create(unique("Draft letter"));
		String id = created.get("id").asString();

		JsonNode first = read(saveDraft(id, LETTER_V2, draftRow(created)).andExpect(status().isOk()));
		JsonNode second = read(saveDraft(id, LETTER, draftRow(first)).andExpect(status().isOk()));

		assertThat(second.at("/draftVersion/versionNo").asInt()).isEqualTo(1);
		assertThat(draftRow(second)).isGreaterThan(draftRow(first));
		assertThat(second.at("/draftVersion/template/schemas/0/0/content").asString()).isEqualTo("Mr James Carter");
		mvc.perform(get("/api/templates/" + id + "/versions")).andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	@DisplayName("CR-04 CR-05 names must be 1 to 200 characters")
	void nameIsValidated() throws Exception {
		postTemplate("GENERAL_LETTER", " ", LETTER)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Name is required"));
		postTemplate("GENERAL_LETTER", "x".repeat(201), LETTER)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Name must be 200 characters or fewer"));
	}

	@Test
	@DisplayName("CR-06 CR-07 names are unique per document type, ignoring case")
	void namesAreUniquePerDocumentType() throws Exception {
		String name = unique("Demand");
		create(name);

		postTemplate("GENERAL_LETTER", name.toUpperCase(), LETTER)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value(
					"A template named \"" + name.toUpperCase() + "\" already exists for this document type."));

		postTemplate("PAYMENT_RECEIPT", name, LETTER).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("CR-08 tags are trimmed and de-duplicated")
	void tagsAreCleanedUp() throws Exception {
		JsonNode created = create(unique("Tagged"));
		assertThat(created.get("tags").toString()).isEqualTo("[\"Letter\",\"Test\"]");
	}

	// ---------- publishing and versioning ----------

	@Test
	@DisplayName("PV-01 publishing the first draft makes version 1 current and the template ACTIVE")
	void publishFirstDraft() throws Exception {
		JsonNode created = create(unique("To publish"));

		publish(created.get("id").asString(), draftRow(created), "First version")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.draftVersion").isEmpty())
			.andExpect(jsonPath("$.currentVersion.versionNo").value(1))
			.andExpect(jsonPath("$.currentVersion.status").value("PUBLISHED"))
			.andExpect(jsonPath("$.currentVersion.changeNote").value("First version"))
			.andExpect(jsonPath("$.currentVersion.publishedAt").isString())
			// US-03
			.andExpect(jsonPath("$.currentVersion.publishedBy").value(SYSTEM_USER));
	}

	@Test
	@DisplayName("PV-02 PV-03 PV-04 editing a published template makes draft version 2; publishing it retires version 1")
	void editPublishedTemplateMakesANewVersion() throws Exception {
		JsonNode published = createPublished(unique("Versioned"));
		String id = published.get("id").asString();

		// PV-02: no draft yet, so draftRowVersion is null and a new draft version 2 is created
		JsonNode withDraft = read(saveDraft(id, LETTER_V2, null).andExpect(status().isOk()));
		assertThat(withDraft.at("/draftVersion/versionNo").asInt()).isEqualTo(2);
		// PV-03: the published version is still version 1, unchanged
		assertThat(withDraft.at("/currentVersion/versionNo").asInt()).isEqualTo(1);
		assertThat(withDraft.at("/currentVersion/template/schemas/0/0/content").asString()).isEqualTo("Mr James Carter");

		// PV-04
		publish(id, draftRow(withDraft), "New recipient")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.currentVersion.versionNo").value(2))
			.andExpect(jsonPath("$.draftVersion").isEmpty());

		mvc.perform(get("/api/templates/" + id + "/versions"))
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].versionNo").value(2))
			.andExpect(jsonPath("$[0].status").value("PUBLISHED"))
			.andExpect(jsonPath("$[1].versionNo").value(1))
			.andExpect(jsonPath("$[1].status").value("RETIRED"));
	}

	@Test
	@DisplayName("PV-05 PV-06 the database refuses to change or delete a published version")
	void publishedVersionsAreLocked() throws Exception {
		JsonNode published = createPublished(unique("Locked"));
		String versionId = published.at("/currentVersion/id").asString();

		assertThatThrownBy(() -> jdbc.update(
				"UPDATE template.template_version SET content = content || '{\"x\":1}'::jsonb WHERE id = ?::uuid", versionId))
			.hasMessageContaining("cannot be changed");
		assertThatThrownBy(() -> jdbc.update("DELETE FROM template.template_version WHERE id = ?::uuid", versionId))
			.hasMessageContaining("cannot be deleted");
	}

	@Test
	@DisplayName("PV-07 a second draft cannot be started while one exists")
	void onlyOneDraft() throws Exception {
		JsonNode published = createPublished(unique("One draft"));
		String id = published.get("id").asString();
		saveDraft(id, LETTER_V2, null).andExpect(status().isOk());

		// Someone else, who has not seen that draft yet, also starts one.
		saveDraft(id, LETTER, null)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("A draft was already started somewhere else. Reload the template before saving."));
	}

	@Test
	@DisplayName("PV-08 discarding a draft keeps the published version; the only draft of a new template cannot be discarded")
	void discardDraft() throws Exception {
		JsonNode published = createPublished(unique("Discard"));
		String id = published.get("id").asString();
		JsonNode withDraft = read(saveDraft(id, LETTER_V2, null).andExpect(status().isOk()));

		mvc.perform(delete("/api/templates/" + id + "/draft").param("rowVersion", String.valueOf(draftRow(withDraft))))
			.andExpect(status().isNoContent());
		mvc.perform(get("/api/templates/" + id))
			.andExpect(jsonPath("$.draftVersion").isEmpty())
			.andExpect(jsonPath("$.currentVersion.versionNo").value(1));
		mvc.perform(delete("/api/templates/" + id + "/draft").param("rowVersion", "0"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("There is no draft to discard."));

		JsonNode neverPublished = create(unique("Never published"));
		mvc.perform(delete("/api/templates/" + neverPublished.get("id").asString() + "/draft")
			.param("rowVersion", String.valueOf(draftRow(neverPublished))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value(
					"This template has never been published. Delete the template instead of discarding its only draft."));
	}

	@Test
	@DisplayName("PV-09 PV-10 version history lists summaries; one old version opens exactly as published")
	void versionHistory() throws Exception {
		JsonNode published = createPublished(unique("History"));
		String id = published.get("id").asString();
		JsonNode withDraft = read(saveDraft(id, LETTER_V2, null));
		publish(id, draftRow(withDraft), null).andExpect(status().isOk());

		mvc.perform(get("/api/templates/" + id + "/versions"))
			.andExpect(jsonPath("$[0].template").doesNotExist())
			.andExpect(jsonPath("$[0].publishedBy").value(SYSTEM_USER));
		mvc.perform(get("/api/templates/" + id + "/versions/1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("RETIRED"))
			.andExpect(jsonPath("$.template.schemas[0][0].content").value("Mr James Carter"));
		mvc.perform(get("/api/templates/" + id + "/versions/9"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Version 9 not found."));
	}

	@Test
	@DisplayName("PV-11 publishing with no draft is refused")
	void publishWithoutDraft() throws Exception {
		JsonNode published = createPublished(unique("Nothing to publish"));
		publish(published.get("id").asString(), 0, null)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("There is no draft to publish."));
	}

	// ---------- concurrent editing ----------

	@Test
	@DisplayName("ER-01 a stale draftRowVersion or rowVersion gets 409 and nothing is overwritten")
	void staleVersionsAreRefused() throws Exception {
		JsonNode created = create(unique("Shared"));
		String id = created.get("id").asString();
		saveDraft(id, LETTER_V2, draftRow(created)).andExpect(status().isOk());

		// Tom still has the first draft row version open.
		saveDraft(id, LETTER, draftRow(created))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("This draft was changed somewhere else. Reload it before saving."));
		publish(id, draftRow(created), null).andExpect(status().isConflict());

		patchTemplate(id, "{ \"rowVersion\": 999, \"name\": \"Renamed\" }")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("This template was changed somewhere else. Reload it before saving."));
	}

	// ---------- My Workspace ----------

	@Test
	@DisplayName("WS-01 WS-02 the list shows summaries, newest first, without designs")
	void listShowsSummaries() throws Exception {
		JsonNode older = createPublished(unique("Older"));
		create(unique("Newer"));
		saveDraft(older.get("id").asString(), LETTER_V2, null).andExpect(status().isOk());

		mvc.perform(get("/api/templates"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(older.get("id").asString()))
			.andExpect(jsonPath("$[0].documentType.code").value("GENERAL_LETTER"))
			.andExpect(jsonPath("$[0].currentVersion.versionNo").value(1))
			.andExpect(jsonPath("$[0].draftVersion.versionNo").value(2))
			.andExpect(jsonPath("$[0].currentVersion.template").doesNotExist())
			.andExpect(jsonPath("$[0].draftVersion.template").doesNotExist());
	}

	@Test
	@DisplayName("WS-05 rename and retag with PATCH")
	void renameAndRetag() throws Exception {
		JsonNode created = create(unique("Old name"));
		String newName = unique("New name");
		patchTemplate(created.get("id").asString(), """
				{ "rowVersion": %d, "name": %s, "tags": ["Finance"] }
				""".formatted(row(created), json.writeValueAsString(newName)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value(newName))
			.andExpect(jsonPath("$.tags[0]").value("Finance"))
			.andExpect(jsonPath("$.description").value("Test letter"));
	}

	@Test
	@DisplayName("WS-10 filter by document type and tag")
	void filters() throws Exception {
		String tag = unique("tag");
		JsonNode receipt = read(postTemplate("PAYMENT_RECEIPT", unique("Receipt"), LETTER).andExpect(status().isCreated()));
		patchTemplate(receipt.get("id").asString(), "{ \"rowVersion\": %d, \"tags\": [%s] }"
			.formatted(row(receipt), json.writeValueAsString(tag))).andExpect(status().isOk());

		mvc.perform(get("/api/templates").param("tag", tag.toUpperCase()))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].id").value(receipt.get("id").asString()));
		mvc.perform(get("/api/templates").param("documentTypeId", documentTypeId("PAYMENT_RECEIPT")))
			.andExpect(jsonPath("$[*].documentType.code", not(hasItem("GENERAL_LETTER"))));
	}

	// ---------- deleting and archiving ----------

	@Test
	@DisplayName("DL-01 DL-02 delete is a soft delete, and the name can be used again")
	void softDelete() throws Exception {
		String name = unique("To delete");
		JsonNode created = create(name);
		String id = created.get("id").asString();

		mvc.perform(delete("/api/templates/" + id).param("rowVersion", String.valueOf(row(created))))
			.andExpect(status().isNoContent());
		mvc.perform(get("/api/templates/" + id))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Template not found. It may have been deleted."));
		mvc.perform(get("/api/templates")).andExpect(jsonPath("$[*].id", not(hasItem(id))));

		// The row is kept, marked as deleted by the current user.
		assertThat(jdbc.queryForObject("SELECT deleted_by::text FROM template.template WHERE id = ?::uuid", String.class, id))
			.isEqualTo(SYSTEM_USER);

		create(name);
	}

	@Test
	@DisplayName("DL-03 archived templates are hidden unless asked for; only published ones can be restored")
	void archive() throws Exception {
		JsonNode published = createPublished(unique("Archive me"));
		String id = published.get("id").asString();

		JsonNode archived = read(patchTemplate(id, "{ \"rowVersion\": %d, \"status\": \"ARCHIVED\" }".formatted(row(published)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ARCHIVED")));
		mvc.perform(get("/api/templates")).andExpect(jsonPath("$[*].id", not(hasItem(id))));
		mvc.perform(get("/api/templates").param("includeArchived", "true")).andExpect(jsonPath("$[*].id", hasItem(id)));

		patchTemplate(id, "{ \"rowVersion\": %d, \"status\": \"ACTIVE\" }".formatted(row(archived)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ACTIVE"));

		JsonNode neverPublished = create(unique("Never published"));
		patchTemplate(neverPublished.get("id").asString(), "{ \"rowVersion\": %d, \"status\": \"DRAFT\" }".formatted(row(neverPublished)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Status can only be set to ARCHIVED or ACTIVE."));
		patchTemplate(neverPublished.get("id").asString(), "{ \"rowVersion\": %d, \"status\": \"ACTIVE\" }".formatted(row(neverPublished)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Only a template with a published version can be made active."));
	}

	// ---------- validation and errors ----------

	@Test
	@DisplayName("ER-03 unknown or invalid ids give clear errors")
	void unknownIds() throws Exception {
		mvc.perform(get("/api/templates/6f1c2a4e-0000-0000-0000-000000000000")).andExpect(status().isNotFound());
		mvc.perform(get("/api/templates/not-a-uuid"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Invalid value for 'id'."));
		mvc.perform(delete("/api/templates/6f1c2a4e-0000-0000-0000-000000000000").param("rowVersion", "0"))
			.andExpect(status().isNotFound());
		mvc.perform(delete("/api/templates/6f1c2a4e-0000-0000-0000-000000000000"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Required parameter 'rowVersion' is missing."));
	}

	@Test
	@DisplayName("ER-04 ER-05 invalid designs and bodies are refused with a message")
	void invalidBodies() throws Exception {
		postTemplate("GENERAL_LETTER", unique("Bad"), "{ \"basePdf\": { \"width\": 210, \"height\": 297, \"padding\": [0,0,0,0] } }")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Template must have a schemas list."));
		mvc.perform(post("/api/templates").contentType(MediaType.APPLICATION_JSON).content("{ broken"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("The request body is not valid JSON."));

		JsonNode created = create(unique("Inputs"));
		mvc.perform(put("/api/templates/" + created.get("id").asString() + "/draft").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{ "template": %s, "sampleInputs": [ { "amount": 918.00 } ], "draftRowVersion": %d }
					""".formatted(LETTER, draftRow(created))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("sampleInputs value 'amount' must be text."));
	}

	@Test
	@DisplayName("ER-07 the React app may call the API, including PATCH; other sites may not")
	void cors() throws Exception {
		mvc.perform(options("/api/templates/x")
			.header("Origin", "http://localhost:5173")
			.header("Access-Control-Request-Method", "PATCH"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));

		mvc.perform(options("/api/templates")
			.header("Origin", "http://evil.example")
			.header("Access-Control-Request-Method", "POST"))
			.andExpect(status().isForbidden());
	}

}
