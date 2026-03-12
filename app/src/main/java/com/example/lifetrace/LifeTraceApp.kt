package com.example.lifetrace

import android.app.Application
import com.amap.api.maps.MapsInitializer
import com.example.lifetrace.service.CaptionService

/**
 * 全局 Application 入口
 * 作用：在 App 刚启动还没显示界面时，先告诉高德”用户同意隐私协议了”，
 * 否则高德 SDK 会拒绝工作。
 */
class LifeTraceApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // 隐私合规校验 (必须在地图加载前调用)
        // context, isShowPrivacyMessage, isAgreePrivacyMode
        MapsInitializer.updatePrivacyShow(this, true, true)
        MapsInitializer.updatePrivacyAgree(this, true)

        // 初始化 AI 文案服务（如果未配置则使用默认 API Key）
        initCaptionServiceIfNeeded()
    }

    /**
     * 初始化文案服务 API Key
     */
    private fun initCaptionServiceIfNeeded() {
        val captionService = CaptionService(this)
        if (!captionService.isApiConfigured()) {
            // 使用 BuildConfig 中的默认配置
            val apiKey = BuildConfig.DOUBAO_API_KEY
            val modelId = BuildConfig.DOUBAO_MODEL_ID
            if (apiKey.isNotBlank()) {
                captionService.saveApiConfig(apiKey, modelId)
            }
        }
    }
}