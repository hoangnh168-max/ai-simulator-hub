
package com.aisimulatorhub.backend.lti.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

@Service
public class LtiEndpointSecurityService {

    /**
     * Validates an HTTPS endpoint and all addresses returned by DNS.
     */
    public void validatePublicHttpsUrl(String value) {

        try {
            if (value == null
                    || value.isBlank()
                    || !value.equals(value.trim())) {
                reject();
            }

            URI uri = URI.create(value);

            int port = uri.getPort();

            if (!uri.isAbsolute()
                    || !"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getHost().isBlank()
                    || uri.getRawUserInfo() != null
                    || uri.getFragment() != null
                    || uri.getRawAuthority() == null
                    || port == 0
                    || port < -1
                    || port > 65535) {
                reject();
            }

            resolvePublicHost(uri.getHost());

        } catch (ResponseStatusException exception) {
            throw exception;

        } catch (Exception exception) {
            throw forbidden();
        }
    }

    /**
     * Performs DNS resolution.
     *
     * Package-private so tests can provide deterministic DNS responses.
     */
    InetAddress[] lookupHostAddresses(String hostname)
            throws UnknownHostException {

        return InetAddress.getAllByName(hostname);
    }

    /**
     * Resolves a hostname exactly once, validates every returned address,
     * and returns those same addresses to the HTTP client's DNS resolver.
     *
     * The caller must use this returned array directly for the connection.
     * It must not perform another DNS lookup.
     */
    public InetAddress[] resolvePublicHost(String hostname)
            throws UnknownHostException {

        try {
            if (hostname == null || hostname.isBlank()) {
                throw unknownHost();
            }

            String host = hostname;

            // Remove IPv6 brackets if present.
            if (host.startsWith("[") && host.endsWith("]")) {
                host = host.substring(1, host.length() - 1);
            }

            host = host.toLowerCase(Locale.ROOT);

            validateHostname(host);

            // This is the only DNS lookup for this resolution operation.
            InetAddress[] addresses = lookupHostAddresses(host);

            if (addresses == null || addresses.length == 0) {
                throw unknownHost();
            }

            // Reject the entire hostname if ANY address is unsafe.
            for (InetAddress address : addresses) {
                if (address == null || !isPublicAddress(address)) {
                    throw unknownHost();
                }
            }

            // Return the exact validated addresses.
            return addresses.clone();

        } catch (UnknownHostException exception) {
            throw exception;

        } catch (Exception exception) {
            throw unknownHost();
        }
    }

    private void validateHostname(String host) {

        if (host == null || host.isBlank()) {
            reject();
        }

        // Reject local and non-public hostname namespaces.
        if (host.equals("localhost")
                || host.endsWith(".localhost")
                || host.endsWith(".local")
                || host.endsWith(".internal")
                || host.endsWith(".test")
                || host.endsWith(".invalid")
                || host.equals("home.arpa")
                || host.endsWith(".home.arpa")) {
            reject();
        }

        // IPv6 literals must not contain zone identifiers.
        if (host.contains("%")) {
            reject();
        }

        // IPv6 literals are validated through their resolved address.
        if (host.contains(":")) {

            if (!host.matches("[0-9a-f:.]+")) {
                reject();
            }

            return;
        }

        // Reject single-label hostnames.
        if (!host.contains(".")) {
            reject();
        }

        // Reject malformed DNS names.
        if (host.length() > 253
                || host.startsWith(".")
                || host.endsWith(".")
                || host.contains("..")) {
            reject();
        }

        String[] labels = host.split("\\.", -1);

        for (String label : labels) {

            if (label.isEmpty()
                    || label.length() > 63
                    || !label.matches(
                    "[a-z0-9](?:[a-z0-9-]*[a-z0-9])?"
            )) {
                reject();
            }
        }
    }

    private boolean isPublicAddress(InetAddress address) {

        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }

