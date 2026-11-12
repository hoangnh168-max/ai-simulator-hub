
package com.aisimulatorhub.backend.lti.service;

import com.nimbusds.jose.jwk.JWKSet;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpsConfigurator;

import org.apache.hc.client5.http.DnsResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.web.server.ResponseStatusException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LtiTokenValidationServiceTests {

    private LtiEndpointSecurityService endpointSecurityService;

    private HttpServer server;

    private HttpsServer httpsServer;

    private LtiTokenValidationService service;

    private AtomicInteger dnsResolutionCount;

    private MutableClock testClock;

    private static final String VALID_JWKS =
            "{\"keys\":[]}";

    @BeforeEach
    void setUp() throws Exception {

        endpointSecurityService =
                mock(LtiEndpointSecurityService.class);

        // URL validation is mocked only for these HTTP integration tests.
        // Production URL validation remains unchanged.
        doNothing().when(endpointSecurityService)
                .validatePublicHttpsUrl(anyString());

        dnsResolutionCount = new AtomicInteger();

        testClock = new MutableClock(
                Instant.parse("2026-11-12T03:00:00Z")
        );

        DnsResolver testDnsResolver = new DnsResolver() {

            @Override
            public InetAddress[] resolve(String hostname)
                    throws UnknownHostException {

                dnsResolutionCount.incrementAndGet();

                return new InetAddress[]{
                        InetAddress.getByName("127.0.0.1")
                };
            }

            @Override
            public String resolveCanonicalHostname(String hostname) {
                return hostname;
            }
        };

        service = new LtiTokenValidationService(
                endpointSecurityService,
                testDnsResolver,
                testClock
        );

        server = HttpServer.create(
                new InetSocketAddress(
                        InetAddress.getByName("127.0.0.1"),
                        0
                ),
                0
        );

        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {

        if (server != null) {
            server.stop(0);
        }

        if (httpsServer != null) {
            httpsServer.stop(0);
        }

        if (service != null) {
            service.closeHttpClient();
        }
    }

    @Test
    void validJwksResponseShouldBeParsed() throws Exception {

        server.createContext("/jwks", exchange -> {

            byte[] response = VALID_JWKS.getBytes(
                    StandardCharsets.UTF_8
            );

            exchange.getResponseHeaders().set(
                    "Content-Type",
                    "application/json"
            );

            exchange.sendResponseHeaders(200, response.length);

            exchange.getResponseBody().write(response);
            exchange.close();
        });

        String url = testUrl("/jwks");

        JWKSet jwkSet = service.fetchJwks(url);

        assertNotNull(jwkSet);
        assertTrue(jwkSet.getKeys().isEmpty());

        assertTrue(
                dnsResolutionCount.get() > 0,
                "The custom DNS resolver should be invoked"
        );

        verify(endpointSecurityService)
                .validatePublicHttpsUrl(url);
    }

    @Test
    void customDnsResolverShouldBeUsedForOutboundConnection()
            throws Exception {

        server.createContext("/dns", exchange -> {

            byte[] response = VALID_JWKS.getBytes(
                    StandardCharsets.UTF_8
            );

            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        service.fetchJwks(testUrl("/dns"));

        assertTrue(
                dnsResolutionCount.get() > 0,
                "Outbound connection must use the custom DNS resolver"
        );
    }

    @Test
    void redirectShouldNotBeFollowed() throws Exception {

        AtomicInteger destinationRequests = new AtomicInteger();

        server.createContext("/redirect", exchange -> {

            exchange.getResponseHeaders().set(
                    "Location",
                    "/destination"
            );

            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        server.createContext("/destination", exchange -> {

            destinationRequests.incrementAndGet();

            byte[] response = VALID_JWKS.getBytes(
                    StandardCharsets.UTF_8
            );

            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.fetchJwks(testUrl("/redirect"))
        );

        assertEquals(
                502,
                exception.getStatusCode().value()
        );

        assertEquals(
                0,
                destinationRequests.get(),
                "The HTTP client must not follow redirects"
        );
    }

    @Test
    void oversizedJwksResponseShouldBeRejected() throws Exception {

        server.createContext("/oversized", exchange -> {

            int size = 1024 * 1024 + 100;

            byte[] response = new byte[size];

            Arrays.fill(response, (byte) ' ');

            exchange.getResponseHeaders().set(
                    "Content-Type",
                    "application/json"
            );

            exchange.sendResponseHeaders(200, response.length);

            exchange.getResponseBody().write(response);
            exchange.close();
        });

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.fetchJwks(testUrl("/oversized"))
        );

        assertEquals(
                502,
                exception.getStatusCode().value()
        );
    }

    @Test
    void invalidJwksJsonShouldBeRejected() throws Exception {

        server.createContext("/invalid", exchange -> {

            byte[] response = "not-valid-json".getBytes(
                    StandardCharsets.UTF_8
            );

            exchange.sendResponseHeaders(200, response.length);

            exchange.getResponseBody().write(response);
            exchange.close();
        });

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.fetchJwks(testUrl("/invalid"))
        );

        assertEquals(
                502,
                exception.getStatusCode().value()
        );
    }

    @Test
    void selfSignedTlsCertificateShouldBeRejected()
            throws Exception {

        // Load the test-only self-signed certificate.
        KeyStore keyStore = KeyStore.getInstance("PKCS12");

        try (InputStream input = getClass()
                .getClassLoader()
                .getResourceAsStream("tls-test-keystore.p12")) {

            assertNotNull(
                    input,
                    "Test TLS keystore must exist in test resources"
            );

            keyStore.load(
                    input,
                    "changeit".toCharArray()
            );
        }

        KeyManagerFactory keyManagerFactory =
                KeyManagerFactory.getInstance(
                        KeyManagerFactory.getDefaultAlgorithm()
                );

        keyManagerFactory.init(
                keyStore,
                "changeit".toCharArray()
        );

        SSLContext serverSslContext =
                SSLContext.getInstance("TLS");

        serverSslContext.init(
                keyManagerFactory.getKeyManagers(),
                null,
                null
        );

        // Start HTTPS server with the self-signed certificate.
        httpsServer = HttpsServer.create(
                new InetSocketAddress(
                        InetAddress.getByName("127.0.0.1"),
                        0
                ),
                0
        );

        httpsServer.setHttpsConfigurator(
                new HttpsConfigurator(serverSslContext)
        );

        httpsServer.createContext("/jwks", exchange -> {

            byte[] response = VALID_JWKS.getBytes(
                    StandardCharsets.UTF_8
            );

            exchange.getResponseHeaders().set(
                    "Content-Type",
                    "application/json"
            );

            exchange.sendResponseHeaders(200, response.length);

            exchange.getResponseBody().write(response);
            exchange.close();
        });

        httpsServer.start();

        String url = "https://jwks-test.example:"
                + httpsServer.getAddress().getPort()
                + "/jwks";

        // The test resolver maps this hostname to the local HTTPS server.
        // The certificate is not trusted by the default Java trust store.
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.fetchJwks(url)
        );

        assertEquals(
                502,
                exception.getStatusCode().value(),
                "An untrusted TLS certificate must be rejected"
        );
    }

    @Test
    void repeatedUnknownKeyRefreshShouldRespectCooldown()
            throws Exception {

        AtomicInteger requests = new AtomicInteger();

        server.createContext("/cooldown", exchange -> {

            requests.incrementAndGet();

            byte[] response = VALID_JWKS.getBytes(
                    StandardCharsets.UTF_8
            );

            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        String url = testUrl("/cooldown");

        service.getJwks(url, false);

        assertEquals(1, requests.get());

        service.refreshJwksForUnknownKey(url);

        assertEquals(
                1,
                requests.get(),
                "Unknown key must not immediately trigger another fetch"
        );

        testClock.advanceSeconds(29);

        service.refreshJwksForUnknownKey(url);

        assertEquals(
                1,
                requests.get(),
                "Refresh must remain throttled before 30 seconds"
        );

        testClock.advanceSeconds(1);

        service.refreshJwksForUnknownKey(url);

        assertEquals(
                2,
                requests.get(),
                "A refresh should be permitted after 30 seconds"
        );
    }

    @Test
    void failedJwksFetchShouldRespectCooldown()
            throws Exception {

        AtomicInteger requests = new AtomicInteger();

        server.createContext("/failed-cooldown", exchange -> {

            requests.incrementAndGet();

            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });

        String url = testUrl("/failed-cooldown");

        // First attempt reaches the server and fails.
        ResponseStatusException firstException = assertThrows(
                ResponseStatusException.class,
                () -> service.getJwks(url, false)
        );

        assertEquals(
                502,
                firstException.getStatusCode().value()
        );

        assertEquals(1, requests.get());

        // A repeated attempt within the cooldown must not reach the server.
        ResponseStatusException secondException = assertThrows(
                ResponseStatusException.class,
                () -> service.getJwks(url, false)
        );

        assertEquals(
                502,
                secondException.getStatusCode().value()
        );

        assertEquals(
                1,
                requests.get(),
                "Failed JWKS requests must also be throttled"
        );

        // Advance to the cooldown boundary.
        testClock.advanceSeconds(30);

        // A new attempt is now permitted.
        ResponseStatusException thirdException = assertThrows(
                ResponseStatusException.class,
                () -> service.getJwks(url, false)
        );

        assertEquals(
                502,
                thirdException.getStatusCode().value()
        );

        assertEquals(
                2,
                requests.get(),
                "A failed JWKS request should be retried after cooldown"
        );
    }

    @Test
    void normalCacheExpiryFetchShouldAlsoStartCooldown()
            throws Exception {

        AtomicInteger requests = new AtomicInteger();

        server.createContext("/cache-expiry", exchange -> {

            requests.incrementAndGet();

            byte[] response = VALID_JWKS.getBytes(
                    StandardCharsets.UTF_8
            );

            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        String url = testUrl("/cache-expiry");

        service.getJwks(url, false);

        assertEquals(1, requests.get());

        testClock.advanceSeconds(300);

        service.getJwks(url, false);

        assertEquals(
                2,
                requests.get(),
                "Expired JWKS cache should be refreshed"
        );

        service.refreshJwksForUnknownKey(url);

        assertEquals(
                2,
                requests.get(),
                "Unknown key must not trigger another fetch immediately after cache refresh"
        );
    }

    @Test
    void slowJwksResponseShouldBeRejectedAfterResponseTimeout()
            throws Exception {

        AtomicInteger requests = new AtomicInteger();

        server.createContext("/slow", exchange -> {

            requests.incrementAndGet();

            try {
                // Deliberately delay the response beyond the test timeout.
                Thread.sleep(2000);

                byte[] response = VALID_JWKS.getBytes(
                        StandardCharsets.UTF_8
                );

                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);

            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();

            } catch (java.io.IOException ignored) {
                // The client may already have closed the connection
                // after its response timeout.

            } finally {
                exchange.close();
            }
        });

        DnsResolver testDnsResolver = new DnsResolver() {

            @Override
            public InetAddress[] resolve(String hostname)
                    throws UnknownHostException {

                return new InetAddress[]{
                        InetAddress.getByName("127.0.0.1")
                };
            }

            @Override
            public String resolveCanonicalHostname(String hostname) {
                return hostname;
            }
        };

        LtiTokenValidationService timeoutService =
                new LtiTokenValidationService(
                        endpointSecurityService,
                        testDnsResolver,
                        testClock,
                        1
                );

        try {
            ResponseStatusException exception = assertThrows(
                    ResponseStatusException.class,
                    () -> timeoutService.fetchJwks(testUrl("/slow"))
            );

            assertEquals(
                    502,
                    exception.getStatusCode().value(),
                    "A JWKS response timeout should return 502 Bad Gateway"
            );

            assertEquals(
                    1,
                    requests.get(),
                    "The delayed JWKS endpoint should receive exactly one request"
            );

        } finally {
            timeoutService.closeHttpClient();
        }
    }

    private String testUrl(String path) {

        return "http://jwks-test.example:"
                + server.getAddress().getPort()
                + path;
    }

    private static class MutableClock extends Clock {

        private final AtomicReference<Instant> currentInstant;

        MutableClock(Instant initialInstant) {
            this.currentInstant = new AtomicReference<>(initialInstant);
        }

        void advanceSeconds(long seconds) {
            currentInstant.updateAndGet(
                    instant -> instant.plusSeconds(seconds)
            );
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return currentInstant.get();
        }
    }
}
