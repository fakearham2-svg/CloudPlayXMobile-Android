package app.cloudplayx.mobile

import java.io.IOException

/** One file or folder in the cloud. [id] is only meaningful to the [Remote] that produced it. */
data class RemoteEntry(
    val id: String,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
    val mime: String? = null,
)

/** [needsSignIn] = the user has to open the app and sign in (again). */
class RemoteException(message: String, val needsSignIn: Boolean = false) : IOException(message)

/** A cloud that can list folders and return byte ranges of files. All calls block; never call them on the main thread. */
interface Remote {
    val rootId: String
    fun list(dirId: String): List<RemoteEntry>
    fun stat(id: String): RemoteEntry?

    /** Returns [length] bytes starting at [offset]; fewer only if the file ends earlier. */
    fun readRange(entry: RemoteEntry, offset: Long, length: Int): ByteArray
}
