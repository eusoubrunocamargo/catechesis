package com.catechesis.backend.klass;

import com.catechesis.backend.common.tenancy.TenantContext;
import com.catechesis.backend.klass.dto.CreateKlassRequest;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KlassService {

    private final KlassRepository klassRepository;
    private final TenantContext tenantContext;

    public KlassService(KlassRepository klassRepository,
                        TenantContext tenantContext) {
        this.klassRepository = klassRepository;
        this.tenantContext = tenantContext;
    }

    @Transactional
    public Klass createKlass(CreateKlassRequest request) {
        UUID churchId = tenantContext.requireChurchId();
        Klass klass = new Klass(churchId, request.name());
        return klassRepository.save(klass);
    }
}