package com.devy.fleasionshizuku

import java.io.File

object CaGenerator {

    private val ASSET_HOSTS = listOf(
        "assetdelivery.roblox.com",
        "assetdelivery.rbxcdn.com",
        "*.rbxcdn.com",
        "rbxcdn.com",
        "c0.rbxcdn.com", "c1.rbxcdn.com", "c2.rbxcdn.com",
        "c3.rbxcdn.com", "c4.rbxcdn.com", "c5.rbxcdn.com",
        "c6.rbxcdn.com", "c7.rbxcdn.com", "c8.rbxcdn.com",
        "c9.rbxcdn.com",
        "data.roblox.com",
        "clientsettings.roblox.com",
        "clientsettingscdn.roblox.com"
    )

    fun ensureEverything(
        shizuku: ShizukuManager,
        filesDir: File,
        log: (String) -> Unit = {}
    ): File? {
        val certDir = File(filesDir, "certs").apply { mkdirs() }
        val caKey    = File(certDir, "ca.key")
        val caCert   = File(certDir, "ca.pem")
        val srvKey   = File(certDir, "server.key")
        val srvCsr   = File(certDir, "server.csr")
        val srvCert  = File(certDir, "server.pem")
        val conf     = File(certDir, "san.cnf")
        val keystore = File(certDir, "devy_keystore.p12")

        // 1. CA
        if (!caCert.exists() || !caKey.exists()) {
            log("Generating CA...")
            shizuku.shellCapture(
                "openssl req -x509 -newkey rsa:2048 -nodes " +
                        "-keyout ${caKey.absolutePath} " +
                        "-out ${caCert.absolutePath} " +
                        "-days 3650 " +
                        "-subj '/CN=Devy Fleasion CA/O=Devy/C=US' " +
                        "-addext 'basicConstraints=critical,CA:TRUE' " +
                        "-addext 'keyUsage=critical,keyCertSign,cRLSign'"
            )?.let { log(it) }
        }

        // 2. SAN config
        val san = ASSET_HOSTS.joinToString("\n") { "DNS.$it" }
        conf.writeText(
            "[req]\n" +
            "distinguished_name = req_distinguished_name\n" +
            "req_extensions = v3_req\n" +
            "prompt = no\n\n" +
            "[req_distinguished_name]\n" +
            "CN = assetdelivery.roblox.com\n\n" +
            "[v3_req]\n" +
            "subjectAltName = @alt_names\n" +
            "extendedKeyUsage = serverAuth\n\n" +
            "[alt_names]\n" +
            "$san\n"
        )

        // 3. Server key + CSR
        if (!srvCsr.exists()) {
            log("Generating server key + CSR...")
            shizuku.shellCapture(
                "openssl req -new -newkey rsa:2048 -nodes " +
                        "-keyout ${srvKey.absolutePath} " +
                        "-out ${srvCsr.absolutePath} " +
                        "-config ${conf.absolutePath}"
            )?.let { log(it) }
        }

        // 4. Sign server cert
        if (!srvCert.exists()) {
            log("Signing server cert...")
            shizuku.shellCapture(
                "openssl x509 -req " +
                        "-in ${srvCsr.absolutePath} " +
                        "-CA ${caCert.absolutePath} " +
                        "-CAkey ${caKey.absolutePath} " +
                        "-CAcreateserial " +
                        "-out ${srvCert.absolutePath} " +
                        "-days 3650 -sha256 " +
                        "-extfile ${conf.absolutePath} " +
                        "-extensions v3_req"
            )?.let { log(it) }
        }

        // 5. PKCS12 keystore
        if (!keystore.exists()) {
            log("Building PKCS12 keystore...")
            shizuku.shellCapture(
                "openssl pkcs12 -export " +
                        "-in ${srvCert.absolutePath} " +
                        "-inkey ${srvKey.absolutePath} " +
                        "-certfile ${caCert.absolutePath} " +
                        "-name devy " +
                        "-out ${keystore.absolutePath} " +
                        "-passout pass:devy"
            )?.let { log(it) }
        }

        shizuku.shellCapture("chmod 644 ${certDir.absolutePath}/* 2>/dev/null")

        return if (keystore.exists()) keystore else null
    }
}
