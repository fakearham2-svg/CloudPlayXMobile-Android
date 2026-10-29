package app.cloudplayx.mobile

import android.net.Uri
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant

/** Google Drive through the official REST API v3 (read-only). */
class GoogleDriveRemote(private val token: () -> String) : Remote {
    override val rootId = "root"
    private val fields = "id,name,mimeType,size,modifiedTime"

    private fun request(url: String): Request.Builder =
        Request.Builder().url(url).header("Authorization", "Bearer ${token()}")


    /** Returns the signed-in Google account and Drive quota from the official Drive API. */
    fun accountSummary(): String {
        val url = "https://www.googleapis.com/drive/v3/about".toHttpUrl().newBuilder()
            .addQueryParameter("fields", "user(displayName,emailAddress),storageQuota(limit,usage,usageInDrive)")
            .build()
        val json = Http.execute(request(url.toString()).build()) { JSONObject(it.body!!.string()) }
        val user = json.optJSONObject("user")
        val quota = json.optJSONObject("storageQuota")
        val name = user?.optString("displayName", "")?.takeIf { it.isNotBlank() } ?: "Google account"
        val email = user?.optString("emailAddress", "")?.takeIf { it.isNotBlank() }
        val used = quota?.optString("usage", "")?.toLongOrNull()
        val limit = quota?.optString("limit", "")?.toLongOrNull()
        val usageLine = if (used != null && limit != null && limit > 0) {
            "Drive storage: ${formatBytes(used)} used of ${formatBytes(limit)}"
        } else if (used != null) {
            "Drive storage used: ${formatBytes(used)} · quota limit not reported"
        } else {
            "Drive storage quota unavailable"
        }
        return listOfNotNull(name, email, usageLine).joinToString("\n")
    }

    private fun formatBytes(value: Long): String {
        if (value < 1024) return "$value B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var amount = value.toDouble()
        var unit = -1
        do { amount /= 1024.0; unit++ } while (amount >= 1024.0 && unit < units.lastIndex)
        return String.format(java.util.Locale.US, "%.2f %s", amount, units[unit])
    }

    override fun list(dirId: String): List<RemoteEntry> {
        val out = ArrayList<RemoteEntry>()
        var page: String? = null
        do {
            val builder = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", "'$dirId' in parents and trashed=false")
                .addQueryParameter("fields", "nextPageToken,files($fields)")
                .addQueryParameter("pageSize", "1000")
                .addQueryParameter("orderBy", "folder,name")
            val pageToken = page
            if (pageToken != null) builder.addQueryParameter("pageToken", pageToken)
            val json = Http.execute(request(builder.build().toString()).build()) { JSONObject(it.body!!.string()) }
            val files = json.optJSONArray("files")
            if (files != null) {
                for (i in 0 until files.length()) {
                    val e = parse(files.getJSONObject(i))
                    if (e != null) out.add(e)
                }
            }
            page = json.optString("nextPageToken", "").ifEmpty { null }
        } while (page != null)
        return out
    }

    override fun stat(id: String): RemoteEntry? {
        val url = "https://www.googleapis.com/drive/v3/files/${Uri.encode(id)}".toHttpUrl().newBuilder()
            .addQueryParameter("fields", fields)
            .build()
        return try {
            Http.execute(request(url.toString()).build()) { parse(JSONObject(it.body!!.string())) }
        } catch (e: RemoteException) {
            if (e.message?.startsWith("HTTP 404") == true) null else throw e
        }
    }

    override fun readRange(entry: RemoteEntry, offset: Long, length: Int): ByteArray {
        val url = "https://www.googleapis.com/drive/v3/files/${Uri.encode(entry.id)}".toHttpUrl().newBuilder()
            .addQueryParameter("alt", "media")
            .build()
        val req = request(url.toString()).header("Range", "bytes=$offset-${offset + length - 1}").build()
        return Http.execute(req) { Http.readBody(it, offset, length) }
    }

    private fun parse(o: JSONObject): RemoteEntry? {
        val mime = o.optString("mimeType")
        val isDir = mime == "application/vnd.google-apps.folder"
        // Google Docs/Sheets/Slides have no raw bytes to stream, so they are skipped in this version.
        if (!isDir && mime.startsWith("application/vnd.google-apps.")) return null
        return RemoteEntry(
            id = o.getString("id"),
            name = o.optString("name", "unnamed"),
            isDir = isDir,
            size = o.optString("size", "0").toLongOrNull() ?: 0L,
            modified = runCatching { Instant.parse(o.getString("modifiedTime")).toEpochMilli() }.getOrDefault(0L),
            mime = if (isDir) null else mime.ifEmpty { null },
        )
    }
}
