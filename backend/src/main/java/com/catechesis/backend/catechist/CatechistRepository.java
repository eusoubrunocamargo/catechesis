package com.catechesis.backend.catechist;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatechistRepository extends JpaRepository<Catechist, UUID> {
}