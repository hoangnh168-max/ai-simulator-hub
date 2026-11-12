
package com.aisimulatorhub.backend.lti.service;

import com.aisimulatorhub.backend.lti.dto.LtiLoginRequest;
import com.aisimulatorhub.backend.lti.dto.LtiLoginResponse;
import com.aisimulatorhub.backend.model.LtiLoginTransaction;
import com.aisimulatorhub.backend.model.PlatformRegistration;
import com.aisimulatorhub.backend.repository.LtiLoginTransactionRepository;
import com.aisimulatorhub.backend.repository.PlatformRegistrationRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LtiLoginServiceTests {

    private static final String ISSUER =
            "https://platform.example";

    private static final String CLIENT_ID =
            "test-client";

    private static final String BASE_URL =
            "http://localhost:8080";

    private PlatformRegistrationRepository platformRepository;
    private LtiLoginTransactionRepository transactionRepository;
    private LtiLoginService service;

    @BeforeEach
    void setUp() {

        platformRepository =
                mock(PlatformRegistrationRepository.class);

        transactionRepository =
                mock(LtiLoginTransactionRepository.class);

        service = new LtiLoginService(
                platformRepository,
                transactionRepository,
                new LtiPlatformBaseUrlValidator(BASE_URL),
                300,
                300
        );

        when(transactionRepository.save(any(LtiLoginTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void validLoginShouldBuildAuthorizationUrlAndPersistTransaction() {

        PlatformRegistration registration =
                createRegistration(
                        "https://platform.example/authorize?tenant=school-a"
                );

        when(platformRepository
                .findByPlatformIssuerAndClientIdAndEnabledTrue(
                        ISSUER,
                        CLIENT_ID
                ))
                .thenReturn(List.of(registration));

        LtiLoginResponse response =
                service.initiateLogin(createRequest());

        assertNotNull(response);
        assertNotNull(response.getAuthorizationUrl());

        URI authorizationUri =
                URI.create(response.getAuthorizationUrl());

        assertEquals("https", authorizationUri.getScheme());
        assertEquals("platform.example", authorizationUri.getHost());
        assertEquals("/authorize", authorizationUri.getPath());

        Map<String, String> parameters =
                parseQueryParameters(authorizationUri);

        assertEquals("school-a", parameters.get("tenant"));
        assertEquals("openid", parameters.get("scope"));
        assertEquals("id_token", parameters.get("response_type"));
        assertEquals("form_post", parameters.get("response_mode"));
        assertEquals("none", parameters.get("prompt"));
        assertEquals(CLIENT_ID, parameters.get("client_id"));
        assertEquals(
                BASE_URL + "/lti/launch",
                parameters.get("redirect_uri")
        );

        assertEquals("test-login-hint", parameters.get("login_hint"));

        assertNotNull(parameters.get("state"));
        assertFalse(parameters.get("state").isBlank());

        assertNotNull(parameters.get("nonce"));
        assertFalse(parameters.get("nonce").isBlank());

        assertNotEquals(
                parameters.get("state"),
                parameters.get("nonce")
        );

        assertEquals(
                "test-message-hint",
                parameters.get("lti_message_hint")
        );

        verify(transactionRepository, times(1))
                .save(any(LtiLoginTransaction.class));
    }

    @Test
    void unknownPlatformShouldBeRejected() {

        when(platformRepository
                .findByPlatformIssuerAndClientIdAndEnabledTrue(
                        ISSUER,
                        CLIENT_ID
                ))
                .thenReturn(List.of());

        assertThrows(
                ResponseStatusException.class,
                () -> service.initiateLogin(createRequest())
        );

        verify(transactionRepository, never())
                .save(any(LtiLoginTransaction.class));
    }

    @Test
    void authorizationEndpointWithConflictingClientIdShouldBeRejected() {

        PlatformRegistration registration =
                createRegistration(
                        "https://platform.example/authorize?client_id=attacker"
                );

        when(platformRepository
                .findByPlatformIssuerAndClientIdAndEnabledTrue(
                        ISSUER,
                        CLIENT_ID
                ))
                .thenReturn(List.of(registration));

        assertThrows(
                ResponseStatusException.class,
                () -> service.initiateLogin(createRequest())
        );

        verify(transactionRepository, never())
                .save(any(LtiLoginTransaction.class));
    }

    @Test
    void authorizationEndpointWithExistingStateParameterShouldBeRejected() {

        PlatformRegistration registration =
                createRegistration(
                        "https://platform.example/authorize?state=attacker"
                );

        when(platformRepository
                .findByPlatformIssuerAndClientIdAndEnabledTrue(
                        ISSUER,
                        CLIENT_ID
                ))
                .thenReturn(List.of(registration));

        assertThrows(
                ResponseStatusException.class,
                () -> service.initiateLogin(createRequest())
        );

        verify(transactionRepository, never())
                .save(any(LtiLoginTransaction.class));
    }

    @Test
    void authorizationEndpointWithUserInfoShouldBeRejected() {

        PlatformRegistration registration =
                createRegistration(
                        "https://attacker@platform.example/authorize"
                );

        when(platformRepository
                .findByPlatformIssuerAndClientIdAndEnabledTrue(
                        ISSUER,
                        CLIENT_ID
                ))
                .thenReturn(List.of(registration));

        assertThrows(
                ResponseStatusException.class,
                () -> service.initiateLogin(createRequest())
        );

        verify(transactionRepository, never())
                .save(any(LtiLoginTransaction.class));
    }

    @Test
    void authorizationEndpointWithFragmentShouldBeRejected() {

        PlatformRegistration registration =
                createRegistration(
                        "https://platform.example/authorize#fragment"
                );

        when(platformRepository
                .findByPlatformIssuerAndClientIdAndEnabledTrue(
                        ISSUER,
                        CLIENT_ID
                ))
                .thenReturn(List.of(registration));

        assertThrows(
                ResponseStatusException.class,
                () -> service.initiateLogin(createRequest())
        );

        verify(transactionRepository, never())
                .save(any(LtiLoginTransaction.class));
    }

    @Test
    void authorizationEndpointWithPortZeroShouldBeRejected() {

        PlatformRegistration registration =
                createRegistration(
                        "https://platform.example:0/authorize"
                );

        when(platformRepository
                .findByPlatformIssuerAndClientIdAndEnabledTrue(
                        ISSUER,
                        CLIENT_ID
                ))
                .thenReturn(List.of(registration));

        assertThrows(
                ResponseStatusException.class,
                () -> service.initiateLogin(createRequest())
        );

        verify(transactionRepository, never())
                .save(any(LtiLoginTransaction.class));
    }

    @Test
    void authorizationEndpointWithPortAboveMaximumShouldBeRejected() {

        PlatformRegistration registration =
                createRegistration(
                        "https://platform.example:99999/authorize"
                );

        when(platformRepository
                .findByPlatformIssuerAndClientIdAndEnabledTrue(
                        ISSUER,
                        CLIENT_ID
                ))
                .thenReturn(List.of(registration));

        assertThrows(
                ResponseStatusException.class,
                () -> service.initiateLogin(createRequest())
        );

        verify(transactionRepository, never())
                .save(any(LtiLoginTransaction.class));
    }

    private PlatformRegistration createRegistration(String endpoint) {

        PlatformRegistration registration =
                new PlatformRegistration();

        registration.setPlatformName("Test Platform");
        registration.setPlatformIssuer(ISSUER);
        registration.setClientId(CLIENT_ID);
        registration.setDeploymentId("test-deployment");

        registration.setAuthorizationEndpoint(endpoint);
        registration.setTokenEndpoint(
                "https://platform.example/token"
        );
        registration.setPlatformJwksUrl(
                "https://platform.example/jwks"
        );

        registration.setEnabled(true);

        return registration;
    }

    private LtiLoginRequest createRequest() {

        LtiLoginRequest request = new LtiLoginRequest();

        request.setIss(ISSUER);
        request.setClientId(CLIENT_ID);
        request.setLoginHint("test-login-hint");
        request.setTargetLinkUri(BASE_URL + "/lti/launch");
        request.setLtiMessageHint("test-message-hint");

        return request;
    }

    private Map<String, String> parseQueryParameters(URI uri) {

        return Arrays.stream(uri.getRawQuery().split("&"))
                .map(parameter -> parameter.split("=", 2))
                .collect(Collectors.toMap(
                        pair -> URLDecoder.decode(
                                pair[0],
                                StandardCharsets.UTF_8
                        ),
                        pair -> URLDecoder.decode(
                                pair.length > 1 ? pair[1] : "",
                                StandardCharsets.UTF_8
                        ),
                        (first, second) -> {
                            throw new IllegalStateException(
                                    "Duplicate authorization parameter"
                            );
                        }
                ));
    }
}
