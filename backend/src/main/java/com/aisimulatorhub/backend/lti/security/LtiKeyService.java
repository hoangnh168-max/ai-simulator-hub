
package com.aisimulatorhub.backend.lti.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

@Service
public class LtiKeyService {

    private final Path keyDirectory;

    private RSAKey signingKey;

    public LtiKeyService(
            @Value("${lti.keys.directory:${user.home}/.ai-simulator-hub/keys}")
            String keyDirectory
    ) {
        this.keyDirectory = Path.of(keyDirectory);
    }

    @jakarta.annotation.PostConstruct
    public synchronized void initialize() {
        try {
            Files.createDirectories(keyDirectory);

            Path privateKeyPath = keyDirectory.resolve("hub-private.pem");
            Path publicKeyPath = keyDirectory.resolve("hub-public.pem");
            Path keyIdPath = keyDirectory.resolve("hub-kid.txt");

            if (Files.exists(privateKeyPath)
                    && Files.exists(publicKeyPath)
                    && Files.exists(keyIdPath)) {

                loadExistingKeys(privateKeyPath, publicKeyPath, keyIdPath);

            } else {
                generateAndSaveKeys(privateKeyPath, publicKeyPath, keyIdPath);
            }

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to initialize AI Simulator Hub LTI signing keys",
                    exception
            );
        }
    }

    private void generateAndSaveKeys(
            Path privateKeyPath,
            Path publicKeyPath,
            Path keyIdPath
    ) throws Exception {

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);

        KeyPair keyPair = generator.generateKeyPair();

        RSAPrivateKey privateKey =
                (RSAPrivateKey) keyPair.getPrivate();

        RSAPublicKey publicKey =
                (RSAPublicKey) keyPair.getPublic();

        String keyId = UUID.randomUUID().toString();

        writePem(privateKeyPath, "PRIVATE KEY", privateKey.getEncoded());
        writePem(publicKeyPath, "PUBLIC KEY", publicKey.getEncoded());

        Files.writeString(
                keyIdPath,
                keyId,
                StandardCharsets.UTF_8
        );

        this.signingKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(keyId)
                .algorithm(JWSAlgorithm.RS256)
                .build();
    }

    private void loadExistingKeys(
            Path privateKeyPath,
            Path publicKeyPath,
            Path keyIdPath
    ) throws Exception {

        byte[] privateBytes = readPem(privateKeyPath, "PRIVATE KEY");
        byte[] publicBytes = readPem(publicKeyPath, "PUBLIC KEY");

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory.generatePrivate(
                new PKCS8EncodedKeySpec(privateBytes)
        );

        RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(
                new X509EncodedKeySpec(publicBytes)
        );

        String keyId = Files.readString(
                keyIdPath,
                StandardCharsets.UTF_8
        ).trim();

        if (keyId.isBlank()) {
            throw new IllegalStateException("LTI signing key ID is empty");
        }

        this.signingKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(keyId)
                .algorithm(JWSAlgorithm.RS256)
                .build();

        if (!signingKey.isPrivate()) {
            throw new IllegalStateException(
                    "LTI signing key does not contain a private key"
            );
        }
    }

    private void writePem(
            Path path,
            String type,
            byte[] encoded
    ) throws IOException {

        String base64 = Base64.getMimeEncoder(
                64,
                "\n".getBytes(StandardCharsets.UTF_8)
        ).encodeToString(encoded);

        String pem = "-----BEGIN " + type + "-----\n"
                + base64
                + "\n-----END " + type + "-----\n";

        Files.writeString(path, pem, StandardCharsets.US_ASCII);
    }

    private byte[] readPem(
            Path path,
            String type
    ) throws IOException {

        String pem = Files.readString(path, StandardCharsets.US_ASCII);

        String content = pem
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");

        return Base64.getDecoder().decode(content);
    }

    public RSAKey getSigningKey() {
        if (signingKey == null) {
            throw new IllegalStateException(
                    "LTI signing key has not been initialized"
            );
        }

        return signingKey;
    }

    public RSAKey getPublicJwk() {
        return getSigningKey().toPublicJWK();
    }

    public String getKeyId() {
        return getSigningKey().getKeyID();
    }
}
