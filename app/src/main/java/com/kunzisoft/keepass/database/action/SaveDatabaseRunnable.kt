/*
 * Copyright 2019 Jeremy Jamet / Kunzisoft.
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
package com.kunzisoft.keepass.database.action

import android.content.Context
import android.net.Uri
import android.util.Log
import com.kunzisoft.keepass.database.ContextualDatabase
import com.kunzisoft.keepass.database.MainCredential
import com.kunzisoft.keepass.database.element.MasterCredential
import com.kunzisoft.keepass.database.exception.DatabaseException
import com.kunzisoft.keepass.hardware.HardwareKey
import com.kunzisoft.keepass.tasks.ActionRunnable
import com.kunzisoft.keepass.tasks.ProgressTaskUpdater
import com.kunzisoft.keepass.utils.AppUtil.getLimits
import com.kunzisoft.keepass.utils.clear
import com.kunzisoft.keepass.utils.DatabaseBackupUtil.databaseBackupManager
import com.kunzisoft.keepass.utils.getUriInputStream
import com.kunzisoft.keepass.utils.getUriOutputStream
import java.io.File

open class SaveDatabaseRunnable(
    protected var context: Context,
    protected var database: ContextualDatabase,
    private var save: Boolean,
    private var mainCredential: MainCredential?, // If null, uses composite Key
    protected var challengeResponseRetriever: (HardwareKey, ByteArray?) -> ByteArray,
    private var databaseCopyUri: Uri? = null,
    private var dataModified: Boolean = !save,
    protected var progressTaskUpdater: ProgressTaskUpdater? = null
) : ActionRunnable() {

    private var mMasterCredential: MasterCredential? = null
    var afterSaveDatabase: ((Result) -> Unit)? = null

    private var cachedHardwareResponse: ByteArray? = null
    protected val cachingRetriever: (HardwareKey, ByteArray?) -> ByteArray = { hardwareKey, seed ->
        cachedHardwareResponse ?: challengeResponseRetriever(hardwareKey, seed).also {
            cachedHardwareResponse = it
        }
    }

    override fun onStartRun() {}

    override fun onActionRun() {
        database.checkVersion()
        // Save database in all cases if it's a copy
        if ((databaseCopyUri != null || save) && result.isSuccess) {
            try {
                val contentResolver = context.contentResolver
                mMasterCredential = mainCredential?.toMasterCredential(contentResolver)
                val destinationUri = databaseCopyUri ?: database.fileUri
                // Only the original database is backed up, a copy goes to a new location
                val backupLocation = if (databaseCopyUri == null) destinationUri?.toString() else null
                // Build temp database file to avoid file corruption if error
                database.saveData(
                    cacheFile = File.createTempFile("db_", ".tmp", context.cacheDir),
                    databaseOutputStream = {
                        contentResolver.getUriOutputStream(destinationUri)
                    },
                    masterCredential = mMasterCredential,
                    challengeResponseRetriever = cachingRetriever,
                    limits = context.getLimits(),
                    databaseInputStream = {
                        contentResolver.getUriInputStream(destinationUri)
                    },
                    backupCurrentDatabase = backupCurrentDatabase(backupLocation, destinationUri),
                    restoreBackup = restoreBackup(backupLocation, destinationUri)
                )
                // Indicate data was saved only if it's not a new location
                if (databaseCopyUri == null) {
                    database.indicateUpToDateData()
                }
            } catch (e: DatabaseException) {
                setError(e)
            }
        } else if (dataModified) {
            database.indicateNotSavedData()
        }
    }

    private fun backupCurrentDatabase(location: String?, uri: Uri?): (() -> Unit)? {
        if (location == null || uri == null)
            return null
        return {
            context.contentResolver.getUriInputStream(uri)?.use { input ->
                val name = database.name.takeIf { it.isNotBlank() }
                    ?: uri.lastPathSegment
                    ?: location
                context.databaseBackupManager().createBackup(location, name, input)
            }
        }
    }

    private fun restoreBackup(location: String?, uri: Uri?): (() -> Boolean)? {
        if (location == null || uri == null)
            return null
        return {
            val backupManager = context.databaseBackupManager()
            val backup = backupManager.latestBackup(location)
            if (backup == null) {
                false
            } else {
                try {
                    val outputStream = context.contentResolver.getUriOutputStream(uri)
                    outputStream?.use { output ->
                        backupManager.copyBackupTo(backup, output)
                    }
                    outputStream != null
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to restore the database backup", e)
                    false
                }
            }
        }
    }

    override fun onFinishRun() {
        // Need to call super.onFinishRun() in child class
        mMasterCredential?.clear()
        cachedHardwareResponse?.clear()
        afterSaveDatabase?.invoke(result)
    }

    companion object {
        private const val TAG = "SaveDatabaseRunnable"
    }
}
