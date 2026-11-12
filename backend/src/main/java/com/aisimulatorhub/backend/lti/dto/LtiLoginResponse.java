
package com.aisimulatorhub.backend.lti.dto;

public class LtiLoginResponse {

    private final String authorizationUrl;

    public LtiLoginResponse(String authorizationUrl) {
        this.authorizationUrl = authorizationUrl;
    }

    public String getAuthorizationUrl() {
        return authorizationUrl;
    }
}
