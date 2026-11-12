
package com.aisimulatorhub.backend.lti.service;

import com.aisimulatorhub.backend.lti.dto.LtiLoginRequest;
import com.aisimulatorhub.backend.lti.dto.LtiLoginResponse;
import com.aisimulatorhub.backend.lti.security.LtiSecurityUtils;
import com.aisimulatorhub.backend.model.LtiLoginTransaction;
import com.aisimulatorhub.backend.model.PlatformRegistration;
import com.aisimulatorhub.backend.repository.LtiLoginTransactionRepository;
import com.aisimulatorhub.backend.repository.PlatformRegistrationRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@Service
public class LtiLoginService {

    private static final Set<String> RESERVED_OIDC_PARAMETERS = Set.of(
            "scope",
            "response_type",
            "response_mode",
            "prompt",
            "client_id",
            "redirect_uri",
            "login_hint",
            "state",
            "nonce",
            "lti_message_hint"
    );

    private final PlatformRegistrationRepository platformRepository;
    private final LtiLoginTransactionRepository transactionRepository;

    private final String platformBaseUrl;
    private final long stateTtlSeconds;
    private final long nonceTtlSeconds;

    public LtiLoginService(
            PlatformRegistrationRepository platformRepository,
            LtiLoginTransactionRepository transactionRepository,
            LtiPlatformBaseUrlValidator baseUrlValidator,
            @Value("${lti.login.state-ttl-seconds:300}") long stateTtlSeconds,
            @Value("${lti.login.nonce-ttl-seconds:300}") long nonceTtlSeconds
    ) {
        this.platformRepository = platformRepository;
        this.transactionRepository = transactionRepository;
        this.platformBaseUrl = baseUrlValidator.getValidatedBaseUrl();
        this.stateTtlSeconds = stateTtlSeconds;
        this.nonceTtlSeconds = nonceTtlSeconds;
    }

    public LtiLoginResponse initiateLogin(LtiLoginRequest request) {

        validateRequest(request);

        List<PlatformRegistration> registrations =
                platformRepository
                        .findByPlatformIssuerAndClientIdAndEnabledTrue(
                                request.getIss(),
                                request.getClientId()
                        );

        if (registrations.isEmpty()) {
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "LTI platform is not registered or enabled"
            );
        }

        validateConsistentPlatformConfiguration(registrations);

        PlatformRegistration platform = registrations.get(0);

        String expectedLaunchUri = normalizeBaseUrl(platformBaseUrl)
                + "/lti/launch";

