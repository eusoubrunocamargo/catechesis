package com.catechesis.backend.church;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChurchRepository extends JpaRepository<Church, UUID> {
}