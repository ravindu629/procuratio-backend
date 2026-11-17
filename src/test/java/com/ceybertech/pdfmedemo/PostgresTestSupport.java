package com.ceybertech.pdfmedemo;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * Starts one real PostgreSQL 16 for all tests (no Docker or installation needed)
 * and points Spring Boot at it. Flyway creates the tables as in production.
 */
public abstract class PostgresTestSupport {

	private static final EmbeddedPostgres POSTGRES = start();

	private static EmbeddedPostgres start() {
		try {
			EmbeddedPostgres postgres = EmbeddedPostgres.start();
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				try {
					postgres.close();
				}
				catch (IOException ignored) {
					// shutting down anyway
				}
			}));
			return postgres;
		}
		catch (IOException e) {
			throw new UncheckedIOException("Could not start embedded PostgreSQL", e);
		}
	}

	@DynamicPropertySource
	static void databaseProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
		registry.add("spring.datasource.username", () -> "postgres");
		registry.add("spring.datasource.password", () -> "");
	}

}
