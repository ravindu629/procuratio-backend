package com.ceybertech.pdfmedemo.template;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Database access for pdf_templates. Spring Data writes the SQL from the method names.
 */
public interface TemplateRepository extends JpaRepository<PdfTemplate, UUID> {

	/** Templates for the list screen, newest first, without the large template JSON. */
	List<SummaryView> findAllByOrderByUpdatedAtDesc();

	/** Only the columns the list needs (Spring Data selects just these). */
	interface SummaryView {

		UUID getId();

		String getName();

		String getDescription();

		List<String> getTags();

		Integer getVersion();

		Instant getCreatedAt();

		Instant getUpdatedAt();

	}

}
