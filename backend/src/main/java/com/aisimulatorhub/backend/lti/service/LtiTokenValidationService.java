
package com.aisimulatorhub.backend.lti.service;

import com.aisimulatorhub.backend.model.PlatformRegistration;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyOperation;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;

import jakarta.annotation.PreDestroy;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.classic.methods.HttpGet;

import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@Service
public class LtiTokenValidationService {

    private static final int MIN_RSA_KEY_SIZE = 2048;
    private static final int MAX_JWKS_SIZE = 1024 * 1024;

    private static final Duration JWKS_CACHE_TTL =
            Duration.ofMinutes(5);

    private static final Duration JWKS_FETCH_COOLDOWN =
            Duration.ofSeconds(30);

    private final CloseableHttpClient httpClient;

    private final LtiEndpointSecurityService endpointSecurityService;

    private final Clock clock;

    private final ConcurrentHashMap<String, CachedJwks> jwksCache =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Object> jwksLocks =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Instant> lastFetchAttempt =
            new ConcurrentHashMap<>();

    @Autowired
    public LtiTokenValidationService(
            LtiEndpointSecurityService endpointSecurityService
    ) {
        this(
                endpointSecurityService,
                createSecureDnsResolver(endpointSecurityService),
                Clock.systemUTC()
        );
    }

    LtiTokenValidationService(
            LtiEndpointSecurityService endpointSecurityService,
            DnsResolver dnsResolver
    ) {
        this(
                endpointSecurityService,
                dnsResolver,
                Clock.systemUTC()
        );
    }

    // Package-private constructor for deterministic tests.
    LtiTokenValidationService(
            LtiEndpointSecurityService endpointSecurityService,
            DnsResolver dnsResolver,
            Clock clock
    ) {
        this(
                endpointSecurityService,
                dnsResolver,
                clock,
                10
        );
    }

    // Package-private constructor for timeout tests.
    LtiTokenValidationService(
            LtiEndpointSecurityService endpointSecurityService,
            DnsResolver dnsResolver,
            Clock clock,
            long responseTimeoutSeconds
    ) {
        this.endpointSecurityService = endpointSecurityService;
        this.clock = clock;

        PoolingHttpClientConnectionManager connectionManager =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(dnsResolver)
                        .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(5))
                .setConnectTimeout(Timeout.ofSeconds(5))
                .setResponseTimeout(
                        Timeout.ofSeconds(responseTimeoutSeconds)
                )
                .build();

