package com.procam.camera.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "vault_media", indices = [Index("deleted"), Index("albumId")])
data class VaultMedia(
    @PrimaryKey val id: String,
    val encryptedPath: String,
    val displayName: String,
    val mimeType: String,
    val albumId: String? = null,
    val deleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val sizeBytes: Long = 0
)

@Entity(tableName = "private_albums")
data class PrivateAlbum(@PrimaryKey val id: String, val name: String, val createdAt: Long = System.currentTimeMillis())

@Entity(tableName = "hidden_apps")
data class HiddenApp(@PrimaryKey val packageName: String, val customName: String? = null, val folderId: String? = null, val addedAt: Long = System.currentTimeMillis())

@Entity(tableName = "app_folders")
data class AppFolder(@PrimaryKey val id: String, val name: String)

@Dao interface VaultMediaDao {
    @Query("SELECT * FROM vault_media WHERE deleted = 0 ORDER BY createdAt DESC") fun observeAll(): Flow<List<VaultMedia>>
    @Query("SELECT * FROM vault_media WHERE deleted = 1 ORDER BY createdAt DESC") fun observeTrash(): Flow<List<VaultMedia>>
    @Query("SELECT * FROM vault_media WHERE id = :id") suspend fun get(id: String): VaultMedia?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(item: VaultMedia)
    @Update suspend fun update(item: VaultMedia)
    @Query("DELETE FROM vault_media WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM vault_media WHERE deleted = 0") fun observeUsedBytes(): Flow<Long>
}
@Dao interface PrivateAlbumDao {
    @Query("SELECT * FROM private_albums ORDER BY name") fun observeAll(): Flow<List<PrivateAlbum>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(item: PrivateAlbum)
    @Query("DELETE FROM private_albums WHERE id = :id") suspend fun delete(id: String)
}
@Dao interface HiddenAppDao {
    @Query("SELECT * FROM hidden_apps ORDER BY addedAt DESC") fun observeAll(): Flow<List<HiddenApp>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(item: HiddenApp)
    @Query("DELETE FROM hidden_apps WHERE packageName = :packageName") suspend fun delete(packageName: String)
    @Query("UPDATE hidden_apps SET customName = :name WHERE packageName = :packageName") suspend fun rename(packageName: String, name: String?)
    @Query("UPDATE hidden_apps SET folderId = :folderId WHERE packageName = :packageName") suspend fun move(packageName: String, folderId: String?)
}
@Dao interface AppFolderDao {
    @Query("SELECT * FROM app_folders ORDER BY name") fun observeAll(): Flow<List<AppFolder>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(item: AppFolder)
    @Query("DELETE FROM app_folders WHERE id = :id") suspend fun delete(id: String)
}

@Database(entities = [VaultMedia::class, PrivateAlbum::class, HiddenApp::class, AppFolder::class], version = 2, exportSchema = false)
abstract class ProCamDatabase : RoomDatabase() {
    companion object {
        @Volatile private var instance: ProCamDatabase? = null
        fun get(context: android.content.Context): ProCamDatabase = instance ?: synchronized(this) {
            instance ?: androidx.room.Room.databaseBuilder(context.applicationContext, ProCamDatabase::class.java, "procam-private.db")
                .addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vault_media ADD COLUMN sizeBytes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vault_media_deleted ON vault_media(deleted)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vault_media_albumId ON vault_media(albumId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS private_albums (id TEXT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE TABLE IF NOT EXISTS hidden_apps (packageName TEXT NOT NULL, customName TEXT, folderId TEXT, addedAt INTEGER NOT NULL, PRIMARY KEY(packageName))")
                db.execSQL("CREATE TABLE IF NOT EXISTS app_folders (id TEXT NOT NULL, name TEXT NOT NULL, PRIMARY KEY(id))")
            }
        }
    }
    abstract fun vaultMediaDao(): VaultMediaDao
    abstract fun privateAlbumDao(): PrivateAlbumDao
    abstract fun hiddenAppDao(): HiddenAppDao
    abstract fun appFolderDao(): AppFolderDao
}
