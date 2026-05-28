package com.vkbot.manager.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/**
 * Помощник для работы с разрешениями.
 * Оптимизирован под minSdk 30 (Android 11+).
 */
object PermissionHelper {
    
    const val REQUEST_CODE_STORAGE = 1001
    const val REQUEST_CODE_MANAGE_STORAGE = 1002
    
    /**
     * Проверяет наличие разрешений на доступ к хранилищу.
     * На Android 11+ используется MANAGE_EXTERNAL_STORAGE.
     */
    fun hasStoragePermissions(context: Context): Boolean {
        // Так как minSdk 30, проверка версии не требуется
        return Environment.isExternalStorageManager()
    }
    
    /**
     * Запрашивает разрешения на доступ к хранилищу.
     */
    fun requestStoragePermissions(activity: Activity) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = "package:${activity.packageName}".toUri()
            }
            activity.startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE)
        } catch (e: Exception) {
            try {
                activity.startActivityForResult(
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                    REQUEST_CODE_MANAGE_STORAGE
                )
            } catch (e2: Exception) {
                Toast.makeText(
                    activity,
                    "Откройте Настройки → Приложения → VK Bot Manager → Разрешения",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}