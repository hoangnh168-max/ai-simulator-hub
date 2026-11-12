
package com.aisimulatorhub.backend.lti.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LtiLoginRequest {

    @NotBlank
    @Size(max = 1000)
    private String iss;

    @NotBlank
    @Size(max = 255)
    private String clientId;

    @NotBlank
    @Size(max = 2000)
    private String loginHint;

    @NotBlank
    @Size(max = 1000)
    private String targetLinkUri;

    @Size(max = 4000)
    private String ltiMessageHint;

    public String getIss() {
        return iss;
    }

    public void setIss(String iss) {
        this.iss = iss;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getLoginHint() {
        return loginHint;
    }

    public void setLoginHint(String loginHint) {
        this.loginHint = loginHint;
    }

    public String getTargetLinkUri() {
        return targetLinkUri;
    }

    public void setTargetLinkUri(String targetLinkUri) {
        this.targetLinkUri = targetLinkUri;
    }

    public String getLtiMessageHint() {
        return ltiMessageHint;
    }

    public void setLtiMessageHint(String ltiMessageHint) {
        this.ltiMessageHint = ltiMessageHint;
    }
}
