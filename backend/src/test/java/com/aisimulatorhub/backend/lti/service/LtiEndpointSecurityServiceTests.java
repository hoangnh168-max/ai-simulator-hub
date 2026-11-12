
package com.aisimulatorhub.backend.lti.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.*;

class LtiEndpointSecurityServiceTests {

    private LtiEndpointSecurityService service;

    @BeforeEach
    void setUp() {
        service = new LtiEndpointSecurityService();
    }

    @Test
    void rejectsLocalhost() {

        assertThrows(
                ResponseStatusException.class,
                () -> service.validatePublicHttpsUrl(
                        "https://localhost/lti/jwks"
                )
        );
    }

    @Test
    void rejectsLoopbackIpv4() {

        assertThrows(
                ResponseStatusException.class,
                () -> service.validatePublicHttpsUrl(
                        "https://127.0.0.1/lti/jwks"
                )
        );
    }

    @Test
    void rejectsPrivateIpv4() {

        assertThrows(
                ResponseStatusException.class,
                () -> service.validatePublicHttpsUrl(
                        "https://192.168.1.10/lti/jwks"
                )
        );

        assertThrows(
                ResponseStatusException.class,
                () -> service.validatePublicHttpsUrl(
                        "https://10.0.0.5/lti/jwks"
                )
        );

        assertThrows(
                ResponseStatusException.class,
                () -> service.validatePublicHttpsUrl(
                        "https://172.16.0.10/lti/jwks"
                )
        );
    }

    @Test
    void rejectsCloudMetadataAddress() {

        assertThrows(
                ResponseStatusException.class,
                () -> service.validatePublicHttpsUrl(
                        "https://169.254.169.254/latest/meta-data/"
                )
        );
    }

    @Test
    void rejectsInvalidUrlSchemes() {

        assertThrows(
                ResponseStatusException.class,
                () -> service.validatePublicHttpsUrl(
                        "http://example.com/lti/jwks"
                )
        );
    }

    @Test
    void rejectsUrlsWithUserInfo() {

        assertThrows(
                ResponseStatusException.class,
                () -> service.validatePublicHttpsUrl(
                        "https://trusted.example.com@127.0.0.1/lti/jwks"
                )
        );
    }

    @Test
    void rejectsUrlsWithFragments() {

        assertThrows(
                ResponseStatusException.class,
                () -> service.validatePublicHttpsUrl(
                        "https://example.com/lti/jwks#fragment"
                )
        );
    }

    @Test
    void rejectsLocalHostnameNamespaces() {

        assertThrows(
                ResponseStatusException.class,
                () -> service.validatePublicHttpsUrl(
                        "https://service.internal/lti/jwks"
                )
        );

        assertThrows(
                ResponseStatusException.class,
                () -> service.validatePublicHttpsUrl(
                        "https://printer.local/lti/jwks"
                )
        );
    }

    @Test
    void rejectsIpv6Loopback() {

        assertThrows(
                ResponseStatusException.class,
                () -> service.validatePublicHttpsUrl(
                        "https://[::1]/lti/jwks"
                )
        );
    }

    @Test
    void rejectsIpv6UniqueLocalAddress() {

        assertThrows(
                ResponseStatusException.class,
                () -> service.validatePublicHttpsUrl(
                        "https://[fd00::1]/lti/jwks"
                )
        );
    }

    @Test
    void rejectsIpv6LinkLocalAddress() {

        assertThrows(
                ResponseStatusException.class,
                () -> service.validatePublicHttpsUrl(
                        "https://[fe80::1]/lti/jwks"
                )
        );
    }

    @Test
    void rejectsMalformedUrls() {

        assertThrows(
                ResponseStatusException.class,
                () -> service.validatePublicHttpsUrl(
                        "not-a-url"
                )
        );

        assertThrows(
                ResponseStatusException.class,
                () -> service.validatePublicHttpsUrl(
                        ""
                )
        );
    }

    @Test
    void rejectsIpv6IetfSpecialPurposeRange() {

        assertThrows(
                ResponseStatusException.class,
                () -> service.validatePublicHttpsUrl(
                        "https://[2001:0:1::1]/lti/jwks"
                )
        );
    }

