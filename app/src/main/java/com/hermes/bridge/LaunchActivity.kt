package com.hermes.bridge

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.WindowManager

/**
 * 透明启动 Activity
 * 
 * 用途：被 termux-open 调起后，立即启动 HttpService，然后自动关闭自己。
 * 用户看到的效果：无感 —— 前台 App 不变，HermesBridge 在后台运行。
 * 
 * 调用方式：
 *   $PREFIX/bin/termux-open "package:com.hermes.bridge/.LaunchActivity"
 */
class LaunchActivity : Activity() {
    
    private val TAG = "HermesBridge"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 让窗口完全不可见
        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        )
        
        val port = intent?.getIntExtra("port", 8889) ?: 8889
        
        // 启动前台服务
        if (!HttpService.isRunning) {
            Log.d(TAG, "LaunchActivity: Starting HttpService on port $port")
            HttpService.start(this, port)
        } else {
            Log.d(TAG, "LaunchActivity: HttpService already running, skipping start")
        }
        
        // 立即关闭，不进最近任务，无动画
        finishAndRemoveTask()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
    
    // 防止 MIUI 显示任何内容
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            finishAndRemoveTask()
        }
    }
}
