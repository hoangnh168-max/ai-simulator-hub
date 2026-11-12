
package com.aisimulatorhub.backend.lti.controller;

import com.aisimulatorhub.backend.lti.dto.LtiLoginRequest;
import com.aisimulatorhub.backend.lti.dto.LtiLoginResponse;
import com.aisimulatorhub.backend.lti.service.LtiLoginService;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@Validated
@RequestMapping("/lti")
public class LtiLoginController {

    private final LtiLoginService ltiLoginService;

    public LtiLoginController(LtiLoginService ltiLoginService) {
        this.ltiLoginService = ltiLoginService;
    }

    @GetMapping("/login")
    public ResponseEntity<Void> initiateLogin(

            @RequestParam("iss")
            @NotBlank
            @Size(max = 1000)
            String issuer,

            @RequestParam("client_id")
            @NotBlank
            @Size(max = 255)
            String clientId,

            @RequestParam("login_hint")
            @NotBlank
            @Size(max = 2000)
            String loginHint,

            @RequestParam("target_link_uri")
            @NotBlank
            @Size(max = 1000)
            String targetLinkUri,

            @RequestParam(value = "lti_message_hint", required = false)
            @Size(max = 4000)
            String ltiMessageHint
    ) {

        LtiLoginRequest request = new LtiLoginRequest();

        request.setIss(issuer);
        request.setClientId(clientId);
        request.setLoginHint(loginHint);
        request.setTargetLinkUri(targetLinkUri);
        request.setLtiMessageHint(ltiMessageHint);

        LtiLoginResponse response =
                ltiLoginService.initiateLogin(request);

        HttpHeaders headers = new HttpHeaders();

        headers.setLocation(URI.create(response.getAuthorizationUrl()));

        headers.setCacheControl("no-store");

        return new ResponseEntity<>(
                headers,
                HttpStatus.FOUND
        );
    }
}
