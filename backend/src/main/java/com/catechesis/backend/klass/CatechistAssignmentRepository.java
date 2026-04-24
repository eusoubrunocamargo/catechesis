package com.catechesis.backend.klass;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatechistAssignmentRepository extends JpaRepository<CatechistAssignment, UUID> {
}