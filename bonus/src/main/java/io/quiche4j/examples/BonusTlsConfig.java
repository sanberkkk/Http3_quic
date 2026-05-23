package io.quiche4j.examples;

import java.io.IOException;

import io.quiche4j.Config;
import io.quiche4j.ConfigBuilder;
import io.quiche4j.Quiche;
import io.quiche4j.Utils;
import io.quiche4j.http3.Http3;

/**
 * Shared QUIC/TLS config for bonus Http3Server and Http3Client.
 */
final class BonusTlsConfig {

    private BonusTlsConfig() {}

    static ConfigBuilder baseBuilder(boolean verifyPeer) throws IOException {
        ConfigBuilder b = new ConfigBuilder(Quiche.PROTOCOL_VERSION)
                .withApplicationProtos(Http3.APPLICATION_PROTOCOL)
                .withVerifyPeer(verifyPeer)
                .loadCertChainFromPemFile(Utils.copyFileFromJAR("certs", "/cert.crt"))
                .loadPrivKeyFromPemFile(Utils.copyFileFromJAR("certs", "/cert.key"))
                .withMaxIdleTimeout(5_000)
                .withMaxUdpPayloadSize(1350)
                .withInitialMaxData(10_000_000)
                .withInitialMaxStreamDataBidiLocal(1_000_000)
                .withInitialMaxStreamDataBidiRemote(1_000_000)
                .withInitialMaxStreamDataUni(1_000_000)
                .withInitialMaxStreamsBidi(100)
                .withInitialMaxStreamsUni(100)
                .withDisableActiveMigration(true);
        if (System.getenv("SSLKEYLOGFILE") != null) {
            b.enableLogKeys();
            System.out.println("> SSLKEYLOGFILE enabled: " + System.getenv("SSLKEYLOGFILE"));
        }
        return b;
    }

    static Config serverConfig() throws IOException {
        return baseBuilder(false).enableEarlyData().build();
    }

    static Config clientConfig(String host) throws IOException {
        boolean local = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
        return baseBuilder(!local).build();
    }
}
