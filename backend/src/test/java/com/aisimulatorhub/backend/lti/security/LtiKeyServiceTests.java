
package com.aisimulatorhub.backend.lti.security;

import com.nimbusds.jose.jwk.RSAKey;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LtiKeyServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void shouldGenerateSigningKeysWhenNoKeyFilesExist()
            throws Exception {

        LtiKeyService service =
                new LtiKeyService(tempDir.toString());

        service.initialize();

        assertTrue(Files.exists(
                tempDir.resolve("hub-private.pem")
        ));

        assertTrue(Files.exists(
                tempDir.resolve("hub-public.pem")
        ));

        assertTrue(Files.exists(
                tempDir.resolve("hub-kid.txt")
        ));

        assertNotNull(service.getSigningKey());

        assertTrue(service.getSigningKey().isPrivate());

        assertFalse(service.getPublicJwk().isPrivate());

        assertEquals(
                36,
                service.getKeyId().length()
        );
    }

    @Test
    void shouldPreserveExistingKeysAndKeyIdAfterRestart()
            throws Exception {

        LtiKeyService firstService =
                new LtiKeyService(tempDir.toString());

        firstService.initialize();

        String originalKeyId =
                firstService.getKeyId();

        String originalPublicKey =
                Files.readString(
                        tempDir.resolve("hub-public.pem")
                );

        String originalPrivateKey =
                Files.readString(
                        tempDir.resolve("hub-private.pem")
                );

        LtiKeyService secondService =
                new LtiKeyService(tempDir.toString());

        secondService.initialize();

        assertEquals(
                originalKeyId,
                secondService.getKeyId()
        );

        assertEquals(
                originalPublicKey,
                Files.readString(
                        tempDir.resolve("hub-public.pem")
                )
        );

        assertEquals(
                originalPrivateKey,
                Files.readString(
                        tempDir.resolve("hub-private.pem")
                )
        );
    }

    @Test
    void shouldRejectIncompleteKeySet()
            throws Exception {

        LtiKeyService service =
                new LtiKeyService(tempDir.toString());

        service.initialize();

        Files.delete(
                tempDir.resolve("hub-kid.txt")
        );

        LtiKeyService restartedService =
                new LtiKeyService(tempDir.toString());

        assertThrows(
                IllegalStateException.class,
                restartedService::initialize
        );

        assertFalse(
                Files.exists(tempDir.resolve("hub-kid.txt"))
        );
    }

    @Test
    void shouldRejectMismatchedPublicAndPrivateKeys()
            throws Exception {

        Path firstDirectory =
                Files.createDirectory(
                        tempDir.resolve("first")
                );

        Path secondDirectory =
                Files.createDirectory(
                        tempDir.resolve("second")
                );

        LtiKeyService firstService =
                new LtiKeyService(firstDirectory.toString());

        firstService.initialize();

        LtiKeyService secondService =
                new LtiKeyService(secondDirectory.toString());

        secondService.initialize();

        Files.copy(
                secondDirectory.resolve("hub-public.pem"),
                firstDirectory.resolve("hub-public.pem"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
        );

        LtiKeyService restartedService =
                new LtiKeyService(firstDirectory.toString());

        assertThrows(
                IllegalStateException.class,
                restartedService::initialize
        );
    }

    @Test
    void shouldExposeOnlyPublicKeyThroughJwks()
            throws Exception {

        LtiKeyService service =
                new LtiKeyService(tempDir.toString());

        service.initialize();

        RSAKey publicJwk = service.getPublicJwk();

        assertFalse(publicJwk.isPrivate());

        assertNull(publicJwk.getPrivateExponent());

        assertEquals(
                "RS256",
                publicJwk.getAlgorithm().getName()
        );

        assertEquals(
                service.getKeyId(),
                publicJwk.getKeyID()
        );
    }
}
