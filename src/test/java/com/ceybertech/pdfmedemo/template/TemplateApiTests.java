package com.ceybertech.pdfmedemo.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.ceybertech.pdfmedemo.PostgresTestSupport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Calls the real endpoints (through MockMvc) against a real PostgreSQL,
 * checking each rule in the frontend's API_CONTRACT.md.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TemplateApiTests extends PostgresTestSupport {

	private static final String TEMPLATE = """
			{ "basePdf": { "width": 210, "height": 297, "padding": [0, 0, 0, 0] },
			  "schemas": [[ { "name": "recipientName", "type": "text",
			                  "position": { "x": 20, "y": 50 }, "width": 90, "height": 10,
			                  "content": "Mr James Carter" } ]] }
			""";

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	private String body(String name, String template, Integer version) {
		return """
				{ "name": %s, "description": "Test letter", "tags": ["Letter", "Test"],
				  "template": %s, "sampleInputs": [ { "recipientName": "Ms Priya Shah" } ] %s }
				""".formatted(json.writeValueAsString(name), template,
				version == null ? "" : ", \"version\": " + version);
	}

	private JsonNode create(String name) throws Exception {
		String response = mvc.perform(post("/api/templates").contentType(MediaType.APPLICATION_JSON)
			.content(body(name, TEMPLATE, null)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return json.readTree(response);
	}

	private ResultActions update(String id, String name, int version) throws Exception {
		return mvc.perform(put("/api/templates/" + id).contentType(MediaType.APPLICATION_JSON)
			.content(body(name, TEMPLATE, version)));
	}

	@Test
	void createReturnsTheSavedTemplateWithVersionOne() throws Exception {
		mvc.perform(post("/api/templates").contentType(MediaType.APPLICATION_JSON).content(body("Riverside letter", TEMPLATE, null)))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/templates/")))
			.andExpect(jsonPath("$.id").isNotEmpty())
			.andExpect(jsonPath("$.name").value("Riverside letter"))
			.andExpect(jsonPath("$.tags[0]").value("Letter"))
			.andExpect(jsonPath("$.version").value(1))
			.andExpect(jsonPath("$.createdAt").isString())
			.andExpect(jsonPath("$.template.basePdf.width").value(210))
			.andExpect(jsonPath("$.template.schemas[0][0].name").value("recipientName"))
			.andExpect(jsonPath("$.sampleInputs[0].recipientName").value("Ms Priya Shah"));
	}

	@Test
	void getReturnsTheTemplateAndListReturnsSummariesWithoutJson() throws Exception {
		String id = create("Payment receipt").get("id").asString();

		mvc.perform(get("/api/templates/" + id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Payment receipt"))
			.andExpect(jsonPath("$.template.schemas").isArray());

		mvc.perform(get("/api/templates"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].id", hasItem(id)))
			.andExpect(jsonPath("$[0].template").doesNotExist())
			.andExpect(jsonPath("$[0].sampleInputs").doesNotExist());
	}

	@Test
	void listShowsTheMostRecentlyUpdatedFirst() throws Exception {
		JsonNode older = create("Older");
		create("Newer");
		update(older.get("id").asString(), "Older (edited)", 1).andExpect(status().isOk());

		mvc.perform(get("/api/templates"))
			.andExpect(jsonPath("$[0].name").value("Older (edited)"));
	}

	@Test
	void updateWithTheCurrentVersionSavesAndIncreasesTheVersion() throws Exception {
		String id = create("Draft").get("id").asString();

		update(id, "Final", 1)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Final"))
			.andExpect(jsonPath("$.version").value(2));

		mvc.perform(get("/api/templates/" + id)).andExpect(jsonPath("$.version").value(2));
	}

	@Test
	void updateWithAnOldVersionIsRejectedWith409() throws Exception {
		String id = create("Shared template").get("id").asString();
		update(id, "Saved by Sarah", 1).andExpect(status().isOk());

		// Tom still has version 1 open and tries to save it.
		update(id, "Saved by Tom", 1)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("This template was changed somewhere else. Reload it before saving."));

		mvc.perform(get("/api/templates/" + id)).andExpect(jsonPath("$.name").value("Saved by Sarah"));
	}

	@Test
	void updateWithoutVersionIsRejected() throws Exception {
		String id = create("No version").get("id").asString();
		mvc.perform(put("/api/templates/" + id).contentType(MediaType.APPLICATION_JSON).content(body("x", TEMPLATE, null)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Version is required when updating a template."));
	}

	@Test
	void deleteRemovesTheTemplate() throws Exception {
		String id = create("To delete").get("id").asString();

		mvc.perform(delete("/api/templates/" + id)).andExpect(status().isNoContent());
		mvc.perform(get("/api/templates/" + id))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Template not found. It may have been deleted."));
		mvc.perform(get("/api/templates")).andExpect(jsonPath("$[*].id", not(hasItem(id))));
	}

	@Test
	void unknownOrInvalidIdsGiveClearErrors() throws Exception {
		mvc.perform(get("/api/templates/6f1c2a4e-0000-0000-0000-000000000000")).andExpect(status().isNotFound());
		mvc.perform(get("/api/templates/not-a-uuid"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Invalid value for 'id'."));
		mvc.perform(delete("/api/templates/6f1c2a4e-0000-0000-0000-000000000000")).andExpect(status().isNotFound());
	}

	@Test
	void invalidRequestsAreRejectedWith400AndAMessage() throws Exception {
		// empty name
		mvc.perform(post("/api/templates").contentType(MediaType.APPLICATION_JSON).content(body(" ", TEMPLATE, null)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Name is required"));
		// template without schemas
		mvc.perform(post("/api/templates").contentType(MediaType.APPLICATION_JSON)
			.content(body("Bad", "{ \"basePdf\": { \"width\": 210, \"height\": 297, \"padding\": [0,0,0,0] } }", null)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Template must have a schemas list."));
		// not JSON at all
		mvc.perform(post("/api/templates").contentType(MediaType.APPLICATION_JSON).content("{ broken"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("The request body is not valid JSON."));
		// sample input value that is not text
		mvc.perform(post("/api/templates").contentType(MediaType.APPLICATION_JSON).content("""
				{ "name": "Bad inputs", "template": %s, "sampleInputs": [ { "amount": 918.00 } ] }
				""".formatted(TEMPLATE)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("sampleInputs value 'amount' must be text."));
	}

	@Test
	void textValuesAreStoredExactlyAsSent() throws Exception {
		String response = mvc.perform(post("/api/templates").contentType(MediaType.APPLICATION_JSON).content("""
				{ "name": "Amounts", "template": %s, "sampleInputs": [ { "amount": "918.00", "postcode": "BS1 6XY" } ] }
				""".formatted(TEMPLATE)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		JsonNode saved = json.readTree(response);
		assertThat(saved.at("/sampleInputs/0/amount").asString()).isEqualTo("918.00");
	}

	@Test
	void theReactAppIsAllowedToCallTheApi() throws Exception {
		mvc.perform(options("/api/templates")
			.header("Origin", "http://localhost:5173")
			.header("Access-Control-Request-Method", "PUT"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));

		mvc.perform(options("/api/templates")
			.header("Origin", "http://evil.example")
			.header("Access-Control-Request-Method", "PUT"))
			.andExpect(status().isForbidden());
	}

}
