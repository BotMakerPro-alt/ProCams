package com.procam.camera.vault

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.procam.camera.database.*
import com.procam.camera.security.EncryptionManager
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID

class VaultRepository(context: Context) {
    private val app = context.applicationContext
    private val resolver: ContentResolver = app.contentResolver
    private val db = ProCamDatabase.get(app)
    private val dao = db.vaultMediaDao()
    private val albums = db.privateAlbumDao()
    private val crypto = EncryptionManager()
    private val root = File(app.filesDir, "private_vault").apply { mkdirs() }

    fun media(): Flow<List<VaultMedia>> = dao.observeAll()
    fun trash(): Flow<List<VaultMedia>> = dao.observeTrash()
    fun albums(): Flow<List<PrivateAlbum>> = albums.observeAll()
    fun usedBytes(): Flow<Long> = dao.observeUsedBytes()

    suspend fun importMedia(uri: Uri, displayName: String, mimeType: String, albumId: String? = null): VaultMedia {
        val id = UUID.randomUUID().toString()
        val encrypted = File(root, "$id.pcv")
        val input = resolver.openInputStream(uri) ?: error("Cannot read selected media")
        try { crypto.encrypt(input, encrypted) } catch (e: Exception) { encrypted.delete(); throw e }
        val item = VaultMedia(id, encrypted.absolutePath, displayName, mimeType, albumId, false, System.currentTimeMillis(), encrypted.length())
        dao.insert(item)
        return item
    }

    suspend fun restoreToTemp(id: String): File {
        val item = dao.get(id) ?: error("Media not found")
        require(!item.deleted) { "Restore this item from Trash first" }
        val temp = File(app.cacheDir, "vault_export_${item.id}_${item.displayName.safeName()}")
        crypto.decrypt(File(item.encryptedPath), temp)
        return temp
    }

    suspend fun export(id: String, destination: Uri) {
        val item = dao.get(id) ?: error("Media not found")
        require(!item.deleted) { "Restore this item from Trash first" }
        val temp = File(app.cacheDir, "vault_export_${item.id}")
        try {
            crypto.decrypt(File(item.encryptedPath), temp)
            resolver.openOutputStream(destination, "w")?.use { out -> temp.inputStream().use { it.copyTo(out) } } ?: error("Cannot write destination")
        } finally { temp.delete() }
    }

    suspend fun rename(id: String, name: String) {
        require(name.isNotBlank() && name.length <= 120)
        val item = dao.get(id) ?: error("Media not found")
        dao.update(item.copy(displayName = name.trim()))
    }
    suspend fun moveToTrash(id: String) { dao.get(id)?.let { dao.update(it.copy(deleted = true)) } }
    suspend fun restore(id: String) { dao.get(id)?.let { dao.update(it.copy(deleted = false)) } }
    suspend fun permanentlyDelete(id: String) {
        val item = dao.get(id) ?: return
        // Best-effort overwrite; flash storage/controllers may retain physical copies.
        val file = File(item.encryptedPath)
        runCatching { file.outputStream().use { out -> val zeros = ByteArray(8192); var remaining = file.length(); while (remaining > 0) { val n = minOf(zeros.size.toLong(), remaining).toInt(); out.write(zeros, 0, n); remaining -= n } } }
        file.delete(); dao.delete(id)
    }
    suspend fun createAlbum(name: String) { require(name.isNotBlank()); albums.insert(PrivateAlbum(UUID.randomUUID().toString(), name.trim())) }
    suspend fun deleteAlbum(id: String) = albums.delete(id)
    fun storageRoot() = root
    private fun String.safeName() = replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
}
