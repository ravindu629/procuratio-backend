package com.ceybertech.pdfmedemo.template;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A template's stable identity: name, document type, owner, tags and status.
 * The designs themselves are rows of {@link TemplateVersion}. One row of template.template.
 */
@Entity
@Table(schema = "template", name = "template")
@EntityListeners(AuditingEntityListener.class)
@DynamicUpdate
public class Template {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "document_type_id", nullable = false)
	private DocumentType documentType;

	@Column(nullable = false, length = 200)
	private String name;

	@Column(nullable = false)
	private String description = "";

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TemplateStatus status = TemplateStatus.DRAFT;

	/** The published version in use; null until the first publish. Must belong to this template. */
	@Column(name = "current_version_id")
	private UUID currentVersionId;

	@Column(name = "owner_user_id", nullable = false)
	private UUID ownerUserId;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false, columnDefinition = "text[]")
	private List<String> tags = new ArrayList<>();

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

	/** Optimistic locking: Hibernate adds 1 on every update and refuses to overwrite newer data. */
	@Version
	@Column(name = "row_version", nullable = false)
	private Integer rowVersion;

	/** Soft delete: set instead of removing the row, because documents may reference its versions. */
	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Column(name = "deleted_by")
	private UUID deletedBy;

	protected Template() {
	}

	public Template(DocumentType documentType, String name, String description, List<String> tags, UUID ownerUserId) {
		this.documentType = documentType;
		this.name = name;
		this.description = description;
		this.tags = new ArrayList<>(tags);
		this.ownerUserId = ownerUserId;
	}

	/** Marks the template as changed, so updated_at and updated_by move when its versions change. */
	public void touch() {
		this.updatedAt = Instant.now();
	}

	public void markDeleted(UUID userId) {
		this.deletedAt = Instant.now();
		this.deletedBy = userId;
	}

	public UUID getId() {
		return id;
	}

	public DocumentType getDocumentType() {
		return documentType;
	}

	public void setDocumentType(DocumentType documentType) {
		this.documentType = documentType;
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

	public TemplateStatus getStatus() {
		return status;
	}

	public void setStatus(TemplateStatus status) {
		this.status = status;
	}

	public UUID getCurrentVersionId() {
		return currentVersionId;
	}

	public void setCurrentVersionId(UUID currentVersionId) {
		this.currentVersionId = currentVersionId;
	}

	public UUID getOwnerUserId() {
		return ownerUserId;
	}

	public List<String> getTags() {
		return tags;
	}

	public void setTags(List<String> tags) {
		this.tags = new ArrayList<>(tags);
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public UUID getCreatedBy() {
		return createdBy;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public UUID getUpdatedBy() {
		return updatedBy;
	}

	public Integer getRowVersion() {
		return rowVersion;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

}
