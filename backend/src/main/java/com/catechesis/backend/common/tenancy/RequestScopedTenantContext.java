package com.catechesis.backend.common.tenancy;

import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

/**
 * Default implementation of {@link TenantContext}. Request-scoped:
 * a fresh instance per HTTP request, populated by a filter and read
 * by controllers/services.
 *
 * <p>The {@code TARGET_CLASS} proxy mode allows this bean to be
 * injected into singleton beans (controllers, services). Each invocation
 * resolves to the request-scoped instance for the current thread.
 */
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST,
        proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestScopedTenantContext implements TenantContext {

    private UUID churchId;

    /**
     * Sets the church ID for the current request. Called by the
     * authentication / tenant-resolution filter only.
     */
    public void setChurchId(UUID churchId) {
        this.churchId = churchId;
    }

    @Override
    public Optional<UUID> churchIdOptional() {
        return Optional.ofNullable(churchId);
    }

    @Override
    public UUID requireChurchId() {
        if (churchId == null) {
            throw new IllegalStateException(
                    "No tenant (churchId) is set on the current request. " +
                            "This usually means a controller method intended for " +
                            "tenant-scoped use was reached without authentication."
            );
        }
        return churchId;
    }
}