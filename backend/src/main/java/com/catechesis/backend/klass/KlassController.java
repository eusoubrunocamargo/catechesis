package com.catechesis.backend.klass;

import com.catechesis.backend.klass.dto.CreateKlassRequest;
import com.catechesis.backend.klass.dto.KlassResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/classes")
public class KlassController {

    private final KlassService klassService;

    public KlassController(KlassService klassService) {
        this.klassService = klassService;
    }

    @PostMapping
    @PreAuthorize("hasRole('LEAD')")
    public ResponseEntity<KlassResponse> createKlass(
            @Valid @RequestBody CreateKlassRequest request) {

        Klass created = klassService.createKlass(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(KlassResponse.from(created));
    }
}