package com.catechesis.backend.common.security;

import com.catechesis.backend.catechist.Catechist;
import com.catechesis.backend.catechist.CatechistRepository;
import com.catechesis.backend.common.tenancy.RequestScopedTenantContext;
import com.catechesis.backend.superadmin.SuperAdmin;
import com.catechesis.backend.superadmin.SuperAdminRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Development-only authentication filter.
 *
 * <p>Reads two mutually-exclusive HTTP headers:
 * <ul>
 *   <li>{@code X-Dev-Super-Admin-Id} — UUID resolved against
 *       {@link SuperAdminRepository}
 *   <li>{@code X-Dev-Catechist-Id} — UUID resolved against
 *       {@link CatechistRepository}
 * </ul>
 *
 * <p>On a valid header, populates both our custom
 * {@link RequestScopedSecurityContext} / {@link RequestScopedTenantContext}
 * AND Spring Security's {@code SecurityContextHolder}, so {@code @PreAuthorize}
 * annotations and our custom interfaces both work.
 *
 * <p>Active only when {@code app.auth.mode=dev}. In any other mode this
 * bean is not registered.
 *
 * <p>Per ADR-0004, this filter is a temporary stand-in. Sprint 5 replaces
 * it with a real Google OAuth flow.
 */
@Component
@ConditionalOnProperty(name = "app.auth.mode", havingValue = "dev")
public class DevAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DevAuthenticationFilter.class);

    private static final String HEADER_SUPER_ADMIN = "X-Dev-Super-Admin-Id";
    private static final String HEADER_CATECHIST   = "X-Dev-Catechist-Id";

    private final SuperAdminRepository superAdminRepository;
    private final CatechistRepository catechistRepository;
    private final RequestScopedSecurityContext securityContext;
    private final RequestScopedTenantContext tenantContext;

    public DevAuthenticationFilter(SuperAdminRepository superAdminRepository,
                                   CatechistRepository catechistRepository,
                                   RequestScopedSecurityContext securityContext,
                                   RequestScopedTenantContext tenantContext) {
        this.superAdminRepository = superAdminRepository;
        this.catechistRepository = catechistRepository;
        this.securityContext = securityContext;
        this.tenantContext = tenantContext;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String superAdminHeader = request.getHeader(HEADER_SUPER_ADMIN);
        String catechistHeader = request.getHeader(HEADER_CATECHIST);

        boolean hasSuperAdmin = superAdminHeader != null && !superAdminHeader.isBlank();
        boolean hasCatechist = catechistHeader != null && !catechistHeader.isBlank();

        if (hasSuperAdmin && hasCatechist) {
            reject(response, HttpServletResponse.SC_BAD_REQUEST,
                    "X-Dev-Super-Admin-Id and X-Dev-Catechist-Id are mutually exclusive");
            return;
        }

        if (hasSuperAdmin) {
            if (!authenticateSuperAdmin(superAdminHeader, response)) {
                return;
            }
        } else if (hasCatechist) {
            if (!authenticateCatechist(catechistHeader, response)) {
                return;
            }
        }
        // No headers: anonymous, continue without populating contexts.

        chain.doFilter(request, response);
    }

    private boolean authenticateSuperAdmin(String headerValue,
                                           HttpServletResponse response) throws IOException {
        UUID id = parseUuid(headerValue);
        if (id == null) {
            reject(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid UUID in " + HEADER_SUPER_ADMIN);
            return false;
        }

        Optional<SuperAdmin> found = superAdminRepository.findById(id);
        if (found.isEmpty()) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Unknown ID in " + HEADER_SUPER_ADMIN);
            return false;
        }

        SuperAdmin superAdmin = found.get();
        securityContext.authenticateAsSuperAdmin(superAdmin.getId());
        // Tenant context stays empty — super-admin is system-level.

        // Populate Spring Security too, so @PreAuthorize works.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "super-admin:" + superAdmin.getId(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
                )
        );

        log.debug("Dev auth: super-admin {}", superAdmin.getId());
        return true;
    }

    private boolean authenticateCatechist(String headerValue,
                                          HttpServletResponse response) throws IOException {
        UUID id = parseUuid(headerValue);
        if (id == null) {
            reject(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid UUID in " + HEADER_CATECHIST);
            return false;
        }

        Optional<Catechist> found = catechistRepository.findById(id);
        if (found.isEmpty()) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Unknown ID in " + HEADER_CATECHIST);
            return false;
        }

        Catechist catechist = found.get();
        securityContext.authenticateAsCatechist(catechist.getId(), catechist.getRole());
        tenantContext.setChurchId(catechist.getChurchId());

        // Populate Spring Security with role-based authorities.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "catechist:" + catechist.getId(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + catechist.getRole().name()))
                )
        );

        log.debug("Dev auth: catechist {} (role={}, church={})",
                catechist.getId(), catechist.getRole(), catechist.getChurchId());
        return true;
    }

    private static UUID parseUuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void reject(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(message);
    }
}