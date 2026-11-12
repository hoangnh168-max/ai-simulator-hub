
package com.aisimulatorhub.backend.lti.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LtiLaunchRequest {

    @NotBlank
    @Size(max = 50000)
    private String idToken;

    @NotBlank
    @Size(max = 200)
    private String state;

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}
