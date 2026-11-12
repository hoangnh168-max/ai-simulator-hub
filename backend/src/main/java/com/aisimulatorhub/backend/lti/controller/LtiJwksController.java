
package com.aisimulatorhub.backend.lti.controller;

import com.aisimulatorhub.backend.lti.security.LtiKeyService;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/lti")
public class LtiJwksController {

    private final LtiKeyService ltiKeyService;

    public LtiJwksController(LtiKeyService ltiKeyService) {
        this.ltiKeyService = ltiKeyService;
    }

    @GetMapping(
        value = "/jwks",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> getJwks() {

        RSAKey publicKey = ltiKeyService.getPublicJwk();

        JWKSet jwkSet = new JWKSet(publicKey);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(
                    CacheControl.maxAge(1, TimeUnit.HOURS)
                        .cachePublic()
                )
                .body(jwkSet.toJSONObject());
    }
}
