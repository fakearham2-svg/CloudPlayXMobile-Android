package app.cloudplayx.mobile

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.format.Formatter
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.net.URLConnection
import java.io.IOException
import java.io.OutputStream

/** A built-in browser so users can inspect their cloud even if their OEM file manager hides document providers. */
class BrowserActivity : Activity() {
    private val bg = Color.rgb(10, 15, 30)
    private val panel = Color.rgb(21, 29, 49)
    private val accent = Color.rgb(125, 93, 255)
    private val mint = Color.rgb(55, 211, 177)
    private val primaryText = Color.rgb(244, 247, 255)
    private val mutedText = Color.rgb(166, 177, 202)

    private lateinit var settings: Settings
    private lateinit var title: TextView
    private lateinit var pathText: TextView
    private lateinit var status: TextView
    private lateinit var listBox: LinearLayout
    private var currentId = "root"
    private val parents = mutableListOf<Pair<String, String>>()
    private var pendingDownload: RemoteEntry? = null
    private companion object { const val REQ_SAVE_DOWNLOAD = 2401 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        currentId = if (settings.backend == "webdav") "/" else "root"
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(bg)
        }
        title = label("Cloud files", 25f, primaryText, true)
        root.addView(title)
        pathText = label("/", 13f, mutedText)
        root.addView(pathText)

        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        toolbar.addView(actionButton("← Back", Color.rgb(43, 58, 88)) { goBack() }, LinearLayout.LayoutParams(0, dp(46), 1f))
        toolbar.addView(actionButton("↻ Refresh", accent) { load() }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(8) })
        root.addView(toolbar)

        status = label("Loading cloud files…", 13f, mutedText)
        status.setPadding(0, dp(9), 0, dp(9))
        root.addView(status)
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply {
            addView(listBox)
            isFillViewport = true
            clipToPadding = false
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(label("Tap a folder to browse. Tap a file to open it; long-press a file to save a local copy.", 12f, mutedText))
        setContentView(root)
        load()
    }

    override fun onBackPressed() {
        if (parents.isNotEmpty()) goBack() else super.onBackPressed()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_SAVE_DOWNLOAD) return
        val entry = pendingDownload
        pendingDownload = null
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null || entry == null) {
            status.text = "Download cancelled"
            return
        }
        downloadToUri(entry, uri)
    }

    private fun load() {
        status.text = "Loading…"
        pathText.text = "/" + parents.joinToString("/") { it.second }
        listBox.removeAllViews()
        listBox.addView(label("Fetching file list…", 14f, mutedText))
        val backend = settings.backend
        Thread {
            val outcome: Pair<List<RemoteEntry>?, String> = try {
                val remote = RemoteFactory.create(applicationContext, settings)
                    ?: throw RemoteException("No cloud is selected. Go back and connect a provider.")
                remote.list(currentId).sortedWith(compareByDescending<RemoteEntry> { it.isDir }.thenBy { it.name.lowercase() }) to ""
            } catch (e: Exception) {
                null to (e.message ?: e.javaClass.simpleName)
            }
            val result = outcome.first
            val errorMessage = outcome.second
            runOnUiThread {
                listBox.removeAllViews()
                if (result == null) {
                    status.text = "Could not load $backend files"
                    listBox.addView(label(errorMessage.ifBlank { "Unknown connection error" }, 14f, Color.rgb(255, 145, 145)))
                } else {
                    status.text = "${result.size} item(s) · ${if (backend == "gdrive") "Google Drive" else "WebDAV"}"
                    if (result.isEmpty()) listBox.addView(label("This folder is empty.", 14f, mutedText))
                    result.forEach { addEntryRow(it) }
                }
            }
        }.start()
    }

    private fun addEntryRow(entry: RemoteEntry) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(11), dp(12), dp(11))
            background = rounded(panel, 14)
            isClickable = true
            isFocusable = true
        }
        val icon = label(if (entry.isDir) "📁" else iconFor(entry), 23f, primaryText)
        row.addView(icon, LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.WRAP_CONTENT))
        val textColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textColumn.addView(label(entry.name, 14f, primaryText, true))
        textColumn.addView(label(if (entry.isDir) "Folder" else Formatter.formatFileSize(this, entry.size), 12f, mutedText))
        row.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(label("›", 25f, mutedText))
        row.setOnClickListener {
            if (entry.isDir) {
                parents.add(currentId to entry.name)
                currentId = entry.id
                load()
            } else openFile(entry)
        }
        row.setOnLongClickListener {
            if (entry.isDir) {
                toast("Open the folder first, then long-press a file to save it.")
            } else {
                pendingDownload = entry
                val mime = entry.mime ?: URLConnection.guessContentTypeFromName(entry.name) ?: "application/octet-stream"
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = mime
                    putExtra(Intent.EXTRA_TITLE, entry.name)
                }
                try {
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, REQ_SAVE_DOWNLOAD)
                } catch (e: Exception) {
                    pendingDownload = null
                    toast("Could not open save dialog: ${e.message}", long = true)
                }
            }
            true
        }
        listBox.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(8)
        })
    }

    private fun openFile(entry: RemoteEntry) {
        val uri = DocumentsContract.buildDocumentUri(CloudProvider.AUTHORITY, entry.id)
        val mime = entry.mime ?: URLConnection.guessContentTypeFromName(entry.name) ?: "application/octet-stream"
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            toast("No compatible app can open this file type. The file is still available through the Android file picker.", long = true)
        }
    }

    /** Streams a cloud file to a user-selected SAF destination without buffering the entire file. */
    private fun downloadToUri(entry: RemoteEntry, uri: android.net.Uri) {
        status.text = "Preparing download: ${entry.name}"
        Thread {
            var output: OutputStream? = null
            try {
                val remote = RemoteFactory.create(applicationContext, settings)
                    ?: throw RemoteException("No cloud is selected. Connect a provider first.")
                output = contentResolver.openOutputStream(uri, "wt")
                    ?: throw IOException("Android could not open the selected destination")
                val chunkSize = 1024 * 1024
                var offset = 0L
                while (offset < entry.size) {
                    val amount = minOf(chunkSize.toLong(), entry.size - offset).toInt()
                    val chunk = remote.readRange(entry, offset, amount)
                    if (chunk.size != amount) throw IOException("Incomplete download at ${offset + chunk.size} of ${entry.size} bytes")
                    output.write(chunk)
                    offset += chunk.size
                    val progress = if (entry.size > 0) (offset * 100 / entry.size).toInt() else 100
                    runOnUiThread { status.text = "Saving ${entry.name} · $progress%" }
                }
                output.flush()
                output.close()
                output = null
                runOnUiThread {
                    status.text = "Saved ${entry.name} · ${Formatter.formatFileSize(this, entry.size)}"
                    toast("Download complete: ${entry.name}", long = true)
                }
            } catch (e: Exception) {
                runCatching { output?.close() }
                runOnUiThread {
                    status.text = "Download failed: ${e.message ?: e.javaClass.simpleName}"
                    toast("Download failed. The destination may contain a partial file; remove it if needed.", long = true)
                }
            }
        }.start()
    }

    private fun goBack() {
        if (parents.isEmpty()) {
            finish()
            return
        }
        currentId = parents.removeAt(parents.lastIndex).first
        load()
    }

    private fun iconFor(entry: RemoteEntry): String = when {
        entry.mime?.startsWith("image/") == true -> "🖼️"
        entry.mime?.startsWith("audio/") == true -> "🎵"
        entry.mime?.startsWith("video/") == true -> "🎬"
        entry.name.substringAfterLast('.', "").lowercase() in setOf("zip", "7z", "rar") -> "📦"
        entry.name.substringAfterLast('.', "").lowercase() in setOf("txt", "md", "pdf", "doc", "docx") -> "📄"
        else -> "📃"
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(2), 0, dp(2))
    }

    private fun actionButton(text: String, color: Int, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 13f
        setTextColor(primaryText)
        backgroundTintList = ColorStateList.valueOf(color)
        minHeight = dp(44)
        setOnClickListener { _: View -> onClick() }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun toast(message: String, long: Boolean = false) =
        Toast.makeText(this, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}
