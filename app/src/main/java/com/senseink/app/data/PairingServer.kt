package com.senseink.app.data

import android.content.Context
import com.senseink.app.R
import fi.iki.elonen.NanoHTTPD
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.security.KeyStore
import java.security.SecureRandom
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * A tiny local HTTP(S) server that lets a desktop browser on the same WiFi
 * network paste the harvested refresh_token instead of typing it on the
 * Kompakt's own keyboard - see PROJECT-STATUS.md for why manual/scripted
 * on-device entry turned out to be unreliable in practice.
 *
 * ## Why this speaks both HTTP and HTTPS on the *same* port
 *
 * A LAN IP has no real domain name, so there's no way to get a
 * browser-trusted certificate for it - a real CA won't issue one for a bare
 * IP. The only option is a self-signed certificate, which every browser
 * flags identically: a full-page "connection not private" interstitial the
 * user has to click through "Advanced" to bypass. That's true no matter how
 * the certificate is generated - a fresh one minted per pairing session is
 * just as untrusted to a browser as a fixed one, since trust here comes
 * from the issuing CA, not the generation method. So this uses one
 * certificate, generated once (`keytool`, 100-year validity) and bundled as
 * `res/raw/pairing_keystore.p12` - no runtime cert-generation dependency
 * (no Bouncy Castle; `java.security`/`javax.net.ssl` alone are enough to
 * load and serve it). [KEYSTORE_PASSWORD] isn't a real secret - it ships in
 * the same APK as the file it "protects" - it exists only because
 * `KeyStore`'s API requires one.
 *
 * That file cannot be regenerated with a bare `keytool -genkeypair`: a
 * default-settings JDK 25 keystore crashed this app outright on the
 * physical Kompakt with `InvalidKeyException: No installed provider
 * supports this key: com.android.org.bouncycastle.jcajce.PKCS12Key` -
 * Android's built-in (and more restricted) PKCS12 reader doesn't support
 * the SHA-256/AES-256 PBE scheme modern JDKs default to since keytool
 * changed defaults. Regenerate with the legacy algorithms Android actually
 * supports instead:
 * ```
 * keytool -genkeypair -keystore pairing_keystore.p12 -storetype PKCS12 \
 *   -J-Dkeystore.pkcs12.certProtectionAlgorithm=PBEWithSHA1AndDESede \
 *   -J-Dkeystore.pkcs12.keyProtectionAlgorithm=PBEWithSHA1AndDESede \
 *   -J-Dkeystore.pkcs12.macAlgorithm=HmacPBESHA1 \
 *   -keyalg RSA -keysize 2048 -validity 36500 -alias pairing \
 *   -dname "CN=SensE-ink Pairing, OU=SensE-ink, O=SensE-ink" \
 *   -storepass sensieink-pairing-nonsecret -keypass sensieink-pairing-nonsecret
 * ```
 *
 * Plain HTTP stays the *primary*, recommended path in every place this
 * server's address is shown - no security warning, simplest for a user to
 * follow. HTTPS exists as a fallback for the specific failure this project
 * hit in practice: Chrome/Edge try `https://` on their own for an address
 * typed without a scheme, on the *exact port they were given* (not 443)
 * before this existed, that attempt died with a bare connection error and
 * no fallback. Now that same automatic attempt completes a real TLS
 * handshake against this cert, so the user sees an actionable "not private"
 * warning - click through it - rather than a dead end.
 *
 * Making both protocols answer on one port (rather than running a second
 * NanoHTTPD instance on a different port for HTTPS) isn't a nicety - it's
 * required. A browser's automatic https retry reuses the exact port from
 * the address it was given; a second server listening elsewhere would never
 * actually be reached by that retry. NanoHTTPD has no built-in support for
 * this, so [DualProtocolServerSocketFactory] below installs a custom
 * [ServerSocket] that peeks the first byte of each new connection - `0x16`
 * unambiguously means a TLS ClientHello (RFC 8446 §5.1; no HTTP method name
 * starts with that byte) - and wraps the connection in TLS or hands it to
 * NanoHTTPD as plain HTTP accordingly, replaying the peeked byte either
 * way so nothing is lost from the actual request.
 *
 * [pin] is what actually protects this exchange either way - anyone
 * submitting to this server has to be looking at the Kompakt's own screen
 * to read it. The plain-HTTP path still sends the token unencrypted on the
 * wire, same as before; only the HTTPS fallback path encrypts it in
 * transit. On a network you don't trust, sticking to the primary HTTP
 * instructions doesn't change that gap - it's still worth knowing before
 * relying on this.
 *
 * Only ever running while SetupScreen's paste step is on screen - started
 * and stopped by that composable, not a persistent service.
 */
