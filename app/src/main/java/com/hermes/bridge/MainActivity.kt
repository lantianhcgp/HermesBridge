package com.hermes.bridge

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors

class MainActivity : AppCompatActivity() {
    
    private lateinit var tvStatus: TextView
    private lateinit var tvPort: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var statusDot: View
    
    companion object {
        const val PORT = 8889
        const val PERMISSION_REQUEST_CODE = 100
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        tvStatus = findViewById(R.id.tvStatus)
        tvPort = findViewById(R.id.tvPort)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        statusDot = findViewById(R.id.statusDot)
        
        tvPort.text = "端口: $PORT"
        
        btnStart.setOnClickListener {
            if (checkPermissions()) {
                startHttpService()
            } else {
                requestPermissions()
            }
        }
        
        btnStop.setOnClickListener {
            stopHttpService()
        }
        
        // 检查权限，如果已全部授予则自动启动服务
        if (checkPermissions()) {
            startHttpService()
            // 启动服务后，轮询检查是否成功，成功即退出
            autoFinishWhenReady()
        } else {
            updateUI()
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateUI()
    }
    
    private fun checkPermissions(): Boolean {
        val permissions = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_CALENDAR)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.WRITE_CALENDAR)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.SEND_SMS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_CONTACTS)
        }
        
        return permissions.isEmpty()
    }
    
    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_CALENDAR)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.WRITE_CALENDAR)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.SEND_SMS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_CONTACTS)
        }
        
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                Toast.makeText(this, "所有权限已授予！服务启动中...", Toast.LENGTH_SHORT).show()
                startHttpService()
            } else {
                Toast.makeText(this, "部分权限未授予，某些功能可能无法使用", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun startHttpService() {
        HttpService.start(this, PORT)
        Toast.makeText(this, "HTTP 服务器启动中...", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 轮询检查服务是否启动成功，成功后自动退出
     * 不管是用户手动打开还是终端调起，都会自动退出
     */
    private fun autoFinishWhenReady() {
        val handler = android.os.Handler(mainLooper)
        val checkRunnable = object : Runnable {
            override fun run() {
                if (HttpService.isRunning) {
                    Toast.makeText(this@MainActivity, "服务已启动 ✅", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    handler.postDelayed(this, 200)
                }
            }
        }
        handler.postDelayed(checkRunnable, 500)
    }
    
    private fun stopHttpService() {
        HttpService.stop(this)
        btnStop.postDelayed({ updateUI() }, 500)
        Toast.makeText(this, "HTTP 服务器已停止", Toast.LENGTH_SHORT).show()
    }
    
    private fun updateUI() {
        val running = HttpService.isRunning
        
        val dotColor: Int
        if (running) {
            dotColor = 0xFF4CAF50.toInt()
            tvStatus.text = "状态: 运行中 ✅"
            tvStatus.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, 0xFF4CAF50.toInt()))
            btnStart.isEnabled = false
            btnStop.isEnabled = true
        } else {
            dotColor = 0xFFF44336.toInt()
            tvStatus.text = "状态: 已停止 ❌"
            tvStatus.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorError, 0xFFF44336.toInt()))
            btnStart.isEnabled = true
            btnStop.isEnabled = false
        }
        
        val dotDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(dotColor)
            setSize(30, 30)
        }
        statusDot.background = dotDrawable
    }
}
