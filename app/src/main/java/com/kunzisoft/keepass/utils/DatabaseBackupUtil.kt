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
package com.kunzisoft.keepass.utils

import android.content.Context
import com.kunzisoft.keepass.database.file.DatabaseBackupManager
import java.io.File

object DatabaseBackupUtil {

    private const val BACKUP_DIRECTORY = "database_backups"

    /**
     * Backups live in the no-backup app directory: private to the app
     * and never sent to the Android cloud backup.
     */
    fun Context.databaseBackupManager(): DatabaseBackupManager {
        return DatabaseBackupManager(File(noBackupFilesDir, BACKUP_DIRECTORY))
    }
}
