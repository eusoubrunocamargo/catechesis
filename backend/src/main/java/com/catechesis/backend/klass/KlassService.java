package com.catechesis.backend.klass;

import com.catechesis.backend.common.AppProperties;
import com.catechesis.backend.common.slug.SlugGenerator;
import com.catechesis.backend.common.tenancy.TenantContext;
import com.catechesis.backend.klass.dto.CreateKlassRequest;
import java.util.UUID;

import com.catechesis.backend.klass.dto.RegistrationLinkResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class KlassService {

    private final KlassRepository klassRepository;
    private final TenantContext tenantContext;
    private final SlugGenerator slugGenerator;
    private final AppProperties appProperties;

    public KlassService(
            KlassRepository klassRepository,
            TenantContext tenantContext,
            SlugGenerator slugGenerator,
            AppProperties appProperties
    )
    {
        this.klassRepository = klassRepository;
        this.tenantContext = tenantContext;
        this.slugGenerator = slugGenerator;
        this.appProperties = appProperties;
    }

    @Transactional
    public Klass createKlass(CreateKlassRequest request) {
        UUID churchId = tenantContext.requireChurchId();
        String slug = generateUniqueSlug();
        Klass klass = new Klass(
                UUID.randomUUID(),
                churchId,
                request.name(),
                slug
                );
        return klassRepository.save(klass);
    }

    @Transactional(readOnly = true)
    public RegistrationLinkResponse getRegistrationLink(UUID klassId) {
        UUID churchId = tenantContext.requireChurchId();

        Klass klass = klassRepository.findById(klassId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // Cross-tenant requests get 404, not 403, to avoid leaking the
        // existence of klasses in other churches. Same pattern as
        // Sprint 1's tenant isolation in other endpoints.
        if (!klass.getChurchId().equals(churchId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        String publicUrl = appProperties.publicBaseUrl() + "/r/" + klass.getPublicSlug();
        return new RegistrationLinkResponse(klass.getPublicSlug(), publicUrl);
    }


    private String generateUniqueSlug() {
        String slug;
        do {
            slug = slugGenerator.generate();
        } while (klassRepository.existsByPublicSlug(slug));
        return slug;
    }
}