    @Test
    void rejectsIpv6DocumentationRange2001Db8() {

        assertThrows(
                ResponseStatusException.class,
                () -> service.validatePublicHttpsUrl(
                        "https://[2001:db8::1]/lti/jwks"
                )
        );
    }

    @Test
    void rejectsIpv6SixToFourRange() {

        assertThrows(
                ResponseStatusException.class,
                () -> service.validatePublicHttpsUrl(
                        "https://[2002::1]/lti/jwks"
                )
        );
    }

    @Test
    void rejectsIpv6DocumentationRange3fff() {

        assertThrows(
                ResponseStatusException.class,
                () -> service.validatePublicHttpsUrl(
                        "https://[3fff::1]/lti/jwks"
                )
        );
    }

    @Test
    void rejectsMixedPublicAndPrivateDnsResults()
            throws Exception {

        LtiEndpointSecurityService testService =
                new LtiEndpointSecurityService() {

                    @Override
                    InetAddress[] lookupHostAddresses(String hostname)
                            throws UnknownHostException {

                        return new InetAddress[]{
                                InetAddress.getByAddress(
                                        new byte[]{8, 8, 8, 8}
                                ),
                                InetAddress.getByAddress(
                                        new byte[]{10, 0, 0, 1}
                                )
                        };
                    }
                };

        assertThrows(
                UnknownHostException.class,
                () -> testService.resolvePublicHost(
                        "mixed.example.com"
                )
        );
    }

    @Test
    void rejectsMixedPublicAndLoopbackDnsResults()
            throws Exception {

        LtiEndpointSecurityService testService =
                new LtiEndpointSecurityService() {

                    @Override
                    InetAddress[] lookupHostAddresses(String hostname)
                            throws UnknownHostException {

                        return new InetAddress[]{
                                InetAddress.getByAddress(
                                        new byte[]{1, 1, 1, 1}
                                ),
                                InetAddress.getByAddress(
                                        new byte[]{127, 0, 0, 1}
                                )
                        };
                    }
                };

        assertThrows(
                UnknownHostException.class,
                () -> testService.resolvePublicHost(
                        "mixed.example.com"
                )
        );
    }

    // Direct tests for the DNS resolver used by Apache HttpClient.

    @Test
    void dnsResolverRejectsLocalhost() {

        assertThrows(
                UnknownHostException.class,
                () -> service.resolvePublicHost("localhost")
        );
    }

    @Test
    void dnsResolverRejectsLoopbackIpv4() {

        assertThrows(
                UnknownHostException.class,
                () -> service.resolvePublicHost("127.0.0.1")
        );
    }

    @Test
    void dnsResolverRejectsPrivateIpv4() {

        assertThrows(
                UnknownHostException.class,
                () -> service.resolvePublicHost("192.168.1.10")
        );

        assertThrows(
                UnknownHostException.class,
                () -> service.resolvePublicHost("10.0.0.5")
        );

        assertThrows(
                UnknownHostException.class,
                () -> service.resolvePublicHost("172.16.0.10")
        );
    }

    @Test
    void dnsResolverRejectsCloudMetadataAddress() {

        assertThrows(
                UnknownHostException.class,
                () -> service.resolvePublicHost("169.254.169.254")
        );
    }

    @Test
    void dnsResolverRejectsIpv6Loopback() {

        assertThrows(
                UnknownHostException.class,
                () -> service.resolvePublicHost("::1")
        );
    }

    @Test
    void dnsResolverRejectsIpv6UniqueLocalAddress() {

        assertThrows(
                UnknownHostException.class,
                () -> service.resolvePublicHost("fd00::1")
        );
    }

    @Test
    void dnsResolverRejectsIpv6LinkLocalAddress() {

        assertThrows(
                UnknownHostException.class,
                () -> service.resolvePublicHost("fe80::1")
        );
    }

    @Test
    void dnsResolverRejectsIpv6SpecialPurposeRanges() {

        assertThrows(
                UnknownHostException.class,
                () -> service.resolvePublicHost("2001:0:1::1")
        );

        assertThrows(
                UnknownHostException.class,
                () -> service.resolvePublicHost("2001:db8::1")
        );

        assertThrows(
                UnknownHostException.class,
                () -> service.resolvePublicHost("2002::1")
        );

        assertThrows(
                UnknownHostException.class,
                () -> service.resolvePublicHost("3fff::1")
        );
    }
}
