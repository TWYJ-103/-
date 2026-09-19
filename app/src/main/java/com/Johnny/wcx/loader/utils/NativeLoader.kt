package com.Johnny.wcx.loader.utils

import android.annotation.SuppressLint
import android.content.Context
import com.tencent.mmkv.MMKV
import com.Johnny.wcx.preferences.WePrefs
import com.Johnny.wcx.utils.fs.createDirsSafe
import java.io.File
import kotlin.io.path.div
import kotlin.io.path.exists

object NativeLoader {

    private val nativeLoadLock = Any()
    private var zygiskPayload: ZygiskNativePayload? = null
    private var zygiskNativeLibraries: Map<String, File> = emptyMap()
    private var nativeLibrariesLoaded = false

    @JvmStatic
    fun configureZygiskPayload(apkPath: String, dataDir: String) = synchronized(nativeLoadLock) {
        check(!nativeLibrariesLoaded) { "native libraries were already loaded" }
        val apk = File(apkPath)
        require(apk.isFile && apk.canRead()) { "Zygisk payload APK is unreadable: $apkPath" }
        val appDataDir = File(dataDir)
        require(appDataDir.isDirectory) { "Zygisk app data directory is unavailable: $dataDir" }
        zygiskPayload = ZygiskNativePayload(apk, appDataDir)
    }

    fun init(hostCtx: Context) {
        val libLoader = synchronized(nativeLoadLock) {
            ensureNativeLibrariesLoaded()
            mmkvLibLoader()
        }
        val mmkvDir = hostCtx.filesDir.toPath() / "mmkv"
        if (!mmkvDir.exists()) {
            mmkvDir.createDirsSafe()
        }

        MMKV.initialize(hostCtx, mmkvDir.toString(), libLoader)
        MMKV.mmkvWithID(WePrefs.PREFS_NAME, MMKV.MULTI_PROCESS_MODE)
    }

    private fun ensureNativeLibrariesLoaded() {
        if (nativeLibrariesLoaded) return

        val payload = zygiskPayload
        if (payload == null) {
            System.loadLibrary("dexkit")
            System.loadLibrary("wekit_native")
        } else {
            zygiskNativeLibraries = payload.loadLibraries()
        }
        nativeLibrariesLoaded = true
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    private fun mmkvLibLoader(): MMKV.LibLoader = if (zygiskPayload == null) {
        MMKV.LibLoader { name -> System.loadLibrary(name) }
    } else {
        MMKV.LibLoader { name ->
            val library = zygiskNativeLibraries[name]
            if (library != null) {
                System.load(library.absolutePath)
            } else {
                System.loadLibrary(name)
            }
        }
    }
}
