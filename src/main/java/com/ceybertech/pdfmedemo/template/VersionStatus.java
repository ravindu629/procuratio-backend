package com.ceybertech.pdfmedemo.template;

/** A template version's status. Only DRAFT can be edited; the database enforces this. */
public enum VersionStatus {

	/** Work in progress. At most one per template. */
	DRAFT,

	/** The version in use. Locked. */
	PUBLISHED,

	/** An older published version. Locked and kept for history. */
	RETIRED

}