        if (!expectedLaunchUri.equals(request.getTargetLinkUri())) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Invalid LTI target_link_uri"
            );
        }

        validateAuthorizationEndpoint(
                platform.getAuthorizationEndpoint()
        );

        if (stateTtlSeconds <= 0 || nonceTtlSeconds <= 0) {
            throw new ResponseStatusException(
                    INTERNAL_SERVER_ERROR,
                    "Invalid LTI transaction lifetime configuration"
            );
        }

        String state = LtiSecurityUtils.generateRandomValue();
        String nonce = LtiSecurityUtils.generateRandomValue();

        String authorizationUrl = buildAuthorizationUrl(
                platform,
                request,
                state,
                nonce
        );

        Instant now = Instant.now();

        long transactionTtl = Math.min(
                stateTtlSeconds,
                nonceTtlSeconds
        );

        LtiLoginTransaction transaction = new LtiLoginTransaction();

        transaction.setPlatformIssuer(request.getIss());
        transaction.setClientId(request.getClientId());
        transaction.setStateHash(LtiSecurityUtils.sha256(state));
        transaction.setNonceHash(LtiSecurityUtils.sha256(nonce));
        transaction.setTargetLinkUri(request.getTargetLinkUri());
        transaction.setLoginHint(request.getLoginHint());
        transaction.setLtiMessageHint(request.getLtiMessageHint());
        transaction.setExpiresAt(now.plusSeconds(transactionTtl));

        transactionRepository.save(transaction);

        return new LtiLoginResponse(authorizationUrl);
    }

    private void validateRequest(LtiLoginRequest request) {

        if (request == null
                || isBlank(request.getIss())
                || isBlank(request.getClientId())
                || isBlank(request.getLoginHint())
                || isBlank(request.getTargetLinkUri())) {

            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Missing required LTI login parameters"
            );
        }

        if (request.getIss().length() > 1000
                || request.getClientId().length() > 255
                || request.getLoginHint().length() > 2000
                || request.getTargetLinkUri().length() > 1000
                || (request.getLtiMessageHint() != null
                    && request.getLtiMessageHint().length() > 4000)) {

            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "LTI login parameter exceeds the permitted length"
            );
        }

        try {
            URI targetUri = URI.create(request.getTargetLinkUri());

            boolean localDevelopment = isLocalDevelopment();

            boolean validScheme =
                    "https".equalsIgnoreCase(targetUri.getScheme())
                    || (localDevelopment
                        && "http".equalsIgnoreCase(targetUri.getScheme()));

            if (!targetUri.isAbsolute()
                    || !validScheme
                    || targetUri.getHost() == null
                    || targetUri.getUserInfo() != null
                    || targetUri.getFragment() != null) {

                throw new IllegalArgumentException();
            }

        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Invalid LTI target_link_uri"
            );
        }
    }

    private boolean isLocalDevelopment() {

        URI baseUri = URI.create(normalizeBaseUrl(platformBaseUrl));

        String host = baseUri.getHost();

        if (host == null) {
            return false;
        }

        host = host.toLowerCase(Locale.ROOT);

        // Normalize IPv6 brackets if present.
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }

        return host.equals("localhost")
                || host.endsWith(".localhost")
                || host.equals("127.0.0.1")
                || host.equals("::1");
    }

    private void validateAuthorizationEndpoint(String endpoint) {

        try {
            if (isBlank(endpoint) || endpoint.length() > 1000) {
                throw new IllegalArgumentException();
            }

            URI uri = URI.create(endpoint);

            int port = uri.getPort();

            if (!uri.isAbsolute()
                    || !"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null
                    || uri.getRawAuthority() == null
                    || (port != -1 && (port < 1 || port > 65535))) {

                throw new IllegalArgumentException();
            }

            validateAuthorizationEndpointParameters(uri);

        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "Registered platform authorization endpoint is invalid"
            );
        }
    }

    private void validateAuthorizationEndpointParameters(URI uri) {

        String query = uri.getRawQuery();

        if (query == null || query.isEmpty()) {
            return;
        }

        Set<String> parameterNames = new HashSet<>();

        for (String parameter : query.split("&", -1)) {

            if (parameter.isEmpty()) {
                throw new IllegalArgumentException();
            }

            String rawName = parameter.split("=", 2)[0];

            String name = java.net.URLDecoder.decode(
                    rawName,
                    StandardCharsets.UTF_8
            ).toLowerCase(Locale.ROOT);

            if (name.isBlank()
                    || !parameterNames.add(name)
                    || RESERVED_OIDC_PARAMETERS.contains(name)) {

                throw new IllegalArgumentException();
            }
        }
    }

    private void validateConsistentPlatformConfiguration(
            List<PlatformRegistration> registrations
    ) {

        PlatformRegistration first = registrations.get(0);

        if (first.getAuthorizationEndpoint() == null
                || first.getTokenEndpoint() == null
                || first.getPlatformJwksUrl() == null) {

            throw new ResponseStatusException(
                    FORBIDDEN,
                    "Incomplete LTI platform configuration"
            );
        }

        for (PlatformRegistration registration : registrations) {

            if (registration.getAuthorizationEndpoint() == null
                    || registration.getTokenEndpoint() == null
                    || registration.getPlatformJwksUrl() == null
                    || !first.getAuthorizationEndpoint().equals(
                    registration.getAuthorizationEndpoint())
                    || !first.getTokenEndpoint().equals(
                    registration.getTokenEndpoint())
                    || !first.getPlatformJwksUrl().equals(
                    registration.getPlatformJwksUrl())) {

                throw new ResponseStatusException(
                        FORBIDDEN,
                        "Conflicting LTI platform configurations"
                );
            }
        }
    }

    private String buildAuthorizationUrl(
            PlatformRegistration platform,
            LtiLoginRequest request,
            String state,
            String nonce
    ) {

        String endpoint = platform.getAuthorizationEndpoint();

        StringBuilder url = new StringBuilder(endpoint);

        url.append(endpoint.contains("?") ? "&" : "?");

        appendParameter(url, "scope", "openid");
        appendParameter(url, "response_type", "id_token");
        appendParameter(url, "response_mode", "form_post");
        appendParameter(url, "prompt", "none");
        appendParameter(url, "client_id", request.getClientId());

        appendParameter(
                url,
                "redirect_uri",
                normalizeBaseUrl(platformBaseUrl) + "/lti/launch"
        );

        appendParameter(url, "login_hint", request.getLoginHint());
        appendParameter(url, "state", state);
        appendParameter(url, "nonce", nonce);

        if (!isBlank(request.getLtiMessageHint())) {
            appendParameter(
                    url,
                    "lti_message_hint",
                    request.getLtiMessageHint()
            );
        }

        return url.toString();
    }

    private void appendParameter(
            StringBuilder url,
            String name,
            String value
    ) {

        if (url.charAt(url.length() - 1) != '?'
                && url.charAt(url.length() - 1) != '&') {
            url.append('&');
        }

        url.append(URLEncoder.encode(name, StandardCharsets.UTF_8));
        url.append('=');
        url.append(URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    private String normalizeBaseUrl(String value) {

        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(
                    INTERNAL_SERVER_ERROR,
                    "LTI platform base URL is not configured"
            );
        }

        String normalized = value.trim();

        while (normalized.endsWith("/")) {
            normalized = normalized.substring(
                    0,
                    normalized.length() - 1
            );
        }

        return normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
