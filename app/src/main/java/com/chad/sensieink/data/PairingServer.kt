package com.chad.sensieink.data

import fi.iki.elonen.NanoHTTPD
import java.net.NetworkInterface
import java.security.SecureRandom

/**
 * A tiny local HTTP server that lets a desktop browser on the same WiFi
 * network paste the harvested refresh_token instead of typing it on the
 * Kompakt's own keyboard - see PROJECT-STATUS.md for why manual/scripted
 * on-device entry turned out to be unreliable in practice.
 *
 * Deliberately plain HTTP, not HTTPS: a LAN IP has no real domain name, so
 * the only way to get TLS is a self-signed certificate, which throws a
 * "connection not private" browser warning. [pin] substitutes for that -
 * anyone submitting to this server has to be looking at the Kompakt's own
 * screen to read it, which is the property that actually matters here. This
 * does not encrypt the token in transit; on a network you don't trust,
 * that's still a real gap, and worth knowing before relying on it.
 *
 * Only ever running while SetupScreen's paste step is on screen - started
 * and stopped by that composable, not a persistent service.
 */
class PairingServer(
    private val onTokenReceived: (String) -> Unit,
) : NanoHTTPD(0) {

    val pin: String = SecureRandom().nextInt(1_000_000).toString().padStart(6, '0')

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
                page("Wrong PIN", "That PIN doesn't match what's shown on the thermostat's screen. Go back and try again."),
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
            page("Done", "Sent. You can close this page and check the thermostat."),
        )
    }

    private fun html(status: Response.Status, body: String) =
        newFixedLengthResponse(status, "text/html; charset=utf-8", body)

    companion object {
        /** This device's own LAN IPv4 address, for display - null if there isn't one. */
        fun localIpAddress(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .flatMap { it.inetAddresses.asSequence() }
                .firstOrNull { addr -> !addr.isLoopbackAddress && addr.hostAddress?.contains(':') == false }
                ?.hostAddress
        }.getOrNull()

        private fun page(title: String, body: String) = """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>$title</title>
            <style>body{font-family:sans-serif;max-width:420px;margin:40px auto;padding:0 16px}</style>
            </head><body><h1>$title</h1><p>$body</p></body></html>
        """.trimIndent()

        private val PAGE = """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Connect to Sensi</title>
            <style>
                body{font-family:sans-serif;max-width:480px;margin:40px auto;padding:0 16px}
                input{width:100%;box-sizing:border-box;padding:10px;font-size:16px;margin:6px 0 16px}
                button{width:100%;padding:12px;font-size:16px}
                ol{padding-left:22px}
                li{margin-bottom:10px}
                code{background:#eee;padding:1px 5px;border-radius:3px}
                hr{margin:28px 0;border:none;border-top:1px solid #ccc}
            </style>
            </head><body>
            <h1>Connect to Sensi</h1>
            <p>This thermostat app can't log in with your Sensi username and
            password directly - Sensi's login page is protected by
            reCAPTCHA. Instead, grab a one-time <code>refresh_token</code>
            from your browser using the steps below, then paste it in the
            form at the bottom of this page.</p>
            <ol>
                <li>On this computer, open Chrome or Edge and go to
                <code>manager.sensicomfort.com</code>. Press F12 to open
                Developer Tools, then click its <b>Network</b> tab.</li>
                <li>Type <code>token</code> into the Network tab's filter
                box. Then log in with the same email and password you use in
                the Sensi mobile app.</li>
                <li>You may land on a screen asking for $1.50/mo per
                thermostat. That's a separate paid product (Sensi Manager) -
                you don't need it and don't need to subscribe. Your token
                was already captured by the login request in the previous
                step.</li>
                <li>In the filtered request list, find
                <code>token?device=...</code>. There may be two - use the
                one whose Response tab has content. Open it and copy the
                full <code>refresh_token</code> value (a long string
                starting with <code>eyJ</code>).</li>
            </ol>
            <hr>
            <p>Enter the PIN shown on the thermostat's screen and paste the
            refresh_token you just copied.</p>
            <form method="post" action="/submit">
                <label for="pin">PIN</label>
                <input id="pin" name="pin" inputmode="numeric" autocomplete="off" required>
                <label for="token">refresh_token</label>
                <input id="token" name="token" autocomplete="off" required>
                <button type="submit">Send</button>
            </form>
            </body></html>
        """.trimIndent()
    }
}
