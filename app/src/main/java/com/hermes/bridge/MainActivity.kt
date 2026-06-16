package com.hermes.bridge

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var chipStatus: Chip
    private lateinit var tvPort: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var rvRecentFiles: RecyclerView
    private lateinit var tvEmptyFiles: TextView
    private val fileAdapter = FileAdapter()

    companion object {
        const val PORT = 8889
        const val PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Toolbar menu
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_github -> {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/lantianhcgp/HermesBridge")))
                    } catch (_: Exception) {}
                    true
                }
                R.id.action_about -> {
                    Toast.makeText(this,
                        "HermesBridge v2.5\nAI Agent Android Bridge\nPort: $PORT",
                        Toast.LENGTH_LONG).show()
                    true
                }
                else -> false
            }
        }

        chipStatus = findViewById(R.id.chipStatus)
        tvPort = findViewById(R.id.tvPort)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)

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

        // ===== 文件列表 =====
        rvRecentFiles = findViewById(R.id.rvRecentFiles)
        tvEmptyFiles = findViewById(R.id.tvEmptyFiles)
        rvRecentFiles.layoutManager = LinearLayoutManager(this)
        rvRecentFiles.adapter = fileAdapter
        refreshFiles()

        // ===== 处理通知点击（SHOW_FILE extra） =====
        handleIncomingIntent(intent)

        // Auto-start if permissions are granted
        if (checkPermissions()) {
            startHttpService()
            autoFinishWhenReady()
        } else {
            updateUI()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        val fileName = intent.getStringExtra("SHOW_FILE")
        if (!fileName.isNullOrEmpty()) {
            showFileReceivedDialog(fileName)
        }
    }

    /**
     * 文件接收完成弹窗
     */
    private fun showFileReceivedDialog(fileName: String) {
        // 查找文件
        val bridgeDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "HermesBridge"
        )
        val file = findFileByName(bridgeDir, fileName)

        val dialogView = layoutInflater.inflate(R.layout.dialog_file_received, null)

        val tvFileName = dialogView.findViewById<TextView>(R.id.tvFileName)
        val tvFileSize = dialogView.findViewById<TextView>(R.id.tvFileSize)
        val tvFilePath = dialogView.findViewById<TextView>(R.id.tvFilePath)
        val ivFileIcon = dialogView.findViewById<TextView>(R.id.ivFileIcon)

        tvFileName.text = fileName
        if (file != null) {
            tvFileSize.text = formatFileSize(file.length())
            tvFilePath.text = file.absolutePath
            ivFileIcon.text = getFileIcon(fileName.substringAfterLast('.', ""))
        } else {
            tvFileSize.text = "未知大小"
            tvFilePath.text = "文件位置: Downloads/HermesBridge/"
            ivFileIcon.text = "📄"
        }

        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)

        val dialog = builder.create()

        // 打开按钮
        dialogView.findViewById<Button>(R.id.btnOpen).setOnClickListener {
            if (file != null) {
                openFile(file)
            }
            dialog.dismiss()
        }

        // 在列表中查看按钮
        dialogView.findViewById<Button>(R.id.btnShowInList).setOnClickListener {
            refreshFiles()
            dialog.dismiss()
        }

        // 删除按钮
        dialogView.findViewById<Button>(R.id.btnDelete).setOnClickListener {
            if (file != null && file.exists()) {
                file.delete()
                refreshFiles()
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        // 取消按钮
        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun findFileByName(dir: File, name: String): File? {
        if (!dir.exists()) return null
        return dir.listFiles()?.find { it.name.equals(name, ignoreCase = true) }
            ?: dir.listFiles()?.find { it.nameWithoutExtension.equals(name.split('.').first(), ignoreCase = true) }
    }

    private fun openFile(file: File) {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 用 MediaStore URI
            try {
                val resolver = contentResolver
                val cursor = resolver.query(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    arrayOf(android.provider.MediaStore.Downloads._ID),
                    "${android.provider.MediaStore.Downloads.DISPLAY_NAME}=?",
                    arrayOf(file.name),
                    null
                )
                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        val id = c.getLong(c.getColumnIndexOrThrow(android.provider.MediaStore.Downloads._ID))
                        android.net.Uri.parse(
                            "content://media/external/downloads/$id"
                        )
                    } else null
                }
            } catch (e: Exception) {
                null
            }
        } else {
            androidx.core.content.FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file
            )
        }

        uri?.let {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(it, contentResolver.getType(it) ?: "application/octet-stream")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "无法打开文件", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(this, "无法找到文件", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
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
    }

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

        if (running) {
            chipStatus.text = "运行中"
            chipStatus.setChipBackgroundColorResource(R.color.md_theme_light_primaryContainer)
            chipStatus.setTextColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOnPrimaryContainer, 0xFF00201B.toInt()))
            btnStart.isEnabled = false
            btnStop.isEnabled = true
        } else {
            chipStatus.text = "已停止"
            chipStatus.setChipBackgroundColorResource(R.color.md_theme_light_errorContainer)
            chipStatus.setTextColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOnErrorContainer, 0xFF410002.toInt()))
            btnStart.isEnabled = true
            btnStop.isEnabled = false
        }
    }

    // ===== 文件列表 =====
    private fun refreshFiles() {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val bridgeDir = File(dir, "HermesBridge")
        val files = if (bridgeDir.exists() && bridgeDir.isDirectory) {
            bridgeDir.listFiles { _, name ->
                !name.startsWith(".")
            }?.filter { it.isFile }?.sortedByDescending { it.lastModified() }
                ?.take(50) ?: emptyList()
        } else {
            emptyList()
        }

        runOnUiThread {
            if (files.isEmpty()) {
                rvRecentFiles.visibility = View.GONE
                tvEmptyFiles.visibility = View.VISIBLE
            } else {
                rvRecentFiles.visibility = View.VISIBLE
                tvEmptyFiles.visibility = View.GONE
                fileAdapter.submitList(files)
            }
        }
    }

    private inner class FileAdapter :
        RecyclerView.Adapter<FileAdapter.ViewHolder>() {

        private var files: List<File> = emptyList()

        fun submitList(newFiles: List<File>) {
            files = newFiles
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_file, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = files[position]
            holder.bind(file)
        }

        override fun getItemCount() = files.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvIcon: TextView = itemView.findViewById(R.id.tvFileIcon)
            private val tvName: TextView = itemView.findViewById(R.id.tvFileName)
            private val tvInfo: TextView = itemView.findViewById(R.id.tvFileInfo)
            private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

            fun bind(file: File) {
                val icon = getFileIcon(file.extension)
                tvIcon.text = icon

                tvName.text = file.nameWithoutExtension
                val size = when {
                    file.length() < 1024 -> "${file.length()} B"
                    file.length() < 1024 * 1024 -> String.format("%.1f KB", file.length().toDouble() / 1024)
                    else -> String.format("%.1f MB", file.length().toDouble() / (1024 * 1024))
                }
                val date = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                    .format(file.lastModified())
                tvInfo.text = "$size · $date"

                // 点击打开文件
                itemView.setOnClickListener {
                    val ctx = itemView.context
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            // Android 10+: 尝试用 MediaStore URI 或 FileProvider
                            val fileProviderUri = androidx.core.content.FileProvider.getUriForFile(
                                ctx,
                                "${ctx.packageName}.fileprovider",
                                file
                            )
                            setDataAndType(fileProviderUri, getMimeType(file.name))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        } else {
                            val contentUri = Uri.fromFile(file)
                            setDataAndType(contentUri, getMimeType(file.name))
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                    try {
                        ctx.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "无法打开文件: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                // 删除文件
                btnDelete.setOnClickListener {
                    file.delete()
                    refreshFiles()
                }
            }
        }
    }

    private fun getFileIcon(extension: String): String {
        return when (extension.lowercase()) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp" -> "🖼️"
            "pdf" -> "📄"
            "doc", "docx" -> "📝"
            "xls", "xlsx", "csv" -> "📊"
            "ppt", "pptx" -> "📑"
            "mp4", "mkv", "avi", "mov" -> "🎬"
            "mp3", "wav", "flac", "aac" -> "🎵"
            "zip", "rar", "7z", "tar", "gz" -> "📦"
            "apk" -> "📱"
            "txt", "json", "xml", "html", "java", "kt", "py" -> "📃"
            else -> "📄"
        }
    }

    private fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "ppt" -> "application/vnd.ms-powerpoint"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "doc" -> "application/msword"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "xls" -> "application/vnd.ms-excel"
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "aac" -> "audio/aac"
            "zip" -> "application/zip"
            "rar" -> "application/vnd.rar"
            "7z" -> "application/x-7z-compressed"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            "txt" -> "text/plain"
            "json" -> "application/json"
            "html", "htm" -> "text/html"
            "csv" -> "text/csv"
            "xml" -> "application/xml"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }
}
