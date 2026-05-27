package com.hermes.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 开机自启接收器
 * 手机重启后自动启动 HermesBridge HTTP 服务
 */
class BootReceiver : BroadcastReceiver() {
    
    private val TAG = "HermesBridge"
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "android.intent.action.LOCKED_BOOT_COMPLETED") {
            
            Log.d(TAG, "Boot completed (${intent.action})! Starting HermesBridge service...")
            
            // 使用 HttpService.start() 静态方法启动服务
            HttpService.start(context, 8889)
            
            Log.d(TAG, "HermesBridge service start command sent on boot")
        }
    }
}
