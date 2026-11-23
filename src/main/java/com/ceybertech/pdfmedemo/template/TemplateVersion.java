package com.ceybertech.pdfmedemo.template;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * One design of a template. DRAFT can be edited; PUBLISHED and RETIRED are locked
 * (a database trigger refuses changes). One row of template.template_version.
 */
@Entity
@Table(schema = "template", name = "template_version")
@EntityListeners(AuditingEntityListener.class)
@DynamicUpdate
public class TemplateVersion implements VersionSummaryView {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "template_id", nullable = false, updatable = false)
	private UUID templateId;

	@Column(name = "version_no", nullable = false, updatable = false)
	private int versionNo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private VersionStatus status = VersionStatus.DRAFT;

	/** The pdfme template JSON. Kept as text in Java and stored as JSONB in PostgreSQL. */
	@Column(nullable = false, columnDefinition = "jsonb")
	@ColumnTransformer(write = "?::jsonb")
	private String content;

	@Column(name = "sample_inputs", nullable = false, columnDefinition = "jsonb")
	@ColumnTransformer(write = "?::jsonb")
	private String sampleInputs = "[]";

	@Column(name = "pdfme_version", length = 20)
	private String pdfmeVersion;

	/** SHA-256 (hex) of content. */
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "content_hash", nullable = false, length = 64)
	private String contentHash;

	@Column(name = "change_note", nullable = false)
	private String changeNote = "";

	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(name = "published_by")
	private UUID publishedBy;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@CreatedBy
	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@LastModifiedBy
	@Column(name = "updated_by", nullable = false)
	private UUID updatedBy;

	@Version
	@Column(name = "row_version", nullable = false)
	private Integer rowVersion;

	protected TemplateVersion() {
	}

	/** A new DRAFT version of a template. */
	public TemplateVersion(UUID templateId, int versionNo) {
		this.templateId = templateId;
		this.versionNo = versionNo;
	}

	/** Replaces the design. Only for drafts; the service checks, and the database trigger enforces it. */
	public void setDesign(String content, String contentHash, String pdfmeVersion, String sampleInputs) {
		this.content = content;
		this.contentHash = contentHash;
		this.pdfmeVersion = pdfmeVersion;
		this.sampleInputs = sampleInputs;
	}

	public void publish(UUID userId, String changeNote) {
		this.status = VersionStatus.PUBLISHED;
		this.publishedAt = Instant.now();
		this.publishedBy = userId;
		if (changeNote != null) {
			this.changeNote = changeNote;
		}
	}

	public void retire() {
		this.status = VersionStatus.RETIRED;
	}

	@Override
	public UUID getId() {
		return id;
	}

	@Override
	public UUID getTemplateId() {
		return templateId;
	}

	@Override
	public int getVersionNo() {
		return versionNo;
	}

	@Override
	public VersionStatus getStatus() {
		return status;
	}

	public String getContent() {
		return content;
	}

	public String getSampleInputs() {
		return sampleInputs;
	}

	@Override
	public String getPdfmeVersion() {
		return pdfmeVersion;
	}

	@Override
	public String getContentHash() {
		return contentHash;
	}

	@Override
	public String getChangeNote() {
		return changeNote;
	}

	@Override
	public Instant getPublishedAt() {
		return publishedAt;
	}

	@Override
	public UUID getPublishedBy() {
		return publishedBy;
	}

	@Override
	public Instant getCreatedAt() {
		return createdAt;
	}

	@Override
	public UUID getCreatedBy() {
		return createdBy;
	}

	@Override
	public Instant getUpdatedAt() {
		return updatedAt;
	}

	@Override
	public UUID getUpdatedBy() {
		return updatedBy;
	}

	@Override
	public Integer getRowVersion() {
		return rowVersion;
	}

}
