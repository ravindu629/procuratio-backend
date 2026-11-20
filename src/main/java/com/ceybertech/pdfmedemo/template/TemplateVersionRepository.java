package com.ceybertech.pdfmedemo.template;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Database access for template.template_version. Methods returning {@link VersionSummaryView}
 * select only the summary columns, never the large design JSON.
 */
public interface TemplateVersionRepository extends JpaRepository<TemplateVersion, UUID> {

	Optional<TemplateVersion> findByTemplateIdAndStatus(UUID templateId, VersionStatus status);

	Optional<TemplateVersion> findByTemplateIdAndVersionNo(UUID templateId, int versionNo);

	@Query("select coalesce(max(v.versionNo), 0) from TemplateVersion v where v.templateId = :templateId")
	int findMaxVersionNo(UUID templateId);

	List<VersionSummaryView> findByIdIn(Collection<UUID> ids);

	List<VersionSummaryView> findByTemplateIdInAndStatus(Collection<UUID> templateIds, VersionStatus status);

	List<VersionSummaryView> findByTemplateIdOrderByVersionNoDesc(UUID templateId);

}
