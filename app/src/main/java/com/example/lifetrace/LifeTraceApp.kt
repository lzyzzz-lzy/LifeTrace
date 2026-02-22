package com.example.lifetrace

import android.app.Application
import com.amap.api.maps.MapsInitializer

/**
 * 全局 Application 入口
 * 作用：在 App 刚启动还没显示界面时，先告诉高德“用户同意隐私协议了”，
 * 否则高德 SDK 会拒绝工作。
 */
class LifeTraceApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // 隐私合规校验 (必须在地图加载前调用)
        // context, isShowPrivacyMessage, isAgreePrivacyMode
        MapsInitializer.updatePrivacyShow(this, true, true)
        MapsInitializer.updatePrivacyAgree(this, true)
    }
}