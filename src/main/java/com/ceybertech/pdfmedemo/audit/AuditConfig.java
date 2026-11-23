package com.ceybertech.pdfmedemo.audit;

import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Turns on Spring Data auditing: fields marked @CreatedBy, @LastModifiedBy, @CreatedDate and
 * @LastModifiedDate are filled in automatically on every save.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditor")
public class AuditConfig {

	@Bean
	AuditorAware<UUID> auditor(CurrentUser currentUser) {
		return () -> Optional.of(currentUser.id());
	}

}
