package com.catechesis.backend.common.security;

import com.catechesis.backend.common.tenancy.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TEMPORARY diagnostic endpoint. Will be removed before S01 closes.
 *
 * <p>Exists only to verify that {@link SecurityContext} and
 * {@link TenantContext} are correctly registered and injectable.
 * Without an authentication filter populating them (S01-08), every
 * call returns "anonymous".
 */
@RestController
public class WhoAmIController {

    private final SecurityContext securityContext;
    private final TenantContext tenantContext;

    public WhoAmIController(SecurityContext securityContext,
                            TenantContext tenantContext) {
        this.securityContext = securityContext;
        this.tenantContext = tenantContext;
    }

    @GetMapping("/whoami")
    public String whoami() {
        if (securityContext.isSuperAdmin()) {
            return "super-admin: " + securityContext.currentSuperAdminId().orElseThrow();
        }
        if (securityContext.currentCatechistId().isPresent()) {
            return "catechist: " + securityContext.currentCatechistId().orElseThrow()
                    + ", role: " + securityContext.currentRole().orElseThrow()
                    + ", church: " + tenantContext.churchIdOptional().orElse(null);
        }
        return "anonymous";
    }
}