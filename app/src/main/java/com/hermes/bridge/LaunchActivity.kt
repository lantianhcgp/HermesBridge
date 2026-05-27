package com.hermes.bridge

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

/**
 * 透明启动 Activity
 * 
 * 用途：被 termux-open 调起后，立即启动 HttpService，然后自动关闭自己。
 * 用户看到的效果：无感 —— 前台 App 不变，HermesBridge 在后台运行。
 * 
 * 调用方式：
 *   termux-open "package:com.hermes.bridge/.LaunchActivity"
 */
class LaunchActivity : AppCompatActivity() {
    
    private val TAG = "HermesBridge"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 不 setContentView — 完全透明，用户看不到任何 UI
        
        val port = intent?.getIntExtra("port", 8889) ?: 8889
        
        // 启动前台服务
        if (!HttpService.isRunning) {
            Log.d(TAG, "LaunchActivity: Starting HttpService on port $port")
            HttpService.start(this, port)
        } else {
            Log.d(TAG, "LaunchActivity: HttpService already running, skipping start")
        }
        
        // 立即关闭自己，回到之前的 App
        finish()
    }
}
