package com.kunzisoft.keepass.database.file

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File

class DatabaseBackupManagerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var root: File
    private lateinit var manager: DatabaseBackupManager

    @Before
    fun setUp() {
        root = temporaryFolder.newFolder("backups")
        manager = DatabaseBackupManager(root, maxBackups = 3)
    }

    private fun backup(location: String, content: ByteArray): File? {
        // Unique timestamps
        Thread.sleep(2)
        return manager.createBackup(location, "vault.kdbx", content.inputStream())
    }

    @Test
    fun backupKeepsExactContent() {
        val content = ByteArray(100_000) { (it % 251).toByte() }
        val file = backup("content://db/1", content)!!
        assertArrayEquals(content, file.readBytes())
        val output = ByteArrayOutputStream()
        manager.copyBackupTo(file, output)
        assertArrayEquals(content, output.toByteArray())
    }

    @Test
    fun emptyStreamIsNotKept() {
        assertNull(backup("content://db/1", ByteArray(0)))
        assertEquals(0, manager.listBackups().size)
    }

    @Test
    fun onlyLastBackupsAreKept() {
        for (i in 1..5) {
            backup("content://db/1", byteArrayOf(i.toByte()))
        }
        val backups = manager.listBackups()
        assertEquals(3, backups.size)
        // Newest first
        assertArrayEquals(byteArrayOf(5), backups[0].file.readBytes())
        assertArrayEquals(byteArrayOf(3), backups[2].file.readBytes())
        assertArrayEquals(byteArrayOf(5), manager.latestBackup("content://db/1")!!.readBytes())
        assertEquals("vault.kdbx", backups[0].databaseName)
    }

    @Test
    fun databasesAreSeparated() {
        for (i in 1..4) {
            backup("content://db/1", byteArrayOf(i.toByte()))
        }
        backup("content://db/2", byteArrayOf(42))
        assertEquals(4, manager.listBackups().size)
        assertArrayEquals(byteArrayOf(42), manager.latestBackup("content://db/2")!!.readBytes())
        assertArrayEquals(byteArrayOf(4), manager.latestBackup("content://db/1")!!.readBytes())
        assertNull(manager.latestBackup("content://db/3"))
    }

    @Test
    fun sha256MatchesForSameContent() {
        val file = temporaryFolder.newFile()
        file.writeBytes(byteArrayOf(1, 2, 3))
        assertArrayEquals(
            DatabaseBackupManager.sha256(file),
            DatabaseBackupManager.sha256(byteArrayOf(1, 2, 3).inputStream())
        )
    }
}
