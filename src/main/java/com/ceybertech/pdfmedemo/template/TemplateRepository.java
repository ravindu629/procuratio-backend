package com.ceybertech.pdfmedemo.template;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Database access for template.template. Deleted (soft-deleted) templates are never returned.
 */
public interface TemplateRepository extends JpaRepository<Template, UUID> {

	/** All templates that are not deleted, newest first, with their document type loaded. */
	@Query("select t from Template t join fetch t.documentType where t.deletedAt is null order by t.updatedAt desc")
	List<Template> findAllNotDeleted();

	Optional<Template> findByIdAndDeletedAtIsNull(UUID id);

	boolean existsByDocumentTypeIdAndNameIgnoreCaseAndDeletedAtIsNull(UUID documentTypeId, String name);

	boolean existsByDocumentTypeIdAndNameIgnoreCaseAndDeletedAtIsNullAndIdNot(UUID documentTypeId, String name,
			UUID id);

}
