package com.hermes.bridge

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.WindowManager

/**
 * 快速启动 Activity
 * 
 * 流程：启动服务 → 立即退出
 * 用户看到的效果：瞬间闪一下，然后回到之前的 App
 * 
 * 调用方式：
 *   $PREFIX/bin/termux-open "package:com.hermes.bridge/.LaunchActivity"
 */
class LaunchActivity : Activity() {
    
    private val TAG = "HermesBridge"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 让窗口不可见
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
        
        // 立即退出，用户只看到一闪
        finish()
    }
}
