package app.cloudplayx.mobile

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit

/** Google authorization for the official Drive API (read-only). */
object GoogleAuth {
    const val DRIVE_READONLY = "https://www.googleapis.com/auth/drive.readonly"

    fun request(): AuthorizationRequest =
        AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_READONLY))).build()

    @Volatile private var token: String? = null
    @Volatile private var tokenUntil = 0L

    /** Keep an access token returned by the user-visible authorization flow. */
    fun remember(accessToken: String) {
        if (accessToken.isBlank()) return
        token = accessToken
        // Google access tokens typically last one hour; renew a little early.
        tokenUntil = System.currentTimeMillis() + 45 * 60 * 1000L
    }

    /** Must not be called on the main thread. */
    fun token(ctx: Context): String {
        val cached = token
        if (cached != null && System.currentTimeMillis() < tokenUntil) return cached
        try {
            val client = Identity.getAuthorizationClient(ctx.applicationContext)
            val result = Tasks.await(client.authorize(request()), 30, TimeUnit.SECONDS)
            if (result.hasResolution()) {
                throw RemoteException("Open CloudPlayX and tap Connect Google Drive to finish authorization.", needsSignIn = true)
            }
            val accessToken = result.accessToken
                ?: throw RemoteException("Google returned no access token. Reconnect Google Drive.", needsSignIn = true)
            remember(accessToken)
            return accessToken
        } catch (e: RemoteException) {
            throw e
        } catch (e: Exception) {
            throw RemoteException("Google authorization failed: ${e.message ?: e.javaClass.simpleName}", needsSignIn = true)
        }
    }

    fun forget() {
        token = null
        tokenUntil = 0L
    }
}
