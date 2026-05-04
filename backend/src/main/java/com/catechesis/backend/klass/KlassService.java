package com.catechesis.backend.klass;

import com.catechesis.backend.common.slug.SlugGenerator;
import com.catechesis.backend.common.tenancy.TenantContext;
import com.catechesis.backend.klass.dto.CreateKlassRequest;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KlassService {

    private final KlassRepository klassRepository;
    private final TenantContext tenantContext;
    private final SlugGenerator slugGenerator;

    public KlassService(
            KlassRepository klassRepository,
            TenantContext tenantContext,
            SlugGenerator slugGenerator
    )
    {
        this.klassRepository = klassRepository;
        this.tenantContext = tenantContext;
        this.slugGenerator = slugGenerator;
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

    private String generateUniqueSlug() {
        String slug;
        do {
            slug = slugGenerator.generate();
        } while (klassRepository.existsByPublicSlug(slug));
        return slug;
    }
}