class PairingServer(
    context: Context,
    private val onTokenReceived: (String) -> Unit,
) : NanoHTTPD(0) {

    val pin: String = SecureRandom().nextInt(1_000_000).toString().padStart(6, '0')

    init {
        setServerSocketFactory(DualProtocolServerSocketFactory(loadSslSocketFactory(context.applicationContext)))
    }

    override fun serve(session: IHTTPSession): Response = when {
        session.method == Method.GET && session.uri == "/" ->
            html(Response.Status.OK, PAGE)
        session.method == Method.POST && session.uri == "/submit" ->
            handleSubmit(session)
        else ->
            html(Response.Status.NOT_FOUND, page("Not found", "That page doesn't exist."))
    }

    private fun handleSubmit(session: IHTTPSession): Response {
        val body = mutableMapOf<String, String>()
        runCatching { session.parseBody(body) }
        val submittedPin = session.parameters["pin"]?.firstOrNull().orEmpty().trim()
        val token = session.parameters["token"]?.firstOrNull().orEmpty().trim()

        if (submittedPin != pin) {
            return html(
                Response.Status.FORBIDDEN,
                page("Wrong PIN", "That PIN doesn't match what's shown on the Kompakt's screen. Go back and try again."),
            )
        }
        if (token.isBlank()) {
            return html(
                Response.Status.BAD_REQUEST,
                page("Empty token", "Paste the refresh_token value before submitting."),
            )
        }
        onTokenReceived(token)
        return html(
            Response.Status.OK,
            page("Done", "Sent. You can close this page and check the Kompakt."),
        )
    }

    private fun html(status: Response.Status, body: String) =
        newFixedLengthResponse(status, "text/html; charset=utf-8", body)

    companion object {
        // Not a real secret - see the class doc's HTTPS section.
        private val KEYSTORE_PASSWORD = "sensieink-pairing-nonsecret".toCharArray()

        private fun loadSslSocketFactory(appContext: Context): SSLSocketFactory {
            val keyStore = KeyStore.getInstance("PKCS12")
            appContext.resources.openRawResource(R.raw.pairing_keystore).use {
                keyStore.load(it, KEYSTORE_PASSWORD)
            }
            val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            keyManagerFactory.init(keyStore, KEYSTORE_PASSWORD)
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(keyManagerFactory.keyManagers, null, null)
            return sslContext.socketFactory
        }

        /**
         * This device's WiFi LAN IPv4 address, for display - null if WiFi
         * isn't connected. Deliberately reads only `wlan0` (the Kompakt's
         * WiFi station interface - confirmed via `adb shell ip addr` on the
         * physical unit, serial MK20250414220), not "any non-loopback IPv4
         * address on any interface": this device can simultaneously have
         * cellular data up (`ccmni*`) and its own WiFi hotspot active
         * (`ap0`, its own private subnet), both of which carry a real
         * non-loopback IPv4 address too. The original any-interface version
         * picked up the hotspot's address on real hardware and showed it as
         * the pairing URL - unreachable from a computer on the user's
         * actual WiFi, since that address only exists inside the Kompakt's
         * own hotspot subnet. Falling through to some other interface here
         * would reproduce the same bug, so this returns null instead
         * (correctly triggering [SetupScreen]'s manual-paste fallback)
         * whenever `wlan0` itself has no address, i.e. WiFi isn't connected.
         */
        fun localIpAddress(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .firstOrNull { it.name == "wlan0" }
                ?.inetAddresses?.asSequence()
                ?.firstOrNull { addr -> !addr.isLoopbackAddress && addr.hostAddress?.contains(':') == false }
                ?.hostAddress
        }.getOrNull()

        private fun page(title: String, body: String) = """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>$title</title>
            <style>body{font-family:sans-serif;max-width:420px;margin:40px auto;padding:0 16px}</style>
            </head><body><h1>$title</h1><p>$body</p></body></html>
        """.trimIndent()

        // Rewritten 2026-09-18 for comprehension, revised twice more same
        // day: first to name the website and say when to go there as its
        // own step instead of a clause buried in the DevTools step, and to
        // say what step 3 *will* happen rather than what it "may" (per
        // sensi-client-spec.md section 7, the paywall screen is confirmed,
        // not occasional, for a non-subscriber account); then again once
        // HTTPS existed as a fallback, to explain the "not private" warning
        // a user might land here through instead of leaving it unexplained.
        private val PAGE = """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Connect to Sensi</title>
            <style>
                body{font-family:sans-serif;max-width:480px;margin:40px auto;padding:0 16px;line-height:1.5}
                input{width:100%;box-sizing:border-box;padding:10px;font-size:16px;margin:6px 0 16px}
                button{width:100%;padding:12px;font-size:16px}
                h2{font-size:17px;margin:26px 0 6px}
                code{background:#eee;padding:1px 5px;border-radius:3px}
                .note{background:#f3f3f3;border-left:4px solid #999;padding:10px 14px;margin:10px 0;font-size:15px}
                hr{margin:28px 0;border:none;border-top:1px solid #ccc}
            </style>
            </head><body>
            <h1>Connect to Sensi</h1>
            <div class="note">Got here through a browser warning like "your
            connection is not private"? That's expected - this page is
            served from the Kompakt itself (the e-ink device, not the wall
            thermostat), which can't have a browser-trusted address. It's
            still the right page; the PIN below is what actually confirms
            you're looking at the right device, not the browser
            padlock.</div>
            <p>This thermostat app can't log in with your Sensi username and
            password directly - Sensi blocks that kind of automated login.
            Logging in yourself below, in this browser, works completely
            normally.</p>
            <p>What you're actually doing on this page: log in to
            <code>manager.sensicomfort.com</code>, then copy a one-time code
            it hands your browser (called a <code>refresh_token</code>) and
            paste it into the form at the bottom. Four steps, all on this
            computer.</p>

            <h2>1. Open Developer Tools and get ready to capture</h2>
            <p>This is a panel built into every browser - nothing to
            install. In Chrome, Edge, or Firefox, press <code>F12</code>
            (Windows/Linux) or <code>Cmd+Option+I</code> (Mac). A panel
            opens along one edge of the window; click its <b>Network</b>
            tab, then type <code>token</code> into its filter/search box.
            Do this now, before the next step, so it's already capturing
            traffic when you log in.</p>
            <div class="note"><b>On Safari:</b> the same shortcut only
            works after you turn it on once. Safari &gt; Settings &gt;
            Advanced &gt; check "Show features for web developers" (older
            versions: "Show Develop menu in menu bar"). Then
            <code>Cmd+Option+I</code> opens it like any other browser.</div>

            <h2>2. Go to manager.sensicomfort.com and log in</h2>
            <p>In the main browser window (not the DevTools panel), go to
            <code>manager.sensicomfort.com</code> and log in with the same
            email and password you use in the Sensi mobile app.</p>

            <h2>3. You'll land on a subscription page - ignore it</h2>
            <div class="note">After logging in, you'll see a page asking
            for $1.50/month per thermostat. That's a separate paid product
            (Sensi Manager) - you don't need it and don't need to
            subscribe. Your token was already captured during the login
            itself, before this screen loads.</div>

            <h2>4. Copy your token</h2>
            <p>Back in the Network tab, you should see one or two requests
            named <code>token?device=...</code>. Click the one whose
            <b>Response</b> panel shows some text (the other may be empty).
            In that response, find <code>refresh_token</code> and copy the
            long value next to it - it starts with <code>eyJ</code>.</p>

            <hr>
            <p>Enter the PIN shown on the Kompakt's screen, paste the
            token you just copied, then press Send.</p>
            <form method="post" action="/submit">
                <label for="pin">PIN (from the Kompakt's screen)</label>
                <input id="pin" name="pin" inputmode="numeric" autocomplete="off" required>
                <label for="token">refresh_token</label>
                <input id="token" name="token" type="password" autocomplete="off" required>
                <button type="submit">Send</button>
            </form>
            </body></html>
        """.trimIndent()
    }
}

