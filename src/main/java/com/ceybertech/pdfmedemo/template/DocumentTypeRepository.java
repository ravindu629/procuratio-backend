package com.ceybertech.pdfmedemo.template;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Database access for template.document_type. */
public interface DocumentTypeRepository extends JpaRepository<DocumentType, UUID> {

	List<DocumentType> findByActiveTrueOrderByName();

	Optional<DocumentType> findByIdAndActiveTrue(UUID id);

}
