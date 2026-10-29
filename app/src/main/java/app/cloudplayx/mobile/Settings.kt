package app.cloudplayx.mobile

import android.content.Context
import java.io.File

/** Small settings store. WebDAV password is kept in the app-private preferences (not encrypted in this version). */
class Settings(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("cloudplayx", Context.MODE_PRIVATE)

    /** "", "gdrive" or "webdav". */
    var backend: String
        get() = p.getString("backend", "") ?: ""
        set(v) { p.edit().putString("backend", v).apply() }

    var webDavUrl: String
        get() = p.getString("webDavUrl", "") ?: ""
        set(v) { p.edit().putString("webDavUrl", v).apply() }

    var webDavUser: String
        get() = p.getString("webDavUser", "") ?: ""
        set(v) { p.edit().putString("webDavUser", v).apply() }

    var webDavPass: String
        get() = p.getString("webDavPass", "") ?: ""
        set(v) { p.edit().putString("webDavPass", v).apply() }

    /** Cache budget in MiB (default 10 GiB). */
    var cacheBudgetMb: Long
        get() = p.getLong("cacheBudgetMb", 10L * 1024).coerceIn(1L, 2_097_152L)
        set(v) { p.edit().putLong("cacheBudgetMb", v.coerceIn(1L, 2_097_152L)).apply() }

    /** Cached blocks not read for this many days are deleted. */
    var maxAgeDays: Int
        get() = p.getInt("maxAgeDays", 30).coerceIn(1, 3650)
        set(v) { p.edit().putInt("maxAgeDays", v.coerceIn(1, 3650)).apply() }

    /** Changes whenever the cloud account changes, so the provider drops its in-memory state. */
    var configVersion: Int
        get() = p.getInt("configVersion", 0)
        set(v) { p.edit().putInt("configVersion", v).apply() }

    val budgetBytes: Long get() = cacheBudgetMb * 1024L * 1024L
}

object RemoteFactory {
    fun create(ctx: Context, settings: Settings): Remote? = when (settings.backend) {
        "gdrive" -> GoogleDriveRemote { GoogleAuth.token(ctx) }
        "webdav" -> WebDavRemote(settings.webDavUrl, settings.webDavUser, settings.webDavPass)
        else -> null
    }
}

/** One cache per process, shared by the file provider and the settings screen. */
object CacheHolder {
    @Volatile
    private var cache: BlockCache? = null

    fun get(ctx: Context): BlockCache {
        val s = Settings(ctx)
        val c = cache ?: synchronized(this) {
            cache ?: BlockCache(File(ctx.applicationContext.cacheDir, "blocks"), s.budgetBytes).also {
                it.trimByAge(s.maxAgeDays * 86_400_000L)
                cache = it
            }
        }
        c.budgetBytes = s.budgetBytes
        return c
    }
}
