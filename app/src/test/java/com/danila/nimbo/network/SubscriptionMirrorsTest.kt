package com.danila.nimbo.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionMirrorsTest {

    private val primary = "https://sub.example.com/api/sub/abc123?format=json"

    @Test
    fun parsesCommaAndSpaceSeparatedEntries() {
        val parsed = SubscriptionMirrors.parse("sub2.example.com, sub3.example.org:8443 https://backup.example.net")
        assertEquals(listOf("sub2.example.com", "sub3.example.org:8443", "https://backup.example.net"), parsed)
    }

    @Test
    fun parsesNewlineSeparatedHeaderAndDropsDuplicates() {
        val parsed = SubscriptionMirrors.parse("sub2.example.com\nsub2.example.com\n\nsub4.example.com")
        assertEquals(listOf("sub2.example.com", "sub4.example.com"), parsed)
    }

    @Test
    fun ignoresGarbageEntries() {
        val parsed = SubscriptionMirrors.parse("not a host, ftp://mirror.example.com, localhost, ok.example.com")
        assertEquals(listOf("ok.example.com"), parsed)
    }

    @Test
    fun emptyHeaderGivesEmptyList() {
        assertTrue(SubscriptionMirrors.parse(null).isEmpty())
        assertTrue(SubscriptionMirrors.parse("   ").isEmpty())
    }

    @Test
    fun limitsMirrorCount() {
        val many = (1..20).joinToString(",") { "sub$it.example.com" }
        assertEquals(SubscriptionMirrors.MAX_MIRRORS, SubscriptionMirrors.parse(many).size)
    }

    @Test
    fun bareHostKeepsPathAndQuery() {
        assertEquals(
            "https://sub2.example.com/api/sub/abc123?format=json",
            SubscriptionMirrors.rewrite(primary, "sub2.example.com")
        )
    }

    @Test
    fun hostWithPortKeepsPathAndQuery() {
        assertEquals(
            "https://sub3.example.org:8443/api/sub/abc123?format=json",
            SubscriptionMirrors.rewrite(primary, "sub3.example.org:8443")
        )
    }

    @Test
    fun mirrorWithOwnPathReplacesPathAndInheritsQuery() {
        assertEquals(
            "https://backup.example.net/s/abc123?format=json",
            SubscriptionMirrors.rewrite(primary, "https://backup.example.net/s/abc123")
        )
    }

    @Test
    fun mirrorSchemeWins() {
        assertEquals(
            "http://plain.example.com/api/sub/abc123?format=json",
            SubscriptionMirrors.rewrite(primary, "http://plain.example.com")
        )
    }

    @Test
    fun brokenMirrorGivesNull() {
        assertNull(SubscriptionMirrors.rewrite(primary, "://"))
    }

    @Test
    fun candidatesStartWithPrimaryWhenNothingWorkedBefore() {
        val candidates = SubscriptionMirrors.candidates(
            primaryUrl = primary,
            mirrors = listOf("sub2.example.com", "sub3.example.com")
        )
        assertEquals(
            listOf(
                primary,
                "https://sub2.example.com/api/sub/abc123?format=json",
                "https://sub3.example.com/api/sub/abc123?format=json"
            ),
            candidates
        )
    }

    @Test
    fun lastWorkingMirrorGoesFirst() {
        val working = "https://sub3.example.com/api/sub/abc123?format=json"
        val candidates = SubscriptionMirrors.candidates(
            primaryUrl = primary,
            mirrors = listOf("sub2.example.com", "sub3.example.com"),
            preferredUrl = working
        )
        assertEquals(working, candidates.first())
        assertEquals(primary, candidates[1])
        assertEquals(3, candidates.size)
    }

    @Test
    fun unknownPreferredUrlIsIgnored() {
        val candidates = SubscriptionMirrors.candidates(
            primaryUrl = primary,
            mirrors = listOf("sub2.example.com"),
            preferredUrl = "https://stranger.example.com/api/sub/abc123"
        )
        assertEquals(primary, candidates.first())
        assertEquals(2, candidates.size)
    }

    @Test
    fun mirrorEqualToPrimaryIsNotDuplicated() {
        val candidates = SubscriptionMirrors.candidates(
            primaryUrl = primary,
            mirrors = listOf("sub.example.com", "sub2.example.com")
        )
        assertEquals(2, candidates.size)
        assertEquals(primary, candidates.first())
    }

    @Test
    fun extractsMirrorsFromLinkAndCleansUrl() {
        val link = SubscriptionMirrors.extractFromUrl(
            "https://sub.example.com/sub/abc123?format=json&mirrors=sub2.example.com,sub3.example.net"
        )
        assertEquals("https://sub.example.com/sub/abc123?format=json", link.url)
        assertEquals(listOf("sub2.example.com", "sub3.example.net"), link.mirrors)
    }

    @Test
    fun dropsQueryEntirelyWhenOnlyMirrorsWereThere() {
        val link = SubscriptionMirrors.extractFromUrl("https://sub.example.com/sub/abc123?mirrors=sub2.example.com")
        assertEquals("https://sub.example.com/sub/abc123", link.url)
        assertEquals(listOf("sub2.example.com"), link.mirrors)
    }

    @Test
    fun acceptsUrlEncodedMirrorList() {
        val link = SubscriptionMirrors.extractFromUrl(
            "https://sub.example.com/sub/abc123?nimbo-mirrors=sub2.example.com%2Csub3.example.net"
        )
        assertEquals(listOf("sub2.example.com", "sub3.example.net"), link.mirrors)
    }

    @Test
    fun linkWithoutMirrorsIsUntouched() {
        val link = SubscriptionMirrors.extractFromUrl(primary)
        assertEquals(primary, link.url)
        assertTrue(link.mirrors.isEmpty())
    }

    @Test
    fun mergeKeepsOrderAndDropsDuplicates() {
        val merged = SubscriptionMirrors.merge(
            known = listOf("sub2.example.com"),
            added = listOf("SUB2.example.com", "sub3.example.com")
        )
        assertEquals(listOf("sub2.example.com", "sub3.example.com"), merged)
    }

    @Test
    fun hostOfExtractsDomain() {
        assertEquals("sub.example.com", SubscriptionMirrors.hostOf(primary))
        assertNull(SubscriptionMirrors.hostOf("not a url"))
    }
}
