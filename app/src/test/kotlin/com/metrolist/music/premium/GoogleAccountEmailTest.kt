package com.metrolist.music.premium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleAccountEmailTest {
    @Test
    fun readsPrimaryAddressFromListAccounts() {
        val body = """["gaia.l.a.r",[["gaia.l.a",1,"","Owner@Gmail.com","",1,1,0]]]"""

        assertEquals(listOf("owner@gmail.com"), parseListAccountsEmails(body))
    }

    @Test
    fun keepsAccountOrderAndSkipsSignedOutOnes() {
        val body =
            """["gaia.l.a.r",[""" +
                listOf(
                    entry("first@gmail.com"),
                    entry("signedout@gmail.com", signedOut = true),
                    entry("expired@gmail.com", sessionValid = 0),
                    entry("second@googlemail.com"),
                ).joinToString(",") +
                """]]"""

        assertEquals(
            listOf("first@gmail.com", "second@googlemail.com"),
            parseListAccountsEmails(body),
        )
    }

    /** GAIA account entry with every field the real answer carries (email=3, valid=9, out=14). */
    private fun entry(
        email: String,
        sessionValid: Int = 1,
        signedOut: Boolean = false,
    ): String =
        """["gaia.l.a",1,"","$email","",1,1,0,0,""" +
            """$sessionValid,"gaiaid",0,0,0,${if (signedOut) 1 else 0}]"""

    @Test
    fun toleratesXssiPrefixAndHtmlWrapper() {
        val body = ")]}'\n<html><body>[&quot;gaia.l.a.r&quot;," +
            "[&quot;gaia.l.a&quot;,1,&quot;&quot;,&quot;brand@gmail.com&quot;]]</body></html>"

        assertEquals(listOf("brand@gmail.com"), parseListAccountsEmails(body))
    }

    @Test
    fun dropsGoogleRobotAccounts() {
        val body =
            """["gaia.l.a.r",[""" +
                """["gaia.l.a",1,"","robot@google.com","",1,1,0],""" +
                """["gaia.l.a",1,"","svc@project.gserviceaccount.com","",1,1,0],""" +
                """["gaia.l.a",1,"","real@gmail.com","",1,1,0]""" +
                """]]"""

        assertEquals(listOf("real@gmail.com"), parseListAccountsEmails(body))
    }

    @Test
    fun emptyOrUnreadableBodyYieldsNoEmail() {
        assertTrue(parseListAccountsEmails("").isEmpty())
        assertTrue(parseListAccountsEmails("""{"error":"400"}""").isEmpty())
        assertTrue(parseListAccountsEmails("not an email at all").isEmpty())
    }

    @Test
    fun deduplicatesRepeatedAddresses() {
        val body =
            """["gaia.l.a.r",[""" +
                """["gaia.l.a",1,"","same@gmail.com","",1,1,0],""" +
                """["gaia.l.a",1,"","SAME@gmail.com","",1,1,0]""" +
                """]]"""

        assertEquals(listOf("same@gmail.com"), parseListAccountsEmails(body))
    }

    @Test
    fun decodesBase64ProtobufAnswer() {
        // size + tag bytes then the raw address, the way GAIA packs display_email.
        val proto = byteArrayOf(0x0A, 0x0D) + "owner@gmail.com".toByteArray()
        val body = java.util.Base64.getEncoder().encodeToString(proto)

        assertEquals(listOf("owner@gmail.com"), parseListAccountsEmails(body))
    }
}