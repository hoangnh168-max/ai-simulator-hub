
package com.aisimulatorhub.backend.lti.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.RSAKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class LtiKeyService {

    private static final int MINIMUM_RSA_KEY_SIZE = 2048;

    private static final byte[] KEY_PAIR_TEST_MESSAGE =
            "AI Simulator Hub LTI signing key validation"
                    .getBytes(StandardCharsets.UTF_8);

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

            setOwnerOnlyPermissionsIfSupported(
                    keyDirectory,
                    true
            );

            Path privateKeyPath =
                    keyDirectory.resolve("hub-private.pem");

            Path publicKeyPath =
                    keyDirectory.resolve("hub-public.pem");

            Path keyIdPath =
                    keyDirectory.resolve("hub-kid.txt");

            boolean privateExists = Files.exists(privateKeyPath);
            boolean publicExists = Files.exists(publicKeyPath);
            boolean keyIdExists = Files.exists(keyIdPath);

            int existingFileCount =
                    (privateExists ? 1 : 0)
                    + (publicExists ? 1 : 0)
                    + (keyIdExists ? 1 : 0);

            if (existingFileCount == 0) {

                generateAndSaveKeys(
                        privateKeyPath,
                        publicKeyPath,
                        keyIdPath
                );

            } else if (existingFileCount == 3) {

                loadExistingKeys(
                        privateKeyPath,
                        publicKeyPath,
                        keyIdPath
                );

            } else {

                throw new IllegalStateException(
                        "Incomplete LTI signing key set. "
                        + "Expected all three files: "
                        + "hub-private.pem, hub-public.pem, hub-kid.txt. "
                        + "Existing keys will not be regenerated automatically."
                );
            }

            setOwnerOnlyPermissionsIfSupported(
                    privateKeyPath,
                    false
            );

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

        KeyPairGenerator generator =
                KeyPairGenerator.getInstance("RSA");

        generator.initialize(MINIMUM_RSA_KEY_SIZE);

        KeyPair keyPair = generator.generateKeyPair();

        RSAPrivateKey privateKey =
                (RSAPrivateKey) keyPair.getPrivate();

        RSAPublicKey publicKey =
                (RSAPublicKey) keyPair.getPublic();

        validateKeyPair(publicKey, privateKey);

        String keyId = UUID.randomUUID().toString();

        writePem(
                privateKeyPath,
                "PRIVATE KEY",
                privateKey.getEncoded()
        );

        writePem(
                publicKeyPath,
                "PUBLIC KEY",
                publicKey.getEncoded()
        );

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

        if (!Files.isRegularFile(privateKeyPath)
                || !Files.isRegularFile(publicKeyPath)
                || !Files.isRegularFile(keyIdPath)) {

            throw new IllegalStateException(
                    "LTI signing key paths must all be regular files"
            );
        }

        byte[] privateBytes =
                readPem(privateKeyPath, "PRIVATE KEY");

        byte[] publicBytes =
                readPem(publicKeyPath, "PUBLIC KEY");

        KeyFactory keyFactory =
                KeyFactory.getInstance("RSA");

        RSAPrivateKey privateKey =
                (RSAPrivateKey) keyFactory.generatePrivate(
                        new PKCS8EncodedKeySpec(privateBytes)
                );

        RSAPublicKey publicKey =
                (RSAPublicKey) keyFactory.generatePublic(
                        new X509EncodedKeySpec(publicBytes)
                );

        String keyId = Files.readString(
                keyIdPath,
                StandardCharsets.UTF_8
        ).trim();

        if (keyId.isBlank()) {

            throw new IllegalStateException(
                    "LTI signing key ID is empty"
            );
        }

        validateKeyPair(publicKey, privateKey);

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

    private void validateKeyPair(
            RSAPublicKey publicKey,
            RSAPrivateKey privateKey
    ) throws Exception {

        if (publicKey == null || privateKey == null) {

            throw new IllegalStateException(
                    "LTI RSA public or private key is missing"
            );
        }

        if (publicKey.getModulus().bitLength()
                < MINIMUM_RSA_KEY_SIZE
                || privateKey.getModulus().bitLength()
                < MINIMUM_RSA_KEY_SIZE) {

            throw new IllegalStateException(
                    "LTI RSA key size must be at least "
                    + MINIMUM_RSA_KEY_SIZE
                    + " bits"
            );
        }

        if (!publicKey.getModulus().equals(
                privateKey.getModulus()
        )) {

            throw new IllegalStateException(
                    "LTI public and private RSA keys do not match"
            );
        }

        Signature signer =
                Signature.getInstance("SHA256withRSA");

        signer.initSign(privateKey);
        signer.update(KEY_PAIR_TEST_MESSAGE);

        byte[] signature = signer.sign();

        Signature verifier =
                Signature.getInstance("SHA256withRSA");

        verifier.initVerify(publicKey);
        verifier.update(KEY_PAIR_TEST_MESSAGE);

        if (!verifier.verify(signature)) {

            throw new IllegalStateException(
                    "LTI public and private RSA keys failed "
                    + "cryptographic consistency verification"
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

        String pem =
                "-----BEGIN " + type + "-----\n"
                + base64
                + "\n-----END " + type + "-----\n";

        Files.writeString(
                path,
                pem,
                StandardCharsets.US_ASCII
        );
    }

    private byte[] readPem(
            Path path,
            String type
    ) throws IOException {

        String pem = Files.readString(
                path,
                StandardCharsets.US_ASCII
        );

        String content = pem
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");

        if (content.isBlank()) {

            throw new IllegalStateException(
                    "LTI key file is empty: " + path.getFileName()
            );
        }

        return Base64.getDecoder().decode(content);
    }

    private void setOwnerOnlyPermissionsIfSupported(
            Path path,
            boolean directory
    ) throws IOException {

        String operatingSystem = System.getProperty("os.name")
                .toLowerCase(Locale.ROOT);

        // Windows: explicitly enforce the filesystem ACL.
        if (operatingSystem.contains("win")) {

            AclFileAttributeView aclView =
                    Files.getFileAttributeView(
                            path,
                            AclFileAttributeView.class
                    );

            if (aclView == null) {
                throw new IOException(
                        "Windows ACL support is unavailable for: " + path
                );
            }

            UserPrincipalLookupService lookup =
                    path.getFileSystem().getUserPrincipalLookupService();

            UserPrincipal owner = Files.getOwner(path);

            UserPrincipal system =
                    lookup.lookupPrincipalByName(
                            "NT AUTHORITY\\SYSTEM"
                    );

            UserPrincipal administrators =
                    lookup.lookupPrincipalByName(
                            "BUILTIN\\Administrators"
                    );

            EnumSet<AclEntryPermission> fullControl =
                    EnumSet.allOf(AclEntryPermission.class);

            Set<AclEntryFlag> flags = directory
                    ? EnumSet.of(
                             AclEntryFlag.FILE_INHERIT,
                            AclEntryFlag.DIRECTORY_INHERIT
                    )
                    : EnumSet.noneOf(AclEntryFlag.class);

            List<AclEntry> entries = new ArrayList<>();

            entries.add(
                    AclEntry.newBuilder()
                            .setType(AclEntryType.ALLOW)
                            .setPrincipal(owner)
                            .setPermissions(fullControl)
                            .setFlags(flags)
                            .build()
            );

            entries.add(
                    AclEntry.newBuilder()
                            .setType(AclEntryType.ALLOW)
                            .setPrincipal(system)
                            .setPermissions(fullControl)
                            .setFlags(flags)
                            .build()
            );

            entries.add(
                    AclEntry.newBuilder()
                            .setType(AclEntryType.ALLOW)
                            .setPrincipal(administrators)
                            .setPermissions(fullControl)
                            .setFlags(flags)
                            .build()
            );

            aclView.setAcl(entries);

            return;
        }

        // Linux/macOS: use POSIX owner-only permissions.
        if (Files.getFileAttributeView(
                path,
                PosixFileAttributeView.class
        ) != null) {

            Set<PosixFilePermission> permissions =
                    directory
                            ? EnumSet.of(
                                    PosixFilePermission.OWNER_READ,
                                    PosixFilePermission.OWNER_WRITE,
                                    PosixFilePermission.OWNER_EXECUTE
                            )
                            : EnumSet.of(
                                    PosixFilePermission.OWNER_READ,
                                    PosixFilePermission.OWNER_WRITE
                            );

            Files.setPosixFilePermissions(path, permissions);
        }
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
