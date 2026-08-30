package com.techeazyhq.cm.seckit;

import java.util.List;
import java.util.Set;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Adapter (plan §12) from a Keycloak-issued {@link Jwt} to the platform's own notion of "who is
 * calling" — isolates every service's application/interface layers from Spring Security's JWT
 * representation. {@code dfId} is populated for both interactive DF App users (PKCE) and DF
 * backend systems (client-credentials, the {@code cm-df-api} client, plan §10) since both carry
 * the same {@code df_id} claim.
 */
public record AuthCtx(String subject, String dfId, String dpId, Set<String> roles) {

    private static final String CLAIM_DF_ID = "df_id";
    private static final String CLAIM_ROLES = "roles";
    private static final String ROLE_DP = "DP";

    /**
     * {@code df_id} is a distinct claim since a DF's employees are separate Keycloak accounts
     * sharing one organizational identity. A DP has no such distinction — their CM identity
     * *is* their own Keycloak account, so {@code dpId} is simply their {@code sub} once linked
     * (plan §7a), not a separate custom claim.
     */
    public static AuthCtx from(Jwt jwt) {
        List<String> roleClaim = jwt.getClaimAsStringList(CLAIM_ROLES);
        Set<String> roles = roleClaim == null ? Set.of() : Set.copyOf(roleClaim);
        return new AuthCtx(
                jwt.getSubject(),
                jwt.getClaimAsString(CLAIM_DF_ID),
                roles.contains(ROLE_DP) ? jwt.getSubject() : null,
                roles);
    }

    public boolean isCmAdmin() {
        return roles.contains("CM_ADMIN");
    }

    public boolean isDf() {
        return dfId != null;
    }

    public boolean isDp() {
        return dpId != null;
    }
}
