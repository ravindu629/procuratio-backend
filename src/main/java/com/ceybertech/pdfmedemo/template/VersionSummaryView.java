package com.ceybertech.pdfmedemo.template;

import java.time.Instant;
import java.util.UUID;

/**
 * The columns of a version without its (possibly large) design JSON.
 * Spring Data selects only these columns for list screens; {@link TemplateVersion} implements it too.
 */
public interface VersionSummaryView {

	UUID getId();

	UUID getTemplateId();

	int getVersionNo();

	VersionStatus getStatus();

	String getChangeNote();

	String getContentHash();

	String getPdfmeVersion();

	Instant getPublishedAt();

	UUID getPublishedBy();

	Instant getCreatedAt();

	UUID getCreatedBy();

	Instant getUpdatedAt();

	UUID getUpdatedBy();

	Integer getRowVersion();

}
