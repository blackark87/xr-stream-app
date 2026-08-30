package blackark.app.vr.dmm

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import dalvik.system.DexClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Proxy
import java.security.MessageDigest

data class DmmRightsRequest(
    val contentUri: String,
    val rightsIssuer: String,
)

data class DmmRightsResponse(
    val statusCode: Int,
    val location: String?,
) {
    val isAcquired: Boolean get() = statusCode == 200
}

interface DmmWsdListener {
    fun onRightsRequired(request: DmmRightsRequest)
    fun onReady(playbackUri: Uri)
    fun onError(error: Throwable)
}

/**
 * Reflection bridge to the WSD runtime provisioned from an authorized APK/SDK.
 *
 * WSD performs the real rights check and decrypts byte ranges into its loopback HTTP server. This
 * class never handles a content key and never bypasses a failed or expired license.
 */
class DmmWsdRuntime(
    context: Context,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var classLoader: DexClassLoader? = null
    private var facade: Any? = null
    private var sourceUri: Uri? = null
    private var listener: DmmWsdListener? = null
    private var rightsSession: Any? = null

    val isProvisioned: Boolean
        get() = runCatching {
            appContext.assets.open(RUNTIME_ASSET).close()
            File(appContext.applicationInfo.nativeLibraryDir, "libwsdnat.so").isFile &&
                File(appContext.applicationInfo.nativeLibraryDir, "libwsdprtn.so").isFile
        }.getOrDefault(false)

    @Synchronized
    fun open(uri: Uri, listener: DmmWsdListener) {
        try {
            closeFacade()
            this.listener = listener
            sourceUri = uri
            val loader = requireClassLoader()
            val sinkClass = loader.loadClass(WSD_SINK_CLASS)
            val sink = Proxy.newProxyInstance(loader, arrayOf(sinkClass)) { proxy, method, args ->
                when (method.name) {
                    "onAcquireRights" -> {
                        val startParam = requireNotNull(args?.firstOrNull())
                        val request = DmmRightsRequest(
                            contentUri = startParam.javaClass.getField("contentUri")
                                .get(startParam) as String,
                            rightsIssuer = startParam.javaClass.getField("rightsIssuer")
                                .get(startParam) as String,
                        )
                        dispatch { this.listener?.onRightsRequired(request) }
                        null
                    }

                    "onRightsChecked" -> {
                        val playbackUri = args?.firstOrNull() as Uri
                        dispatch { this.listener?.onReady(playbackUri) }
                        null
                    }

                    "onError" -> {
                        val error = args?.firstOrNull() as? Throwable
                            ?: IllegalStateException("WSD reported an unknown error.")
                        dispatch { this.listener?.onError(error) }
                        null
                    }

                    "toString" -> "DmmWsdSink"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.firstOrNull()
                    else -> null
                }
            }
            val interactionClass = loader.loadClass(WSD_INTERACTION_CLASS)
            facade = interactionClass
                .getMethod("newFacade", Context::class.java, sinkClass)
                .invoke(null, appContext, sink)
            invokeOpen(uri)
        } catch (error: Throwable) {
            dispatch { listener.onError(error.unwrapReflection()) }
        }
    }

    suspend fun startRights(request: DmmRightsRequest): DmmRightsResponse =
        withContext(Dispatchers.IO) {
            val loader = requireClassLoader()
            val sessionClass = loader.loadClass(WSD_RIGHTS_SESSION_CLASS)
            val session = sessionClass.getConstructor(Context::class.java).newInstance(appContext)
            rightsSession = session
            sessionClass.getMethod("requestFirst", String::class.java)
                .invoke(session, request.rightsIssuer)
                .toRightsResponse()
        }

    fun isLicenseUrl(uri: Uri): Boolean {
        val session = rightsSession ?: return false
        return runCatching {
            session.javaClass.getMethod("isLicenseUrl", Uri::class.java)
                .invoke(session, uri) as Boolean
        }.getOrDefault(false)
    }

    suspend fun submitLicenseUrl(url: String): DmmRightsResponse = withContext(Dispatchers.IO) {
        val session = checkNotNull(rightsSession) { "No WSD rights session is active." }
        session.javaClass.getMethod("requestNext", String::class.java)
            .invoke(session, url)
            .toRightsResponse()
    }

    @Synchronized
    fun resumeAfterRights() {
        val uri = checkNotNull(sourceUri) { "No WSD content is open." }
        try {
            closeFacade(keepListener = true)
            val currentListener = checkNotNull(listener)
            open(uri, currentListener)
        } catch (error: Throwable) {
            dispatch { listener?.onError(error.unwrapReflection()) }
        }
    }

    @Synchronized
    override fun close() {
        closeFacade()
        listener = null
        sourceUri = null
        rightsSession = null
    }

    private fun invokeOpen(uri: Uri) {
        val loader = requireClassLoader()
        val currentFacade = checkNotNull(facade)
        val openParamsClass = loader.loadClass(WSD_OPEN_PARAMS_CLASS)
        val standard = openParamsClass.getField("standard").get(null)
        val allowNonDrm = openParamsClass
            .getMethod("renew_allowNonDrmInput", Boolean::class.javaPrimitiveType)
            .invoke(standard, true)
        currentFacade.javaClass
            .getMethod("openAsync", Uri::class.java, openParamsClass)
            .invoke(currentFacade, uri, allowNonDrm)
    }

    private fun closeFacade(keepListener: Boolean = false) {
        val current = facade
        facade = null
        if (current != null) {
            runCatching { current.javaClass.getMethod("closeSilently").invoke(current) }
        }
        if (!keepListener) listener = null
    }

    @Synchronized
    private fun requireClassLoader(): DexClassLoader {
        classLoader?.let { return it }
        check(isProvisioned) {
            "WSD runtime is missing. Run scripts/provision-dmm-runtime.sh with your authorized APK files."
        }
        val runtimeDir = File(appContext.codeCacheDir, "dmm-wsd").apply { mkdirs() }
        val dexFile = File(runtimeDir, "wsd-runtime.dex")
        val assetBytes = appContext.assets.open(RUNTIME_ASSET).use { it.readBytes() }
        val needsCopy = !dexFile.isFile || !MessageDigest.isEqual(
            dexFile.inputStream().use { MessageDigest.getInstance("SHA-256").digest(it.readBytes()) },
            MessageDigest.getInstance("SHA-256").digest(assetBytes),
        )
        if (needsCopy) {
            val temporary = File(runtimeDir, "wsd-runtime.dex.tmp")
            FileOutputStream(temporary).use { it.write(assetBytes) }
            check(temporary.setReadOnly()) { "Unable to make the WSD dex read-only." }
            if (dexFile.exists()) check(dexFile.delete()) { "Unable to replace the WSD dex." }
            check(temporary.renameTo(dexFile)) { "Unable to install the WSD dex." }
        } else if (dexFile.canWrite()) {
            check(dexFile.setReadOnly()) { "Unable to make the WSD dex read-only." }
        }
        return DexClassLoader(
            dexFile.absolutePath,
            runtimeDir.absolutePath,
            appContext.applicationInfo.nativeLibraryDir,
            appContext.classLoader,
        ).also { loader ->
            loader.loadClass(WSD_INTERACTION_CLASS)
            classLoader = loader
        }
    }

    private fun Any.toRightsResponse(): DmmRightsResponse = DmmRightsResponse(
        statusCode = javaClass.getField("httpStatusCode").getInt(this),
        location = javaClass.getField("httpLocation").get(this) as? String,
    )

    private fun Throwable.unwrapReflection(): Throwable =
        (this as? java.lang.reflect.InvocationTargetException)?.targetException ?: this

    private fun dispatch(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    companion object {
        private const val RUNTIME_ASSET = "dmm/wsd-runtime.dex"
        private const val WSD_INTERACTION_CLASS =
            "jp.co.webstream.drm.android.pub.WsdVideoInteraction"
        private const val WSD_SINK_CLASS =
            "jp.co.webstream.drm.android.pub.WsdVideoInteraction\$Sink"
        private const val WSD_OPEN_PARAMS_CLASS =
            "jp.co.webstream.drm.android.pub.WsdVideoInteraction\$OpenParams"
        private const val WSD_RIGHTS_SESSION_CLASS =
            "jp.co.webstream.drm.android.pub.WsdRightsAcquiringSession"
    }
}
