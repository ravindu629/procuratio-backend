package com.ceybertech.pdfmedemo.template;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A kind of document the business produces, such as SERVICE_CHARGE_DEMAND.
 * One row of template.document_type. Seeded by the V2 migration; read-only in the app for now.
 */
@Entity
@Table(schema = "template", name = "document_type")
public class DocumentType {

	@Id
	private UUID id;

	@Column(nullable = false, length = 50)
	private String code;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false, length = 30)
	private String category;

	@Column(nullable = false)
	private boolean active;

	protected DocumentType() {
	}

	public UUID getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public String getCategory() {
		return category;
	}

	public boolean isActive() {
		return active;
	}

}
