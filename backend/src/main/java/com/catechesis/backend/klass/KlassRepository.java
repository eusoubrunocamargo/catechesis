package com.catechesis.backend.klass;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KlassRepository extends JpaRepository<Klass, UUID> {
    boolean existsByPublicSlug(String publicSlug);
}