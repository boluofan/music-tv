package top.boluofan.musictv.data.api

import android.content.Context
import android.os.Build
import android.util.Log
import top.boluofan.musictv.R
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Android < 7.1.1（API 25）系统信任库缺少 ISRG Root X1（Let's Encrypt 根证书，
 * 且其兼容交叉签名链已于 2024-06 停用），导致老设备连 Let's Encrypt HTTPS 服务器
 * 握手失败。此处内置该根证书：优先走系统信任链，失败时回退用内置证书验证。
 */
object TlsCompat {
    private const val TAG = "TlsCompat"

    private var trustManager: X509TrustManager? = null
    private var sslContext: SSLContext? = null

    fun initialize(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) return
        runCatching {
            val isrgRoot = context.resources.openRawResource(R.raw.isrg_root_x1).use {
                CertificateFactory.getInstance("X.509").generateCertificate(it)
            }
            val extraKeyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                setCertificateEntry("isrg_root_x1", isrgRoot)
            }
            val composite = CompositeTrustManager(
                primary = systemTrustManager(null),
                fallback = systemTrustManager(extraKeyStore)
            )
            val ctx = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(composite), null)
            }
            trustManager = composite
            sslContext = ctx
            // Media3/Coil 等经 HttpURLConnection 的请求也走内置证书
            HttpsURLConnection.setDefaultSSLSocketFactory(ctx.socketFactory)
        }.onFailure { Log.w(TAG, "TLS 兼容初始化失败，保持系统默认", it) }
    }

    fun apply(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        val tm = trustManager ?: return builder
        val ctx = sslContext ?: return builder
        return builder.sslSocketFactory(ctx.socketFactory, tm)
    }

    private fun systemTrustManager(keyStore: KeyStore?): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(keyStore)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    private class CompositeTrustManager(
        private val primary: X509TrustManager,
        private val fallback: X509TrustManager
    ) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
            primary.checkClientTrusted(chain, authType)

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            try {
                primary.checkServerTrusted(chain, authType)
            } catch (e: CertificateException) {
                fallback.checkServerTrusted(chain, authType)
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> =
            primary.acceptedIssuers + fallback.acceptedIssuers
    }
}
