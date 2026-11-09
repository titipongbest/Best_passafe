package com.kunzisoft.keepass.activities.helpers

import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.kunzisoft.keepass.R
import com.kunzisoft.keepass.viewmodels.FileDatabaseInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The system "create file" screen normally makes a new empty file, or renames it
 * ("vault (1).kdbx") when the name is taken. Some file managers and cloud providers
 * instead hand back the existing file, and writing a new database there would
 * silently destroy the old one. This checks the returned file and asks first.
 */
object ExistingFileOverwriteHelper {

    private const val TAG = "ExistingFileOverwrite"

    fun FragmentActivity.confirmOverwriteIfNotEmpty(
        fileUri: Uri,
        onConfirm: () -> Unit,
        onCancel: () -> Unit = {}
    ) {
        lifecycleScope.launch {
            val existing = withContext(Dispatchers.IO) {
                try {
                    val info = FileDatabaseInfo(applicationContext, fileUri)
                    val size = info.getSize() ?: 0L
                    if (info.exists && size > 0L) {
                        info.retrieveDatabaseAlias("") ?: ""
                    } else null
                } catch (e: Exception) {
                    Log.w(TAG, "Unable to check if the file already has content", e)
                    null
                }
            }
            if (existing == null) {
                onConfirm()
            } else {
                AlertDialog.Builder(this@confirmOverwriteIfNotEmpty)
                    .setTitle(R.string.overwrite_existing_file_title)
                    .setMessage(getString(R.string.overwrite_existing_file_message, existing))
                    .setPositiveButton(R.string.overwrite_existing_file_confirm) { _, _ -> onConfirm() }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> onCancel() }
                    .setOnCancelListener { onCancel() }
                    .show()
            }
        }
    }
}
