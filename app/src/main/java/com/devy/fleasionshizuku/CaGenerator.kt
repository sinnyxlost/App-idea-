package com.devy.fleasionshizuku

import java.io.File

/**
 * Generates a local CA + a server certificate for the Roblox asset
 * domains, then bundles them into a PKCS12 keystore our proxy uses.
 *
 * All operations run via Shizuku shell (openssl is available on most
 * Android systems under /system/bin/openssl).
 */
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

    /**
     * Ensure CA + server cert + keystore exist. Idempotent.
     * Returns the keystore path on success, null on failure.
     */
    fun ensureEverything(
        shizuku: ShizukuManager,
        filesDir: File,
        log: (String) -> Unit = {}
    ): File? {
        val certDir = File(filesDir, "certs").apply { mkdirs() }
        val caKey   = File(certDir, "ca.key")
        val caCert  = File(certDir, "ca.pem")
        val srvKey  = File(certDir, "server.key")
        val srvCsr  = File(certDir, "server.csr")
        val srvCert = File(certDir, "server.pem")
        val conf    = File(certDir, "san.cnf")
        val keystore = File(certDir, "devy_keystore.p12")

        // ---- 1. CA ----
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

        // ---- 2. SAN config ----
        val san = ASSET_HOSTS.joinToString("\n") { "DNS.$it" }
        conf.writeText(
            """
            [req]
            distinguished_name = req_distinguished_name
            req_extensions = v3_req
            prompt = no

            [req_distinguished_name]
            CN = assetdelivery.roblox.com

            [v3_req]
            subjectAltName = @alt_names
            extendedKeyUsage = serverAuth

            [alt_names]
            $san
            """.trimIndent()
        )

        // ---- 3. Server key + CSR ----
        if (!srvCsr.exists()) {
            log("Generating server key + CSR...")
            shizuku.shellCapture(
                "openssl req -new -newkey rsa:2048 -nodes " +
                        "-keyout ${srvKey.absolutePath} " +
                        "-out ${srvCsr.absolutePath} " +
                        "-config ${conf.absolutePath}"
            )?.let { log(it) }
        }

        // ---- 4. Sign server cert with our CA ----
        if (!srvCert.exists()) {
            log("Signing server cert...")
            shizuku.shellCapture(
                "openssl x509 -req " +
                        "-in ${srvCsr.absolutePath} " +
                        "-CA ${caCert.absolutePath} " +
                        "-CAkey ${caKey.absolutePath} " +
                        "-CAcreateserial " +
                        "-out ${srvCert.absolutePath} " +
                        "-days 3650 " +
                        "-sha256 " +
                        "-extfile ${conf.absolutePath} " +
                        "-extensions v3_req"
            )?.let { log(it) }
        }

        // ---- 5. Bundle into PKCS12 keystore ----
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

        // ---- 6. Make sure app can read them ----
        shizuku.shellCapture("chmod 644 ${certDir.absolutePath}/* 2>/dev/null")

        return if (keystore.exists()) keystore else null
    }
}