        byte[] bytes = address.getAddress();

        if (bytes.length == 4) {
            return isPublicIpv4(bytes);
        }

        if (bytes.length == 16) {
            return isPublicIpv6(bytes);
        }

        return false;
    }

    private boolean isPublicIpv4(byte[] ip) {

        int a = ip[0] & 0xFF;
        int b = ip[1] & 0xFF;
        int c = ip[2] & 0xFF;

        // Unspecified and "this network": 0.0.0.0/8
        if (a == 0) return false;

        // Private: 10.0.0.0/8
        if (a == 10) return false;

        // Shared address space / CGNAT: 100.64.0.0/10
        if (a == 100 && b >= 64 && b <= 127) return false;

        // Loopback: 127.0.0.0/8
        if (a == 127) return false;

        // Link-local: 169.254.0.0/16
        if (a == 169 && b == 254) return false;

        // Private: 172.16.0.0/12
        if (a == 172 && b >= 16 && b <= 31) return false;

        // IETF protocol assignments: 192.0.0.0/24
        if (a == 192 && b == 0 && c == 0) return false;

        // Documentation: 192.0.2.0/24
        if (a == 192 && b == 0 && c == 2) return false;

        // Deprecated relay: 192.88.99.0/24
        if (a == 192 && b == 88 && c == 99) return false;

        // Private: 192.168.0.0/16
        if (a == 192 && b == 168) return false;

        // Benchmarking: 198.18.0.0/15
        if (a == 198 && (b == 18 || b == 19)) return false;

        // Documentation: 198.51.100.0/24
        if (a == 198 && b == 51 && c == 100) return false;

        // Documentation: 203.0.113.0/24
        if (a == 203 && b == 0 && c == 113) return false;

        // Multicast and reserved: 224.0.0.0/4 and 240.0.0.0/4
        if (a >= 224) return false;

        return true;
    }

    private boolean isPublicIpv6(byte[] ip) {

        // Only accept IPv6 global-unicast space: 2000::/3.
        if (!matchesIpv6Prefix(ip,
                new int[]{0x20, 0x00, 0x00, 0x00}, 3)) {
            return false;
        }

        // IETF special-purpose assignments: 2001::/23.
        if (matchesIpv6Prefix(ip,
                new int[]{0x20, 0x01, 0x00}, 23)) {
            return false;
        }

        // Documentation: 2001:db8::/32.
        if (matchesIpv6Prefix(ip,
                new int[]{0x20, 0x01, 0x0D, 0xB8}, 32)) {
            return false;
        }

        // 6to4 transition mechanism: 2002::/16.
        if (matchesIpv6Prefix(ip,
                new int[]{0x20, 0x02}, 16)) {
            return false;
        }

        // Documentation: 3fff::/20.
        if (matchesIpv6Prefix(ip,
                new int[]{0x3F, 0xFF, 0x00}, 20)) {
            return false;
        }

        return true;
    }

    /**
     * Compares an IPv6 address against a prefix, bit by bit.
     */
    private boolean matchesIpv6Prefix(
            byte[] address,
            int[] prefix,
            int prefixLength) {

        if (address == null
                || address.length != 16
                || prefix == null
                || prefixLength < 0
                || prefixLength > 128
                || prefix.length < (prefixLength + 7) / 8) {
            return false;
        }

        for (int bit = 0; bit < prefixLength; bit++) {

            int addressByte = address[bit / 8] & 0xFF;
            int prefixByte = prefix[bit / 8] & 0xFF;

            int mask = 1 << (7 - (bit % 8));

            if ((addressByte & mask) != (prefixByte & mask)) {
                return false;
            }
        }

        return true;
    }

    private UnknownHostException unknownHost() {
        return new UnknownHostException(
                "Hostname is not permitted for outbound connections"
        );
    }

    private void reject() {
        throw forbidden();
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Platform endpoint URL is not permitted"
        );
    }
}
