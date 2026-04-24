package com.catechesis.backend.superadmin;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SuperAdminRepository extends JpaRepository<SuperAdmin, UUID> {
}