package com.highsockscapital.sunshine.termux

/** Run only during explicit setup, as the Termux app user, never during passive inspection. */
internal object TermuxRuntimePackages {
    const val TimeoutMillis = 20 * 60 * 1_000L

    // Termux is rolling-release: avoid partial upgrades before installing native dependencies.
    // Refuse removals, preserve local configuration, and stop at the first failed command.
    val installScript: String = """
        set -eu
        export DEBIAN_FRONTEND=noninteractive
        apt-get -o APT::Update::Error-Mode=any update
        apt-get -o Dpkg::Options::=--force-confold upgrade -y --with-new-pkgs --no-remove
        apt-get -o Dpkg::Options::=--force-confold install -y --no-remove nodejs openssl clang make pkg-config python git
        node --version
        openssl version
        node -e 'require("node:crypto").createHash("sha256").update("sunshine").digest("hex"); require("node:tls").createSecureContext(); console.log("Sunshine Node/OpenSSL probe passed")'
    """.trimIndent()
}
