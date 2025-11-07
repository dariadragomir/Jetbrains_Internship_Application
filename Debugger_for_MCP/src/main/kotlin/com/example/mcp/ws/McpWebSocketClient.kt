package com.example.mcp.ws

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.*
import okio.ByteString
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class McpWebSocketClient(
    private val serverUrl: String,
    private val onConnected: (McpWebSocketClient) -> Unit
) : WebSocketListener() {
    private val client = OkHttpClient()
    private var ws: WebSocket? = null
    private val nextId = AtomicLong(1)
    private val pending = ConcurrentHashMap<Long, (JsonElement?, String?) -> Unit>()

    fun connect(onError: (String) -> Unit) {
        val request = Request.Builder()
            .url(serverUrl)
            .build()
        ws = client.newWebSocket(request, this)
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        onConnected(this)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        try {
            val json = Json.parse(text)
            val obj = json as? JsonObject ?: return
            val id = (obj["id"] as? JsonPrimitive)?.longOrNull
            if (id != null) {
                val callback = pending.remove(id)
                if (callback != null) {
                    val error = obj["error"]?.toString()
                    val result = obj["result"]
                    callback(result, if (obj.containsKey("error")) error else null)
                }
            }
        } catch (_: Exception) {
        }
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        //ignore binary
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        //notify all pending with error
        val err = t.message ?: "connection failure"
        pending.values.forEach { it(null, err) }
        pending.clear()
    }

    fun listTools(callback: (List<com.example.mcp.ui.McpTool>, String?) -> Unit) {
        sendRequest("tools/list", buildJsonObject { }, { result, error ->
            if (error != null) {
                callback(emptyList(), error)
                return@sendRequest
            }
            val toolsJson = (result as? JsonObject)?.get("tools") ?: result
            val tools = mutableListOf<com.example.mcp.ui.McpTool>()
            try {
                (toolsJson as? JsonArray)?.forEach { el ->
                    val obj = el.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.content ?: return@forEach
                    val desc = obj["description"]?.jsonPrimitive?.content
                    val inputSchema = obj["inputSchema"] ?: JsonNull
                    val keys = extractSchemaKeys(inputSchema)
                    tools.add(com.example.mcp.ui.McpTool(name, desc, keys))
                }
                callback(tools, null)
            } catch (e: Exception) {
                callback(emptyList(), e.message ?: "parse error")
            }
        })
    }

    fun callTool(toolName: String, params: Map<String, Any?>, callback: (String?, String?) -> Unit) {
        val paramsJson = Json.fromMap(params)
        val req = buildJsonObject {
            put("name", JsonPrimitive(toolName))
            put("arguments", paramsJson)
        }
        sendRequest("tools/call", req) { result, error ->
            if (error != null) {
                callback(null, error)
                return@sendRequest
            }
            callback(result?.toString(), null)
        }
    }

    private fun sendRequest(method: String, params: JsonObject, callback: (JsonElement?, String?) -> Unit) {
        val id = nextId.getAndIncrement()
        val payload = buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0"))
            put("id", JsonPrimitive(id))
            put("method", JsonPrimitive(method))
            put("params", params)
        }.toString()
        pending[id] = callback
        ws?.send(payload)
    }
}

object Json {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    fun parse(text: String): JsonElement = json.parseToJsonElement(text)

    fun parseObject(text: String): Map<String, Any?> {
        val el = parse(text)
        if (el !is JsonObject) error("Expected JSON object")
        return toMap(el)
    }

    fun fromMap(map: Map<String, Any?>): JsonElement {
        val content = map.mapValues { (_, v) -> toJson(v) }
        return JsonObject(content)
    }

    private fun toJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Map<*, *> -> {
            val m = value.entries.associate { (k, v) -> (k as String) to toJson(v) }
            JsonObject(m)
        }
        is List<*> -> JsonArray(value.map { toJson(it) })
        else -> JsonPrimitive(value.toString())
    }

    private fun toMap(obj: JsonObject): Map<String, Any?> = obj.mapValues { (_, v) -> fromJson(v) }

    private fun fromJson(el: JsonElement): Any? = when (el) {
        is JsonNull -> null
        is JsonPrimitive -> when {
            el.isString -> el.content
            el.booleanOrNull != null -> el.boolean
            el.longOrNull != null -> el.long
            el.doubleOrNull != null -> el.double
            else -> el.content
        }
        is JsonObject -> toMap(el)
        is JsonArray -> el.map { fromJson(it) }
        else -> null
    }
}

private fun extractSchemaKeys(schemaEl: JsonElement): List<String> {
    //extract json-schema properties keys
    return try {
        val props = schemaEl.jsonObject["properties"]?.jsonObject ?: return emptyList()
        props.keys.toList()
    } catch (_: Exception) {
        emptyList()
    }
}

