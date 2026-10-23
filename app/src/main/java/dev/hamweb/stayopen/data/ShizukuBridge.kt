package dev.hamweb.stayopen.data

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import dev.hamweb.stayopen.BuildConfig
import dev.hamweb.stayopen.domain.BridgeAccess
import dev.hamweb.stayopen.privileged.IRamService
import dev.hamweb.stayopen.privileged.RamUserService
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku

/** Owns permission listeners and a non-daemon, explicitly releasable privileged worker. */
class ShizukuBridge(private val context: Context, private val changed: (BridgeAccess, String) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var remote: IRamService? = null
    private var pending: CompletableDeferred<IRamService>? = null
    private var binding = false
    private var generation = 0L
    private var activeConnection: ServiceConnection? = null
    private val args = Shizuku.UserServiceArgs(ComponentName(context, RamUserService::class.java))
        .tag("ram-clear-${context.applicationInfo.uid / 100_000}")
        .version(BuildConfig.VERSION_CODE).daemon(false).debuggable(BuildConfig.DEBUG).processNameSuffix("ram_bridge")
    private fun connection(id: Long, reply: CompletableDeferred<IRamService>) = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (id != generation) return
            binding = false
            if (binder == null) { reply.completeExceptionally(IllegalStateException("No privileged worker")); return }
            remote = IRamService.Stub.asInterface(binder)
            reply.complete(remote!!)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            // A previous unbind can report death after the next bind has already begun.
            if (id != generation) return
            remote = null; binding = false
            reply.completeExceptionally(IllegalStateException("Privileged worker disconnected"))
        }
    }
    private val received = Shizuku.OnBinderReceivedListener { refresh() }
    private val dead = Shizuku.OnBinderDeadListener {
        generation++; activeConnection = null
        remote = null; binding = false
        pending?.completeExceptionally(IllegalStateException("Shizuku stopped"))
        changed(BridgeAccess.STOPPED, "Shizuku stopped. Open it and start the service again.")
    }
    private val result = Shizuku.OnRequestPermissionResultListener { code, _ -> if (code == 121) refresh() }

    init {
        Shizuku.addBinderReceivedListenerSticky(received, handler)
        Shizuku.addBinderDeadListener(dead, handler)
        Shizuku.addRequestPermissionResultListener(result, handler)
        refresh()
    }

    fun refresh(): BridgeAccess = try {
        val access: BridgeAccess
        val detail: String
        if (!Shizuku.pingBinder()) {
            @Suppress("DEPRECATION")
            val installed = runCatching { context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0) }.isSuccess
            access = if (installed) BridgeAccess.STOPPED else BridgeAccess.NOT_INSTALLED
            detail = if (installed) "Start Shizuku, then return here." else "Install Shizuku to grant background-process clearing access."
        } else if (Shizuku.isPreV11() || Shizuku.getVersion() < 13) {
            access = BridgeAccess.UNSUPPORTED; detail = "Update Shizuku to version 13 or newer."
        } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            access = BridgeAccess.READY
            detail = "Access granted · ${if (Shizuku.getUid() == 0) "root" else "ADB / shell"} · eligible cached apps can be cleared"
        } else {
            access = if (Shizuku.shouldShowRequestPermissionRationale()) BridgeAccess.DENIED else BridgeAccess.PERMISSION
            detail = if (access == BridgeAccess.DENIED) "Allow StayOpen in Shizuku → Authorised applications." else "Shizuku is running. Grant StayOpen access to continue."
        }
        changed(access, detail); access
    } catch (_: Exception) { changed(BridgeAccess.STOPPED, "Shizuku is unavailable. Open it and check its status."); BridgeAccess.STOPPED }

    fun requestPermission() {
        if (refresh() == BridgeAccess.PERMISSION) runCatching { Shizuku.requestPermission(121) }
    }

    suspend fun service(): IRamService = withContext(Dispatchers.Main.immediate) {
        check(refresh() == BridgeAccess.READY) { "Start Shizuku and grant StayOpen access first" }
        remote?.takeIf { it.asBinder().isBinderAlive }?.let { return@withContext it }
        if (pending?.isActive != true) pending = CompletableDeferred()
        if (!binding) {
            binding = true
            val connection = connection(++generation, pending!!)
            activeConnection = connection
            try { Shizuku.bindUserService(args, connection) }
            catch (e: Exception) { binding = false; pending?.completeExceptionally(e) }
        }
        try { withTimeout(15_000) { pending!!.await() } }
        catch (e: Exception) { release(); throw e }
    }

    fun release() {
        generation++
        val connection = activeConnection
        activeConnection = null
        pending?.cancel(); pending = null; remote = null
        if (connection != null && Shizuku.pingBinder()) runCatching { Shizuku.unbindUserService(args, connection, true) }
        binding = false
    }
}
