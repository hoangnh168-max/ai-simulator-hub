
package com.aisimulatorhub.backend.lti.controller;

import com.aisimulatorhub.backend.lti.dto.ValidatedLtiLaunch;
import com.aisimulatorhub.backend.lti.service.LtiLaunchService;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/lti")
@Validated
public class LtiLaunchController {

    private final LtiLaunchService launchService;

    public LtiLaunchController(LtiLaunchService launchService) {
        this.launchService = launchService;
    }

    @PostMapping(
            value = "/launch",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
    )
    public ResponseEntity<Map<String, Object>> launch(
            @RequestParam("id_token")
            @NotBlank
            @Size(max = 50000)
            String idToken,

            @RequestParam("state")
            @NotBlank
            @Size(max = 200)
            String state
    ) {

        ValidatedLtiLaunch launch =
                launchService.processLaunch(idToken, state);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of(
                        "status", "validated",
                        "messageType", launch.getMessageType(),
                        "ltiVersion", launch.getLtiVersion()
                ));
    }
}
