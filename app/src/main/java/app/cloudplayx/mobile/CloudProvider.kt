package app.cloudplayx.mobile

import android.app.AuthenticationRequiredException
import android.app.PendingIntent
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.system.ErrnoException
import android.system.OsConstants
import java.io.FileNotFoundException
import java.io.IOException
import java.net.URLConnection
import java.util.concurrent.ConcurrentHashMap

/**
 * Shows the cloud as a drive named "CloudPlayX" in the Files app and in every app's file picker.
 * Nothing is downloaded when you browse; file contents are fetched block by block when an app reads them.
 */
class CloudProvider : DocumentsProvider() {

    companion object {
        const val AUTHORITY = "app.cloudplayx.mobile.documents"
        private const val ROOT_ID = "cloudplayx"
        private const val ROOT_DOC = "root"
        private const val LIST_TTL_MS = 30_000L

        private val ROOT_COLS = arrayOf(
            Root.COLUMN_ROOT_ID, Root.COLUMN_FLAGS, Root.COLUMN_ICON, Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_MIME_TYPES,
        )
        private val DOC_COLS = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_MIME_TYPE, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS, Document.COLUMN_SIZE,
        )
    }

    private lateinit var settings: Settings
    private var remoteCache: Remote? = null
    private var remoteVersion = -1
    private val entries = ConcurrentHashMap<String, RemoteEntry>()
    private val listings = ConcurrentHashMap<String, Pair<Long, List<RemoteEntry>>>()

    override fun onCreate(): Boolean {
        settings = Settings(context!!)
        return true
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: ROOT_COLS)
        val summary = when (settings.backend) {
            "gdrive" -> "Google Drive"
            "webdav" -> "WebDAV"
            else -> "Not set up: open the CloudPlayX app"
        }
        cursor.newRow().apply {
            add(Root.COLUMN_ROOT_ID, ROOT_ID)
            add(Root.COLUMN_DOCUMENT_ID, ROOT_DOC)
            add(Root.COLUMN_TITLE, "CloudPlayX")
            add(Root.COLUMN_SUMMARY, summary)
            add(Root.COLUMN_ICON, R.drawable.ic_cloud)
            add(Root.COLUMN_FLAGS, 0)
            add(Root.COLUMN_MIME_TYPES, "*/*")
        }
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor = guard {
        val cursor = MatrixCursor(projection ?: DOC_COLS)
        addRow(cursor, entryFor(documentId), documentId)
        cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String, projection: Array<out String>?, sortOrder: String?,
    ): Cursor = guard {
        val cursor = MatrixCursor(projection ?: DOC_COLS)
        for (e in children(parentDocumentId)) addRow(cursor, e, e.id)
        cursor
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
        guard {
            if (mode != "r") throw FileNotFoundException("CloudPlayX is read-only in this version.")
            val entry = entryFor(documentId)
            if (entry.isDir) throw FileNotFoundException("This is a folder.")
            val remote = remote()
            val cache = CacheHolder.get(context!!)
            val thread = HandlerThread("cpx-io").apply { start() }
            val storage = context!!.getSystemService(StorageManager::class.java)
            storage.openProxyFileDescriptor(
                ParcelFileDescriptor.MODE_READ_ONLY,
                FileCallback(entry, remote, cache, thread),
                Handler(thread.looper),
            )
        }

    /** Answers the kernel's read() calls for one open file, straight from the block cache. */
    private class FileCallback(
        private val entry: RemoteEntry,
        private val remote: Remote,
        private val cache: BlockCache,
        private val thread: HandlerThread,
    ) : ProxyFileDescriptorCallback() {
        override fun onGetSize(): Long = entry.size

        override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
            try {
                return cache.read(entry, remote, offset, data, 0, size)
            } catch (e: IOException) {
                throw ErrnoException("onRead", OsConstants.EIO)
            }
        }

        override fun onRelease() {
            thread.quitSafely()
        }
    }

    // ---- helpers ----

    private fun addRow(cursor: MatrixCursor, e: RemoteEntry, docId: String) {
        val mime = when {
            e.isDir -> Document.MIME_TYPE_DIR
            else -> e.mime ?: URLConnection.guessContentTypeFromName(e.name) ?: "application/octet-stream"
        }
        cursor.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, docId)
            add(Document.COLUMN_DISPLAY_NAME, e.name)
            add(Document.COLUMN_MIME_TYPE, mime)
            add(Document.COLUMN_SIZE, if (e.isDir) null else e.size)
            add(Document.COLUMN_LAST_MODIFIED, if (e.modified > 0) e.modified else null)
            add(Document.COLUMN_FLAGS, 0)
        }
    }

    private fun entryFor(docId: String): RemoteEntry {
        if (docId == ROOT_DOC) return RemoteEntry(remote().rootId, "CloudPlayX", true, 0, 0)
        entries[docId]?.let { return it }
        val found = remote().stat(docId) ?: throw FileNotFoundException("Not found: $docId")
        entries[docId] = found
        return found
    }

    private fun children(parentDoc: String): List<RemoteEntry> {
        val r = remote()
        val id = if (parentDoc == ROOT_DOC) r.rootId else parentDoc
        val now = System.currentTimeMillis()
        val hit = listings[id]
        if (hit != null && now - hit.first < LIST_TTL_MS) return hit.second
        val list = r.list(id).sortedWith(compareByDescending<RemoteEntry> { it.isDir }.thenBy { it.name.lowercase() })
        for (e in list) entries[e.id] = e
        listings[id] = now to list
        return list
    }

    @Synchronized
    private fun remote(): Remote {
        val current = remoteCache
        if (current != null && remoteVersion == settings.configVersion) return current
        val created = RemoteFactory.create(context!!, settings)
            ?: throw authRequired("Open the CloudPlayX app and choose a cloud first.")
        remoteCache = created
        remoteVersion = settings.configVersion
        entries.clear()
        listings.clear()
        return created
    }

    private fun authRequired(message: String): AuthenticationRequiredException {
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return AuthenticationRequiredException(IllegalStateException(message), pending)
    }

    /** Binder can only carry a few exception types: turn cloud errors into ones the Files app understands. */
    private inline fun <T> guard(block: () -> T): T {
        try {
            return block()
        } catch (e: RemoteException) {
            if (e.needsSignIn) throw authRequired(e.message ?: "Sign-in needed")
            throw FileNotFoundException(e.message ?: "Cloud error")
        } catch (e: FileNotFoundException) {
            throw e
        } catch (e: IOException) {
            throw FileNotFoundException(e.message ?: "I/O error")
        }
    }
}