/** Installs [DualProtocolServerSocket] as NanoHTTPD's server socket - see [PairingServer]'s doc. */
private class DualProtocolServerSocketFactory(
    private val sslSocketFactory: SSLSocketFactory,
) : NanoHTTPD.ServerSocketFactory {
    override fun create(): ServerSocket = DualProtocolServerSocket(sslSocketFactory)
}

/**
 * A [ServerSocket] whose [accept] sniffs each new connection's first byte
 * to tell a TLS ClientHello from a plain HTTP request line, then wraps or
 * passes through accordingly - see [PairingServer]'s doc for why both
 * protocols have to share one port here rather than living on two.
 *
 * Peeking uses [BufferedInputStream]'s own `mark()`/`reset()`, not a
 * raw-socket trick: Android's `SSLSocketFactory` is missing the JDK's
 * `createSocket(Socket, InputStream, boolean)` convenience overload that
 * would otherwise let an already-consumed byte be replayed directly into a
 * new SSL wrapper (confirmed by compiling against it - the overload simply
 * isn't there on Android). `mark(1)` + `read()` + `reset()` sidesteps that
 * entirely: nothing is actually consumed from [PeekedSocket]'s point of
 * view, so the *same* wrapped stream can be hand to either NanoHTTPD
 * directly (plain HTTP) or into [SSLSocketFactory.createSocket]'s
 * Socket-wrapping overload (TLS) without needing to reconstruct anything.
 *
 * NanoHTTPD itself calls exactly six methods on an accepted client
 * [Socket] (confirmed by disassembling `nanohttpd:2.3.1`'s
 * `ServerRunnable`/`ClientHandler` classes, not guessed): `setSoTimeout`,
 * `getInputStream`, `getOutputStream`, `getInetAddress`, `isClosed`,
 * `close`. [PeekedSocket] covers those plus the socket-state queries
 * (`isConnected`, `getPort`, etc.) that the TLS-wrapping overload may
 * reasonably check before layering itself over an existing connection.
 */
