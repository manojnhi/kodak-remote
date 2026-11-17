package com.unofficial.kodakremote;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/** Builds a self-signed X.509 v3 certificate with plain JCA + hand-written DER (no BouncyCastle). */
public final class SelfSignedCert {
    private SelfSignedCert() {}

    private static byte[] tlv(int tag, byte[]... parts) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (byte[] p : parts) body.write(p, 0, p.length);
        byte[] b = body.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        if (b.length < 128) {
            out.write(b.length);
        } else if (b.length < 256) {
            out.write(0x81); out.write(b.length);
        } else if (b.length < 65536) {
            out.write(0x82); out.write(b.length >> 8); out.write(b.length & 0xFF);
        } else {
            out.write(0x83); out.write(b.length >> 16); out.write((b.length >> 8) & 0xFF); out.write(b.length & 0xFF);
        }
        out.write(b, 0, b.length);
        return out.toByteArray();
    }

    private static String utc(Date d) {
        SimpleDateFormat f = new SimpleDateFormat("yyMMddHHmmss'Z'");
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(d);
    }

    public static X509Certificate generate(KeyPair kp, String commonName) throws Exception {
        byte[] sigAlg = tlv(0x30,
                tlv(0x06, new byte[]{0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x01, 0x0B}),
                tlv(0x05));
        byte[] name = tlv(0x30, tlv(0x31, tlv(0x30,
                tlv(0x06, new byte[]{0x55, 0x04, 0x03}),
                tlv(0x0C, commonName.getBytes("UTF-8")))));
        long now = System.currentTimeMillis();
        byte[] validity = tlv(0x30,
                tlv(0x17, utc(new Date(now - 86400000L)).getBytes("US-ASCII")),
                tlv(0x17, utc(new Date(now + 20L * 365 * 86400000L)).getBytes("US-ASCII")));
        byte[] serial = tlv(0x02, BigInteger.valueOf(now).toByteArray());
        byte[] version = tlv(0xA0, tlv(0x02, new byte[]{0x02}));
        byte[] tbs = tlv(0x30, version, serial, sigAlg, name, validity, name, kp.getPublic().getEncoded());

        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(kp.getPrivate());
        s.update(tbs);
        byte[] sig = s.sign();
        byte[] sigBits = new byte[sig.length + 1];
        System.arraycopy(sig, 0, sigBits, 1, sig.length);

        byte[] cert = tlv(0x30, tbs, sigAlg, tlv(0x03, sigBits));
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(cert));
    }
}