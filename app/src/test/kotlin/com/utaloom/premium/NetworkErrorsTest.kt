package com.utaloom.premium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.http.HttpConnectTimeoutException
import javax.net.ssl.SSLHandshakeException

/**
 * "HTTP connect timed out" (the raw JDK message a friend saw) is useless on its own, so each
 * transport failure has to be translated into something the user can act on — and only the
 * failures that can plausibly recover on a second try may be retried.
 */
class NetworkErrorsTest {
    private val server = "https://premium.adminbeat.my.id"

    @Test
    fun connectTimeoutNamesTheHostAndHints() {
        val message = networkErrorMessage(HttpConnectTimeoutException("HTTP connect timed out"), server)
        assertTrue(message.contains("premium.adminbeat.my.id"))
        assertTrue("should suggest firewall/VPN", message.contains("firewall") || message.contains("VPN"))
        assertTrue(isTransientNetworkError(HttpConnectTimeoutException("HTTP connect timed out")))
    }

    @Test
    fun socketTimeoutIsRetried() {
        assertTrue(isTransientNetworkError(SocketTimeoutException("Read timed out")))
        assertTrue(networkErrorMessage(SocketTimeoutException("Read timed out"), server).contains("habis waktu"))
    }

    @Test
    fun dnsFailureTellsTheUserToCheckTheInternet() {
        val message = networkErrorMessage(UnknownHostException("premium.adminbeat.my.id"), server)
        assertTrue(message.contains("Tidak bisa menemukan"))
        assertTrue(isTransientNetworkError(UnknownHostException("premium.adminbeat.my.id")))
    }

    @Test
    fun refusedConnectionSuggestsAnotherNetwork() {
        val message = networkErrorMessage(ConnectException("Connection refused"), server)
        assertTrue(message.contains("hotspot") || message.contains("jaringan lain"))
        assertTrue(isTransientNetworkError(ConnectException("Connection refused")))
    }

    @Test
    fun tlsHandshakeFailureBlamesTheClockAndIsNotRetried() {
        val message = networkErrorMessage(SSLHandshakeException("PKIX path building failed"), server)
        assertTrue(message.contains("tanggal"))
        assertFalse(
            "a broken certificate chain will not fix itself",
            isTransientNetworkError(SSLHandshakeException("PKIX path building failed")),
        )
    }

    @Test
    fun expiredSessionKeepsTheServerMessageAndIsNotRetried() {
        val error = SessionExpiredException("login diperlukan atau sertakan email di body")
        assertEquals(error.message, networkErrorMessage(error, server))
        assertFalse(isTransientNetworkError(error))
    }

    @Test
    fun apiErrorsAreShownAsTheyAre() {
        val error = IllegalStateException("Email sudah terdaftar")
        assertEquals("Email sudah terdaftar", networkErrorMessage(error, server))
        assertFalse(isTransientNetworkError(error))
    }

    @Test
    fun missingServerUrlStillProducesAReadableMessage() {
        val message = networkErrorMessage(SocketTimeoutException(), "")
        assertTrue(message.contains("server BeatPlay"))
    }
}