private class DualProtocolServerSocket(
    private val sslSocketFactory: SSLSocketFactory,
) : ServerSocket() {
    override fun accept(): Socket {
        val raw = super.accept()

        // NanoHTTPD only applies its own read timeout *after* accept()
        // returns (see the class doc) - without setting one here first, a
        // connection that opens but never sends a byte would block this
        // read forever, stalling the entire accept loop for every other
        // pairing attempt behind it. If even this much fails, raw's stream
        // has not been touched at all yet, so it's still safe to hand back
        // directly.
        val bufferedInput = runCatching {
            raw.soTimeout = PEEK_TIMEOUT_MS
            BufferedInputStream(raw.getInputStream())
        }.getOrNull() ?: return raw

        // From here on, bufferedInput - not raw's own stream - is the only
        // correct view of what's left to read: BufferedInputStream reads
        // in chunks under the hood, so any byte beyond the one peeked may
        // already have been pulled out of the real socket stream and be
        // sitting in this buffer, unreachable via raw.getInputStream()
        // again. Every fallback below returns a Socket exposing
        // bufferedInput, never bare raw, to avoid silently losing that
        // data.
        val peeked = PeekedSocket(raw, bufferedInput)
        val firstByte = runCatching {
            bufferedInput.mark(1)
            bufferedInput.read()
        }.getOrDefault(-1)
        if (firstByte == -1) return peeked

        runCatching { bufferedInput.reset() }

        if (firstByte != TLS_HANDSHAKE_CONTENT_TYPE) return peeked

        return runCatching {
            (
                sslSocketFactory.createSocket(peeked, raw.inetAddress?.hostAddress, raw.port, true)
                    as SSLSocket
                ).apply { useClientMode = false }
            // If SSL wrapping itself fails, falling back to the plain
            // peeked socket is the best remaining option - not protocol
            // correct (the client sent a ClientHello, not an HTTP
            // request), but it lets that one connection fail cleanly on
            // the client's end instead of taking down the accept loop for
            // every pairing attempt after it.
        }.getOrDefault(peeked)
    }

    private companion object {
        // TLS record layer ContentType for a Handshake record, which every
        // ClientHello arrives wrapped in - always this exact byte per RFC
        // 8446 section 5.1. No HTTP method name starts with it.
        const val TLS_HANDSHAKE_CONTENT_TYPE = 0x16
        const val PEEK_TIMEOUT_MS = 5_000
    }
}

/**
 * Delegates every call to [delegate], except [getInputStream] which
 * returns the resettable [bufferedInput] instead of asking [delegate] for
 * a fresh (non-peekable) stream. See [DualProtocolServerSocket]'s doc for
 * why this exists and why it needs more than the bare minimum NanoHTTPD
 * itself calls.
 */
private class PeekedSocket(
    private val delegate: Socket,
    private val bufferedInput: InputStream,
) : Socket() {
    override fun getInputStream(): InputStream = bufferedInput
    override fun getOutputStream(): OutputStream = delegate.getOutputStream()
    override fun getInetAddress(): InetAddress? = delegate.inetAddress
    override fun isClosed(): Boolean = delegate.isClosed
    override fun close() = delegate.close()
    override fun setSoTimeout(timeout: Int) = delegate.setSoTimeout(timeout)
    override fun isConnected(): Boolean = delegate.isConnected
    override fun isBound(): Boolean = delegate.isBound
    override fun getPort(): Int = delegate.port
    override fun getLocalPort(): Int = delegate.localPort
    override fun getLocalAddress(): InetAddress? = delegate.localAddress
    override fun getRemoteSocketAddress(): SocketAddress? = delegate.remoteSocketAddress
    override fun getLocalSocketAddress(): SocketAddress? = delegate.localSocketAddress
}
