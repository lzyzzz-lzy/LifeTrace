package com.example.lifetrace

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.amap.api.maps.MapsInitializer
import com.example.lifetrace.ui.map.LifeTraceMap
import com.example.lifetrace.ui.navigation.AppNavHost
import com.example.lifetrace.ui.theme.MyApplicationTheme
import java.security.MessageDigest
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 打印 SHA1 (运行后去 Logcat 搜索 "LifeTrace_SHA1")
        val currentSHA1 = getSHA1(this)
        Log.d("LifeTrace_SHA1", "当前 App 真实的 SHA1 是: $currentSHA1")

        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                AppNavHost()
            }
        }
    }

    /**
     * 兼容性获取 SHA1 函数
     * 修复了 signatures 废弃报错问题
     */
    private fun getSHA1(context: Context): String? {
        try {
            val packageName = context.packageName
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Android 9.0 及以上版本
                val info = context.packageManager.getPackageInfo(
                    packageName, PackageManager.GET_SIGNING_CERTIFICATES
                )
                info.signingInfo?.apkContentsSigners
            } else {
                // Android 9.0 以下版本
                @Suppress("DEPRECATION")
                val info = context.packageManager.getPackageInfo(
                    packageName, PackageManager.GET_SIGNATURES
                )
                info.signatures
            }

            val cert = signatures?.get(0)?.toByteArray()
            if (cert != null) {
                val md = MessageDigest.getInstance("SHA1")
                val publicKey = md.digest(cert)
                val hexString = StringBuilder()
                for (i in publicKey.indices) {
                    val appendString = Integer.toHexString(0xFF and publicKey[i].toInt())
                        .uppercase(Locale.US)
                    if (appendString.length == 1) hexString.append("0")
                    hexString.append(appendString)
                    if (i < publicKey.size - 1) hexString.append(":")
                }
                return hexString.toString()
            }
        } catch (e: Exception) {
            Log.e("LifeTrace", "获取SHA1解析失败", e)
        }
        return "获取失败"
    }
}