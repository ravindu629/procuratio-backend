package com.ceybertech.pdfmedemo.template;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * One saved pdfme template: one row of the pdf_templates table.
 */
@Entity
@Table(name = "pdf_templates")
public class PdfTemplate {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = 200)
	private String name;

	@Column(nullable = false)
	private String description = "";

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false, columnDefinition = "text[]")
	private List<String> tags = new ArrayList<>();

	/** The pdfme template JSON. Kept as text in Java and stored as JSONB in PostgreSQL. */
	@Column(nullable = false, columnDefinition = "jsonb")
	@ColumnTransformer(write = "?::jsonb")
	private String template;

	/** pdfme inputs JSON (a list of field name -> value objects). */
	@Column(name = "sample_inputs", nullable = false, columnDefinition = "jsonb")
	@ColumnTransformer(write = "?::jsonb")
	private String sampleInputs = "[]";

	/**
	 * Optimistic locking: Hibernate adds 1 on every update and refuses to overwrite a row
	 * that someone else changed in the meantime. New templates start at version 1.
	 */
	@Version
	@Column(nullable = false)
	private Integer version = 1;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public List<String> getTags() {
		return tags;
	}

	public void setTags(List<String> tags) {
		this.tags = tags;
	}

	public String getTemplate() {
		return template;
	}

	public void setTemplate(String template) {
		this.template = template;
	}

	public String getSampleInputs() {
		return sampleInputs;
	}

	public void setSampleInputs(String sampleInputs) {
		this.sampleInputs = sampleInputs;
	}

	public Integer getVersion() {
		return version;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
