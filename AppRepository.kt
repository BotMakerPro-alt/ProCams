package com.procam.camera.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.procam.camera.database.HiddenApp
import com.procam.camera.database.ProCamDatabase
import kotlinx.coroutines.flow.Flow

data class LaunchableApp(val label: String, val packageName: String)
class AppRepository(private val context: Context) {
    private val db by lazy { ProCamDatabase.get(context) }
    fun observeSelected(): Flow<List<HiddenApp>> = db.hiddenAppDao().observeAll()
    suspend fun add(packageName: String) { db.hiddenAppDao().insert(HiddenApp(packageName)) }
    suspend fun remove(packageName: String) { db.hiddenAppDao().delete(packageName) }
    suspend fun rename(packageName: String, name: String?) { db.hiddenAppDao().rename(packageName, name) }
    suspend fun move(packageName: String, folderId: String?) { db.hiddenAppDao().move(packageName, folderId) }
    suspend fun createFolder(name: String) { db.appFolderDao().insert(com.procam.camera.database.AppFolder(java.util.UUID.randomUUID().toString(), name)) }
    fun installedLaunchableApps(): List<LaunchableApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val results = if (Build.VERSION.SDK_INT >= 33) pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0)) else pm.queryIntentActivities(intent, 0)
        return results.mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == context.packageName) null else LaunchableApp(info.loadLabel(pm).toString(), pkg)
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }
    fun launch(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { context.startActivity(intent); true } catch (_: Exception) { false }
    }
}