        this.httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .build();
    }

    private static DnsResolver createSecureDnsResolver(
            LtiEndpointSecurityService endpointSecurityService
    ) {
        return new DnsResolver() {

            @Override
            public InetAddress[] resolve(String hostname)
                    throws UnknownHostException {
                return endpointSecurityService.resolvePublicHost(hostname);
            }

            @Override
            public String resolveCanonicalHostname(String hostname)
                    throws UnknownHostException {
                return hostname;
            }
        };
    }

    public SignedJWT verifySignature(
            String idToken,
            PlatformRegistration registration
    ) {
        if (registration == null || !registration.isEnabled()) {
            reject("LTI Platform deployment is not enabled");
        }

        SignedJWT signedJWT;

        try {
            signedJWT = SignedJWT.parse(idToken);
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Invalid LTI ID token format"
            );
        }

        if (!JWSAlgorithm.RS256.equals(
                signedJWT.getHeader().getAlgorithm())) {
            reject("Unsupported LTI ID token signing algorithm");
        }

        Set<String> headerParameters =
                signedJWT.getHeader().getIncludedParams();

        if (headerParameters.contains("jku")
                || headerParameters.contains("x5u")) {
            reject("Untrusted JWT key-location headers are not permitted");
        }

        String keyId = signedJWT.getHeader().getKeyID();

        if (keyId == null || keyId.isBlank()) {
            reject("LTI ID token is missing its key ID");
        }

        String jwksUrl = registration.getPlatformJwksUrl();

        JWKSet jwkSet = getJwks(jwksUrl, false);

        List<JWK> matchingKeys = findKeysById(jwkSet, keyId);

        if (matchingKeys.isEmpty()) {
            jwkSet = refreshJwksForUnknownKey(jwksUrl);
            matchingKeys = findKeysById(jwkSet, keyId);
        }

        if (matchingKeys.size() != 1
                || !(matchingKeys.get(0) instanceof RSAKey)) {
            reject(
                    "No unique matching RSA key was found in Platform JWKS"
            );
        }

        RSAKey rsaKey = (RSAKey) matchingKeys.get(0);

        try {
            if (rsaKey.isPrivate()) {
                reject("Platform JWKS must not expose private key material");
            }

            RSAPublicKey publicKey = rsaKey.toRSAPublicKey();

            if (publicKey.getModulus() == null
                    || publicKey.getModulus().bitLength() < MIN_RSA_KEY_SIZE) {
                reject("Platform RSA key must be at least 2048 bits");
            }

            if (publicKey.getPublicExponent() == null) {
                reject("Platform RSA public exponent is missing");
            }

            if (rsaKey.getAlgorithm() != null
                    && !JWSAlgorithm.RS256.equals(
                            rsaKey.getAlgorithm())) {
                reject("Platform RSA key has an incompatible algorithm");
            }

            if (rsaKey.getKeyUse() != null
                    && !KeyUse.SIGNATURE.equals(rsaKey.getKeyUse())) {
                reject("Platform RSA key is not intended for signatures");
            }

            Set<KeyOperation> operations = rsaKey.getKeyOperations();

            if (operations != null
                    && !operations.isEmpty()
                    && !operations.contains(KeyOperation.VERIFY)) {
                reject("Platform RSA key does not permit verification");
            }

            JWSVerifier verifier = new RSASSAVerifier(publicKey);

            if (!signedJWT.verify(verifier)) {
                reject("LTI ID token signature verification failed");
            }

            return signedJWT;

        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "Unable to verify LTI ID token signature"
            );
        }
    }

    private List<JWK> findKeysById(
            JWKSet jwkSet,
            String keyId
    ) {
        return jwkSet.getKeys()
                .stream()
                .filter(key -> keyId.equals(key.getKeyID()))
                .toList();
    }

    // Package-private to allow deterministic cooldown tests.
    JWKSet refreshJwksForUnknownKey(String jwksUrl) {

        Object lock = jwksLocks.computeIfAbsent(
                jwksUrl,
                key -> new Object()
        );

        synchronized (lock) {

            CachedJwks cached = jwksCache.get(jwksUrl);

            if (cached == null) {
                return getJwks(jwksUrl, false);
            }

            Instant previousAttempt = lastFetchAttempt.get(jwksUrl);
            Instant now = clock.instant();

            if (previousAttempt != null
                    && previousAttempt.plus(JWKS_FETCH_COOLDOWN)
                            .isAfter(now)) {
                return cached.jwkSet();
            }

            return fetchAndCacheJwks(jwksUrl);
        }
    }

    // Package-private to allow cache-expiry tests.
    JWKSet getJwks(
            String jwksUrl,
            boolean forceRefresh
    ) {
        Object lock = jwksLocks.computeIfAbsent(
                jwksUrl,
                key -> new Object()
        );

        synchronized (lock) {

            CachedJwks cached = jwksCache.get(jwksUrl);

            if (!forceRefresh
                    && cached != null
                    && cached.fetchedAt().plus(JWKS_CACHE_TTL)
                            .isAfter(clock.instant())) {
                return cached.jwkSet();
            }

            Instant previousAttempt = lastFetchAttempt.get(jwksUrl);

            if (previousAttempt != null
                    && previousAttempt.plus(JWKS_FETCH_COOLDOWN)
                            .isAfter(clock.instant())) {

                if (cached != null) {
                    return cached.jwkSet();
                }

                throw new ResponseStatusException(
                        BAD_GATEWAY,
                        "Platform JWKS fetch is temporarily throttled"
                );
            }

            return fetchAndCacheJwks(jwksUrl);
        }
    }

    private JWKSet fetchAndCacheJwks(String jwksUrl) {

        // Record the attempt before making the outbound request.
        // Both successful and failed requests are throttled.
        lastFetchAttempt.put(jwksUrl, clock.instant());

        JWKSet freshJwks = fetchJwks(jwksUrl);

        CachedJwks cached = new CachedJwks(
                freshJwks,
                clock.instant()
        );

        jwksCache.put(jwksUrl, cached);

        return freshJwks;
    }

    JWKSet fetchJwks(String jwksUrl) {

        try {
            endpointSecurityService.validatePublicHttpsUrl(jwksUrl);

            URI uri = URI.create(jwksUrl);

            HttpGet request = new HttpGet(uri);
            request.setHeader("Accept", "application/json");

            try (CloseableHttpResponse response =
                         httpClient.execute(request)) {

                if (response.getCode() != 200) {
                    throw new ResponseStatusException(
                            BAD_GATEWAY,
                            "Unable to retrieve Platform JWKS"
                    );
                }

                HttpEntity entity = response.getEntity();

                if (entity == null) {
                    throw new ResponseStatusException(
                            BAD_GATEWAY,
                            "Platform JWKS response is empty"
                    );
                }

                try (InputStream body = entity.getContent()) {

                    byte[] responseBytes =
                            body.readNBytes(MAX_JWKS_SIZE + 1);

                    if (responseBytes.length > MAX_JWKS_SIZE) {
                        throw new ResponseStatusException(
                                BAD_GATEWAY,
                                "Platform JWKS response exceeds the permitted size"
                        );
                    }

                    String responseBody = new String(
                            responseBytes,
                            StandardCharsets.UTF_8
                    );

                    return JWKSet.parse(responseBody);
                }
            }

        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "Platform JWKS retrieval or parsing failed"
            );
        }
    }

    @PreDestroy
    public void closeHttpClient() throws IOException {
        httpClient.close();
    }

    private void reject(String message) {
        throw new ResponseStatusException(FORBIDDEN, message);
    }

    private record CachedJwks(
            JWKSet jwkSet,
            Instant fetchedAt
    ) {
    }
}
