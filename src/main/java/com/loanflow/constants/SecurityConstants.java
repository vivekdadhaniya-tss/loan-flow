package com.loanflow.constants;


public final class SecurityConstants {

    private SecurityConstants() {}


    public static final String AUTHORIZATION_HEADER = "Authorization";

    public static final String BEARER_PREFIX = "Bearer ";

    public static final String TOKEN_TYPE = "Bearer";

    // Spring Security role prefix
    // Used by CustomUserDetailsService when building SimpleGrantedAuthority

    /**
     * Spring Security requires ROLE_ prefix.
     * Usage: ROLE_PREFIX + user.getRole().name()
     * Produces: "ROLE_BORROWER", "ROLE_LOAN_OFFICER", "ROLE_ADMIN"
     */
    public static final String ROLE_PREFIX = "ROLE_";

    // Public endpoints
    // Used by SecurityConfig — add new public URLs here only
    public static final String[] PUBLIC_URLS = {
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/actuator/health"
    };
}

