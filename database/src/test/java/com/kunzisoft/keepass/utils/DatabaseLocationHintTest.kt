package com.kunzisoft.keepass.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseLocationHintTest {

    @Test
    fun parentFolderFromExternalStorageDocument() {
        assertEquals("Documents/Work", DatabaseLocationHint.parentFolder(
            "content://com.android.externalstorage.documents/document/primary:Documents/Work/vault.kdbx"))
    }

    @Test
    fun parentFolderFromTreeDocument() {
        assertEquals("Passwords", DatabaseLocationHint.parentFolder(
            "content://com.android.externalstorage.documents/tree/primary:Passwords/document/primary:Passwords/vault.kdbx"))
    }

    @Test
    fun parentFolderKeepsLastTwoFolders() {
        assertEquals("0/Download", DatabaseLocationHint.parentFolder(
            "file:///storage/emulated/0/Download/vault.kdbx"))
    }

    @Test
    fun parentFolderUnknownForOpaqueIds() {
        assertNull(DatabaseLocationHint.parentFolder(
            "content://com.google.android.apps.docs.storage/document/acc=1;doc=encoded=abc123"))
        assertNull(DatabaseLocationHint.parentFolder(null))
        assertNull(DatabaseLocationHint.parentFolder(""))
    }

    @Test
    fun describeCombinesFolderAndDate() {
        assertEquals("Documents · 9 Nov 2026", DatabaseLocationHint.describe(
            "content://com.android.externalstorage.documents/document/primary:Documents/vault.kdbx",
            "9 Nov 2026"))
    }

    @Test
    fun describeFallsBackToPath() {
        val opaque = "content://com.google.android.apps.docs.storage/document/acc=1;doc=encoded=abc"
        assertEquals("9 Nov 2026", DatabaseLocationHint.describe(opaque, "9 Nov 2026"))
        assertEquals(opaque, DatabaseLocationHint.describe(opaque, null))
    }

    @Test
    fun sharedNameIgnoresCaseAndSpaces() {
        val aliases = listOf("vault.kdbx", "Vault.kdbx ", "other.kdbx", null)
        assertTrue(DatabaseLocationHint.isNameShared("vault.kdbx", aliases))
        assertFalse(DatabaseLocationHint.isNameShared("other.kdbx", aliases))
        assertFalse(DatabaseLocationHint.isNameShared(null, aliases))
        assertFalse(DatabaseLocationHint.isNameShared("", listOf("", "")))
    }
}
