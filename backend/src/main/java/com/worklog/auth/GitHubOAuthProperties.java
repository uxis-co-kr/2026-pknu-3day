package com.worklog.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code worklog.github} 의 OAuth 관련 설정 (PRD F5). */
@ConfigurationProperties(prefix = "worklog.github")
public class GitHubOAuthProperties {

    private String clientId;
    private String clientSecret;
    private String scope = "read:user repo";

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }
}
