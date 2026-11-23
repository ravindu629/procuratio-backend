package com.ceybertech.pdfmedemo.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.ceybertech.pdfmedemo.PostgresTestSupport;

/**
 * Checks the V2 migration on a separate, fresh database: V1 first, then old-style templates,
 * then V2, which must copy them into the new tables.
 */
@SpringBootTest
class TemplateMigrationTests extends PostgresTestSupport {

	@Autowired
	JdbcTemplate appDatabase;

	@Autowired
	Environment env;

	@Test
	@DisplayName("MG-01 to MG-06 V2 copies old templates into templates with a published version 1")
	void migrationCopiesOldTemplates() {
		appDatabase.execute("DROP DATABASE IF EXISTS migration_test");
		appDatabase.execute("CREATE DATABASE migration_test");
		String url = env.getRequiredProperty("spring.datasource.url").replaceFirst("/postgres\\?", "/migration_test?");
		DriverManagerDataSource dataSource = new DriverManagerDataSource(url, "postgres", "");
		JdbcTemplate db = new JdbcTemplate(dataSource);

		Flyway.configure().dataSource(dataSource).target("1").load().migrate();
		String design = "{\"basePdf\":{\"width\":210,\"height\":297,\"padding\":[0,0,0,0]},\"pdfmeVersion\":\"6.2.8\",\"schemas\":[[]]}";
		String insert = "INSERT INTO pdf_templates VALUES (?::uuid, ?, '', ?::text[], ?::jsonb, '[]', 3, ?::timestamptz, ?::timestamptz)";
		db.update(insert, "11111111-1111-1111-1111-111111111111", "Payment receipt", "{Finance,\" QR code \",Finance}",
				design, "2026-11-18T08:55:00Z", "2026-11-18T16:15:00Z");
		db.update(insert, "22222222-2222-2222-2222-222222222222", "Payment receipt", "{Finance}", design,
				"2026-11-18T10:16:00Z", "2026-11-18T10:23:00Z");
		db.update(insert, "33333333-3333-3333-3333-333333333333", "Service charge demand", "{}", design,
				"2026-11-18T08:29:00Z", "2026-11-18T11:58:00Z");

		Flyway.configure().dataSource(dataSource).load().migrate();

		// MG-01 MG-02 MG-03 MG-04
		List<Map<String, Object>> rows = db.queryForList("""
				SELECT t.id::text AS id, t.name, dt.code, t.status, array_to_string(t.tags, '|') AS tags,
				       v.version_no, v.status AS version_status
				FROM template.template t
				JOIN template.document_type dt ON dt.id = t.document_type_id
				JOIN template.template_version v ON v.id = t.current_version_id
				ORDER BY t.created_at""");
		assertThat(rows).hasSize(3);
		assertThat(rows.get(0)).containsEntry("name", "Service charge demand").containsEntry("code", "SERVICE_CHARGE_DEMAND");
		assertThat(rows.get(1)).containsEntry("id", "11111111-1111-1111-1111-111111111111")
			.containsEntry("name", "Payment receipt")
			.containsEntry("code", "PAYMENT_RECEIPT")
			.containsEntry("tags", "Finance|QR code");
		assertThat(rows.get(2)).containsEntry("name", "Payment receipt (2)");
		assertThat(rows).allSatisfy(row -> assertThat(row)
			.containsEntry("status", "ACTIVE")
			.containsEntry("version_no", 1)
			.containsEntry("version_status", "PUBLISHED"));

		// MG-05: the 5 document types are seeded
		assertThat(db.queryForObject("SELECT count(*) FROM template.document_type", Integer.class)).isEqualTo(5);

		// MG-06: running Flyway again changes nothing
		Flyway.configure().dataSource(dataSource).load().migrate();
		assertThat(db.queryForObject("SELECT count(*) FROM template.template", Integer.class)).isEqualTo(3);
	}

}
