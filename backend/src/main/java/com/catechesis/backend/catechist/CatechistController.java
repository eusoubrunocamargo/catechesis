package com.catechesis.backend.catechist;

import com.catechesis.backend.catechist.dto.CatechistResponse;
import com.catechesis.backend.catechist.dto.CreateLeadRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/churches/{churchId}/catechists")
public class CatechistController {

    private final CatechistService catechistService;

    public CatechistController(CatechistService catechistService) {
        this.catechistService = catechistService;
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<CatechistResponse> createFirstLead(
            @PathVariable UUID churchId,
            @Valid @RequestBody CreateLeadRequest request) {

        Catechist created = catechistService.createFirstLead(churchId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CatechistResponse.from(created));
    }
}