package com.catechesis.backend.church;

import com.catechesis.backend.church.dto.ChurchResponse;
import com.catechesis.backend.church.dto.CreateChurchRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/churches")
public class ChurchController {

    private final ChurchService churchService;

    public ChurchController(ChurchService churchService) {
        this.churchService = churchService;
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ChurchResponse> createChurch(
            @Valid @RequestBody CreateChurchRequest request) {

        Church created = churchService.createChurch(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ChurchResponse.from(created));
    }
}