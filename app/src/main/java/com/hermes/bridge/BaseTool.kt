package com.hermes.bridge

import android.content.Context
import com.google.gson.Gson
import io.ktor.server.application.*
import io.ktor.server.request.*

/**
 * BaseTool — 所有 Tool 的基类
 *
 * 提供统一的 JSON 序列化、请求解析和响应格式。
 */
abstract class BaseTool(protected val context: Context) {

    protected val gson: Gson = Gson()

    /** 成功响应 */
    protected fun ok(vararg pairs: Pair<String, Any?>): Map<String, Any?> =
        mapOf("success" to true) + pairs.toMap()

    /** 失败响应 */
    protected fun error(message: String): Map<String, Any?> =
        mapOf("success" to false, "error" to message)

    /** 解析 JSON 请求体 */
    protected suspend fun parseBody(call: ApplicationCall): Map<String, Any> {
        val bodyStr = call.receiveText()
        @Suppress("UNCHECKED_CAST")
        return gson.fromJson(bodyStr, Map::class.java) as Map<String, Any>
    }
}
