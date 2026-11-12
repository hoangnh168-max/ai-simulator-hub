
package com.aisimulatorhub.backend.lti.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LtiPlatformBaseUrlValidatorTests {

    @Test
    void acceptsLocalhostHttp() {
        assertDoesNotThrow(() ->
                new LtiPlatformBaseUrlValidator(
                        "http://localhost:8080"
                ).validate("http://localhost:8080")
        );
    }

    @Test
    void acceptsLoopbackIpv4Http() {
        assertDoesNotThrow(() ->
                new LtiPlatformBaseUrlValidator(
                        "http://127.0.0.1:8080"
                ).validate("http://127.0.0.1:8080")
        );
    }

    @Test
    void acceptsProductionHttpsOrigin() {
        assertDoesNotThrow(() ->
                new LtiPlatformBaseUrlValidator(
                        "https://lti.example.com"
                ).validate("https://lti.example.com")
        );
    }

    @Test
    void acceptsHttpsOriginWithTrailingSlash() {
        assertDoesNotThrow(() ->
                new LtiPlatformBaseUrlValidator(
                        "https://lti.example.com/"
                ).validate("https://lti.example.com/")
        );
    }

    @Test
    void rejectsPublicHttpUrl() {
        assertThrows(
                IllegalStateException.class,
                () -> new LtiPlatformBaseUrlValidator(
                        "http://example.com"
                ).validate("http://example.com")
        );
    }

    @Test
    void rejectsUserInfo() {
        assertThrows(
                IllegalStateException.class,
                () -> new LtiPlatformBaseUrlValidator(
                        "https://user:pass@example.com"
                ).validate("https://user:pass@example.com")
        );
    }

    @Test
    void rejectsUnexpectedPath() {
        assertThrows(
                IllegalStateException.class,
                () -> new LtiPlatformBaseUrlValidator(
                        "https://example.com/api"
                ).validate("https://example.com/api")
        );
    }

    @Test
    void rejectsQueryString() {
        assertThrows(
                IllegalStateException.class,
                () -> new LtiPlatformBaseUrlValidator(
                        "https://example.com?x=1"
                ).validate("https://example.com?x=1")
        );
    }

    @Test
    void rejectsFragment() {
        assertThrows(
                IllegalStateException.class,
                () -> new LtiPlatformBaseUrlValidator(
                        "https://example.com#fragment"
                ).validate("https://example.com#fragment")
        );
    }

    @Test
    void rejectsInvalidScheme() {
        assertThrows(
                IllegalStateException.class,
                () -> new LtiPlatformBaseUrlValidator(
                        "ftp://example.com"
                ).validate("ftp://example.com")
        );
    }

    @Test
    void rejectsMissingHost() {
        assertThrows(
                IllegalStateException.class,
                () -> new LtiPlatformBaseUrlValidator(
                        "https:///lti"
                ).validate("https:///lti")
        );
    }

    @Test
    void rejectsInvalidPort() {
        assertThrows(
                IllegalStateException.class,
                () -> new LtiPlatformBaseUrlValidator(
                        "https://example.com:70000"
                ).validate("https://example.com:70000")
        );
    }

    @Test
    void rejectsPortZero() {
        assertThrows(
                IllegalStateException.class,
                () -> new LtiPlatformBaseUrlValidator(
                        "https://example.com:0"
                ).validate("https://example.com:0")
        );
    }

    @Test
    void rejectsBlankValue() {
        assertThrows(
                IllegalStateException.class,
                () -> new LtiPlatformBaseUrlValidator(
                        " "
                ).validate(" ")
        );
    }

    @Test
    void rejectsMalformedUrl() {
        assertThrows(
                IllegalStateException.class,
                () -> new LtiPlatformBaseUrlValidator(
                        "not-a-url"
                ).validate("not-a-url")
        );
    }
}
