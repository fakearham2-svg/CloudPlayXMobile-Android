package app.cloudplayx.mobile

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.InputType
import android.text.format.Formatter
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.auth.api.identity.Identity

/** Native setup dashboard. Files are browsable in-app and through Android's system document picker. */
class MainActivity : Activity() {
    private companion object { const val REQ_AUTH = 1001 }

    private val bg = Color.rgb(10, 15, 30)
    private val panel = Color.rgb(21, 29, 49)
    private val accent = Color.rgb(125, 93, 255)
    private val mint = Color.rgb(55, 211, 177)
    private val primaryText = Color.rgb(244, 247, 255)
    private val mutedText = Color.rgb(166, 177, 202)

    private lateinit var settings: Settings
    private lateinit var status: TextView
    private lateinit var cacheInfo: TextView
    private lateinit var urlEdit: EditText
    private lateinit var userEdit: EditText
    private lateinit var passEdit: EditText
    private lateinit var budgetEdit: EditText
    private lateinit var ageEdit: EditText
    private var connectionMessage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
            setBackgroundColor(bg)
        }

        content.addView(label("☁  CLOUDPLAYX MOBILE", 13f, mint, bold = true))
        content.addView(label("Your cloud. Your files. Your control.", 27f, primaryText, bold = true).apply {
            setPadding(0, dp(8), 0, dp(5))
        })
        content.addView(label("Browse cloud folders, then save files locally when an app needs them.", 14f, mutedText))

        val connectionCard = card()
        connectionCard.addView(label("01  ·  CLOUD CONNECTION", 12f, mint, bold = true))
        connectionCard.addView(label("Connect Google Drive or use a compatible WebDAV server.", 13f, mutedText).apply {
            setPadding(0, dp(4), 0, dp(2))
        })
        status = label("", 14f, primaryText)
        status.background = rounded(Color.rgb(13, 39, 48), 12)
        status.setPadding(dp(12), dp(10), dp(12), dp(10))
        connectionCard.addView(status)
        connectionCard.addView(actionButton("Connect Google Drive", accent) { signInGoogle() })
        connectionCard.addView(actionButton("Test connection", Color.rgb(43, 58, 88)) { testConnection() })
        connectionCard.addView(actionButton("Browse cloud files", mint, darkText = true) { openBrowser() })
        connectionCard.addView(actionButton("Open Android document picker", Color.rgb(43, 58, 88)) { openSystemPicker() })
        content.addView(connectionCard, cardParams())

        val webCard = card()
        webCard.addView(label("02  ·  WEBDAV SERVER", 12f, mint, bold = true))
        webCard.addView(label("Optional · Nextcloud, rclone WebDAV, and compatible servers", 13f, mutedText))
        urlEdit = field("Server URL · http://192.168.1.5:8080", settings.webDavUrl, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        userEdit = field("Username (optional)", settings.webDavUser, InputType.TYPE_CLASS_TEXT)
        passEdit = field("Password (optional)", settings.webDavPass, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        webCard.addView(urlEdit)
        webCard.addView(userEdit)
        webCard.addView(passEdit)
        webCard.addView(actionButton("Use this WebDAV server", Color.rgb(43, 58, 88)) { saveWebDav() })
        content.addView(webCard, cardParams())

        val cacheCard = card()
        cacheCard.addView(label("03  ·  SMART CACHE", 12f, mint, bold = true))
        cacheCard.addView(label("Frequently used file blocks stay on this phone. The original files remain in the cloud.", 13f, mutedText))
        budgetEdit = field("Cache budget in GB", (settings.cacheBudgetMb / 1024.0).toString(), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        ageEdit = field("Remove unused blocks after (days)", settings.maxAgeDays.toString(), InputType.TYPE_CLASS_NUMBER)
        cacheCard.addView(budgetEdit)
        cacheCard.addView(ageEdit)
        cacheCard.addView(actionButton("Save cache settings", Color.rgb(43, 58, 88)) { saveCacheSettings() })
        cacheInfo = label("", 13f, mutedText)
        cacheInfo.setPadding(0, dp(10), 0, dp(4))
        cacheCard.addView(cacheInfo)
        cacheCard.addView(actionButton("Clear local cache", Color.rgb(95, 47, 72)) { clearCache() })
        content.addView(cacheCard, cardParams())

        content.addView(label("CLOUD BROWSING  ·  LOCAL FILE SAVING  ·  NO ROOT", 11f, mutedText).apply {
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp(8), 0, dp(4))
        })

        setContentView(ScrollView(this).apply {
            clipToPadding = false
            setFillViewport(true)
            addView(content)
            setBackgroundColor(bg)
        })
        refresh()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_AUTH) return

        if (resultCode != RESULT_OK || data == null) {
            connectionMessage = when {
                resultCode == RESULT_CANCELED -> "Google sign-in was cancelled. Your cloud connection was not changed. Tap Connect Google Drive to try again."
                data == null -> "Google sign-in returned no response data. Check Google Play services and try again."
                else -> "Google sign-in did not complete (result code $resultCode). Try again; if it repeats, check the OAuth package name and signing SHA-1."
            }
            refreshStatus()
            toast(connectionMessage!!, long = true)
            return
        }

        try {
            val authResult =
                Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data)
            val accessToken = authResult.accessToken
            if (accessToken.isNullOrBlank()) {
                throw RemoteException(
                    "Google completed the permission screen but returned no access token. Check OAuth setup and try again."
                )
            }
            GoogleAuth.remember(accessToken)
            useBackend("gdrive")
        } catch (e: Exception) {
            connectionMessage =
                "Google authorization failed: ${e.message ?: e.javaClass.simpleName}"
            refreshStatus()
            toast(connectionMessage!!, long = true)
        }
    }

    private fun signInGoogle() {
        Identity.getAuthorizationClient(this).authorize(GoogleAuth.request())
            .addOnSuccessListener { result ->
                val pending = result.pendingIntent
                if (result.hasResolution() && pending != null) {
                    @Suppress("DEPRECATION")
                    startIntentSenderForResult(pending.intentSender, REQ_AUTH, null, 0, 0, 0)
                } else {
                    val accessToken = result.accessToken
                    if (accessToken.isNullOrBlank()) {
                        connectionMessage = "Google returned no access token. Check the OAuth client SHA-1 and Drive API setup."
                        refreshStatus()
                    } else {
                        GoogleAuth.remember(accessToken)
                        useBackend("gdrive")
                    }
                }
            }
            .addOnFailureListener { e ->
                connectionMessage = "Google authorization failed: ${e.message ?: e.javaClass.simpleName}. Check package name, SHA-1, consent-screen test user, and Drive API."
                refreshStatus()
                toast(connectionMessage!!, long = true)
            }
    }

    private fun saveWebDav() {
        val url = urlEdit.text.toString().trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            toast("Server URL must start with http:// or https://")
            return
        }
        settings.webDavUrl = url
        settings.webDavUser = userEdit.text.toString().trim()
        settings.webDavPass = passEdit.text.toString()
        if (url.startsWith("http://", ignoreCase = true) && settings.webDavPass.isNotBlank()) {
            toast("Warning: HTTP does not encrypt your WebDAV password or file data. Prefer HTTPS except on a trusted local network.", long = true)
        }
        useBackend("webdav")
    }

    private fun useBackend(name: String) {
        settings.backend = name
        settings.configVersion = settings.configVersion + 1
        connectionMessage = "${if (name == "gdrive") "Google Drive" else "WebDAV"} selected · checking connection…"
        contentResolver.notifyChange(DocumentsContract.buildRootsUri(CloudProvider.AUTHORITY), null)
        refreshStatus()
        refreshCacheInfo()
        testConnection()
    }

    private fun testConnection() {
        val backend = settings.backend
        if (backend.isBlank()) {
            connectionMessage = "Choose Google Drive or configure a WebDAV server first."
            refreshStatus()
            return
        }
        connectionMessage = "Testing ${if (backend == "gdrive") "Google Drive" else "WebDAV"}…"
        refreshStatus()
        Thread {
            val result = try {
                val remote = RemoteFactory.create(applicationContext, settings)
                    ?: throw RemoteException("Choose a cloud first.")
                val count = remote.list(remote.rootId).size
                if (backend == "gdrive" && remote is GoogleDriveRemote) {
                    "Connected · Google Drive · $count item(s) in root folder\n${remote.accountSummary()}"
                } else {
                    "Connected · WebDAV · $count item(s) in root folder"
                }
            } catch (e: Exception) {
                "Connection failed · ${e.message ?: e.javaClass.simpleName}"
            }
            runOnUiThread {
                connectionMessage = result
                refreshStatus()
                if (result.startsWith("Connection failed")) toast(result, long = true)
            }
        }.start()
    }

    private fun openBrowser() {
        if (settings.backend.isBlank()) {
            toast("Connect Google Drive or configure a WebDAV server first.")
            return
        }
        startActivity(Intent(this, BrowserActivity::class.java))
    }

    private fun openSystemPicker() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            startActivity(intent)
        } catch (e: Exception) {
            toast("Android file picker could not be opened: ${e.message}", long = true)
        }
    }

    private fun saveCacheSettings() {
        val gb = budgetEdit.text.toString().toDoubleOrNull()
        val days = ageEdit.text.toString().toIntOrNull()
        if (gb == null || gb < 0.1 || gb > 2048 || days == null || days < 1 || days > 3650) {
            toast("Enter a cache budget from 0.1 to 2048 GB and unused age from 1 to 3650 days.")
            return
        }
        settings.cacheBudgetMb = (gb * 1024).toLong()
        settings.maxAgeDays = days
        Thread {
            val cache = CacheHolder.get(this)
            cache.trimByAge(days * 86_400_000L)
            cache.trimToBudget()
            runOnUiThread { toast("Cache settings saved."); refreshCacheInfo() }
        }.start()
    }

    private fun clearCache() {
        Thread {
            CacheHolder.get(this).clear()
            runOnUiThread { toast("Local cache cleared. Cloud files are untouched."); refreshCacheInfo() }
        }.start()
    }

    private fun refresh() {
        refreshStatus()
        refreshCacheInfo()
    }

    private fun refreshStatus() {
        if (!::status.isInitialized) return
        status.text = connectionMessage ?: when (settings.backend) {
            "gdrive" -> "Google Drive selected · tap Test connection"
            "webdav" -> "WebDAV selected · tap Test connection"
            else -> "No cloud connected yet. Choose a provider below."
        }
    }

    private fun refreshCacheInfo() {
        if (!::cacheInfo.isInitialized) return
        Thread {
            val used = CacheHolder.get(this).usedBytes
            val budget = settings.budgetBytes
            runOnUiThread {
                if (::cacheInfo.isInitialized) {
                    cacheInfo.text = "Using ${Formatter.formatFileSize(this, used)} of ${Formatter.formatFileSize(this, budget)}"
                }
            }
        }.start()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(panel, 20)
    }

    private fun cardParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        topMargin = dp(14)
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(3), 0, dp(3))
    }

    private fun actionButton(text: String, color: Int, darkText: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 14f
        setTextColor(if (darkText) bg else primaryText)
        backgroundTintList = ColorStateList.valueOf(color)
        minHeight = dp(48)
        setOnClickListener { _: View -> onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        }
    }

    private fun field(hint: String, value: String, inputType: Int) = EditText(this).apply {
        this.hint = hint
        setText(value)
        this.inputType = inputType
        setTextColor(primaryText)
        setHintTextColor(mutedText)
        textSize = 14f
        setSingleLine(true)
        backgroundTintList = ColorStateList.valueOf(Color.rgb(100, 116, 150))
        setPadding(dp(4), dp(10), dp(4), dp(10))
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun toast(message: String, long: Boolean = false) =
        Toast.makeText(this, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}
