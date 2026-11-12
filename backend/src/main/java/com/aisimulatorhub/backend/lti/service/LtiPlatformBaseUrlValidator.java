
package com.aisimulatorhub.backend.lti.service;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

@Component
public class LtiPlatformBaseUrlValidator implements InitializingBean {

    private final String platformBaseUrl;

    public LtiPlatformBaseUrlValidator(
            @Value("${lti.platform.base-url}") String platformBaseUrl) {
        this.platformBaseUrl = platformBaseUrl;
    }

    @Override
    public void afterPropertiesSet() {
        validate(platformBaseUrl);
    }

    public String getValidatedBaseUrl() {
        validate(platformBaseUrl);
        return platformBaseUrl;
    }

    public void validate(String value) {

        if (value == null
                || value.isBlank()
                || !value.equals(value.trim())) {
            throw invalidConfiguration();
        }

        try {
            URI uri = URI.create(value);

            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();

            if (!uri.isAbsolute()
                    || scheme == null
                    || host == null
                    || host.isBlank()
                    || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || uri.getRawAuthority() == null
                    || port == 0
                    || port < -1
                    || port > 65535
                    || (uri.getRawPath() != null
                        && !uri.getRawPath().isEmpty()
                        && !uri.getRawPath().equals("/"))) {

                throw invalidConfiguration();
            }

            scheme = scheme.toLowerCase(Locale.ROOT);
            host = host.toLowerCase(Locale.ROOT);

            if ("https".equals(scheme)) {
                return;
            }

            if ("http".equals(scheme)
                    && isLocalDevelopmentHost(host)) {
                return;
            }

            throw invalidConfiguration();

        } catch (IllegalArgumentException exception) {
            throw invalidConfiguration();
        }
    }

    private boolean isLocalDevelopmentHost(String host) {

        String normalizedHost = host;

        if (normalizedHost.startsWith("[")
                && normalizedHost.endsWith("]")) {
            normalizedHost = normalizedHost.substring(
                    1,
                    normalizedHost.length() - 1
            );
        }

        normalizedHost = normalizedHost.toLowerCase(Locale.ROOT);

        return normalizedHost.equals("localhost")
                || normalizedHost.endsWith(".localhost")
                || normalizedHost.equals("127.0.0.1")
                || normalizedHost.equals("::1");
    }

    private IllegalStateException invalidConfiguration() {
        return new IllegalStateException(
                "Invalid lti.platform.base-url. "
                        + "Use an HTTPS origin in production, "
                        + "or HTTP only for localhost development. "
                        + "Paths, credentials, query parameters, "
                        + "and fragments are not permitted."
        );
    }
}
