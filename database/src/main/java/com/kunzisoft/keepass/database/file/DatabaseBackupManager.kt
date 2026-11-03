/*
 * Copyright 2026 Best Passafe.
 *
 * This file is part of KeePassDX.
 *
 *  KeePassDX is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  KeePassDX is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with KeePassDX.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.kunzisoft.keepass.database.file

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * Keeps the last [maxBackups] versions of each database file, as they were on disk
 * just before the app overwrote them. The copies are the encrypted database files
 * themselves, so they open with the master key that was valid at that time.
 *
 * Layout: <root>/<sha256(databaseLocation)>/<timestamp>.bak and a "name" file
 * holding the database display name.
 */
class DatabaseBackupManager(
    private val rootDirectory: File,
    private val maxBackups: Int = DEFAULT_MAX_BACKUPS
) {

    data class Backup(
        val file: File,
        val databaseName: String,
        val timestamp: Long
    )

    private fun databaseDirectory(databaseLocation: String): File {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(databaseLocation.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .substring(0, 32)
        return File(rootDirectory, hash)
    }

    /**
     * Copy [databaseStream] (the current content of the database file) into a new backup.
     * Nothing is kept if the stream is empty. Returns the backup file, or null if nothing was kept.
     */
    @Throws(IOException::class)
    fun createBackup(
        databaseLocation: String,
        databaseName: String,
        databaseStream: InputStream
    ): File? {
        val directory = databaseDirectory(databaseLocation)
        if (!directory.exists() && !directory.mkdirs())
            throw IOException("Unable to create backup directory $directory")
        val timestamp = System.currentTimeMillis()
        val temp = File(directory, "$timestamp$TEMP_EXTENSION")
        val size = try {
            temp.outputStream().use { output -> databaseStream.copyTo(output) }
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
        if (size <= 0L) {
            temp.delete()
            return null
        }
        val backup = File(directory, "$timestamp$BACKUP_EXTENSION")
        if (!temp.renameTo(backup)) {
            temp.delete()
            throw IOException("Unable to finalize backup $backup")
        }
        try {
            File(directory, NAME_FILE).writeText(databaseName)
        } catch (_: Exception) {
            // The name is only informative
        }
        prune(directory)
        return backup
    }

    /**
     * Latest backup of a database, or null if none.
     */
    fun latestBackup(databaseLocation: String): File? {
        return backupFiles(databaseDirectory(databaseLocation)).firstOrNull()
    }

    /**
     * All backups of all databases, newest first.
     */
    fun listBackups(): List<Backup> {
        val directories = rootDirectory.listFiles { file -> file.isDirectory } ?: return emptyList()
        return directories.flatMap { directory ->
            val name = try {
                File(directory, NAME_FILE).takeIf { it.exists() }?.readText()
            } catch (_: Exception) {
                null
            } ?: directory.name
            backupFiles(directory).map { file ->
                Backup(file, name, timestampOf(file))
            }
        }.sortedByDescending { it.timestamp }
    }

    @Throws(IOException::class)
    fun copyBackupTo(backup: File, outputStream: OutputStream) {
        backup.inputStream().use { input -> input.copyTo(outputStream) }
    }

    private fun backupFiles(directory: File): List<File> {
        return (directory.listFiles { file ->
            file.isFile && file.name.endsWith(BACKUP_EXTENSION)
        } ?: emptyArray()).sortedByDescending { timestampOf(it) }
    }

    private fun prune(directory: File) {
        backupFiles(directory).drop(maxBackups).forEach { it.delete() }
        // Leftovers of an interrupted backup
        directory.listFiles { file -> file.name.endsWith(TEMP_EXTENSION) }?.forEach { it.delete() }
    }

    private fun timestampOf(file: File): Long {
        return file.name.removeSuffix(BACKUP_EXTENSION).toLongOrNull() ?: file.lastModified()
    }

    companion object {
        const val DEFAULT_MAX_BACKUPS = 3
        private const val BACKUP_EXTENSION = ".bak"
        private const val TEMP_EXTENSION = ".tmp"
        private const val NAME_FILE = "name"

        fun sha256(file: File): ByteArray {
            return file.inputStream().use { sha256(it) }
        }

        fun sha256(inputStream: InputStream): ByteArray {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read = inputStream.read(buffer)
            while (read != -1) {
                digest.update(buffer, 0, read)
                read = inputStream.read(buffer)
            }
            return digest.digest()
        }
    }
}
