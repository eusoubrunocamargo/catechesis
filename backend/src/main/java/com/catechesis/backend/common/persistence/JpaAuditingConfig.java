package com.catechesis.backend.common.persistence;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/*
 * Activates Spring Data JPA auditing — specifically the
 * {@code @CreatedDate} and {@code @LastModifiedDate} annotations on
 * entities. Without this, those annotations are silently ignored.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}