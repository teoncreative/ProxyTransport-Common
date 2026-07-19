/*
 * Copyright (c) 2024-2026 NetherGamesMC
 * Licensed under the MIT license.
 */

package org.nethergames.proxytransport.common.util;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Date;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Mints an ephemeral self-signed certificate for a QUIC listener. Uses BouncyCastle directly rather than
 * Netty's {@code SelfSignedCertificate}, which resolves BouncyCastle through the host's classloader where it is
 * often absent. The proxy trusts any certificate, so only validity matters.
 */
public final class SelfSignedCertificateGenerator {

    private SelfSignedCertificateGenerator() {
    }

    public record Result(PrivateKey privateKey, X509Certificate certificate) {
    }

    public static Result generate(String commonName) throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        X500Principal subject = new X500Principal("CN=" + commonName);
        long now = System.currentTimeMillis();
        Date notBefore = new Date(now - 86_400_000L);            // yesterday
        Date notAfter = new Date(now + 3650L * 86_400_000L);     // ~10 years
        BigInteger serial = BigInteger.valueOf(now);

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        X509CertificateHolder holder = new JcaX509v3CertificateBuilder(
            subject, serial, notBefore, notAfter, subject, keyPair.getPublic()
        ).build(signer);

        X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(holder);
        return new Result(keyPair.getPrivate(), certificate);
    }
}