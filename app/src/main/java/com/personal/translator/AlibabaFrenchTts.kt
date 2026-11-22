package com.personal.translator

import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** Minimal RFC 6455 client for the official Qwen3-TTS realtime WebSocket API. */
class AlibabaFrenchTts(private val apiKey: String, private val international: Boolean = false) {
    @Volatile private var activeSocket: SSLSocket? = null
    fun cancel() { runCatching { activeSocket?.close() } }

    fun synthesize(text: String): ByteArray {
        require(text.isNotBlank()) { "法语语音文本为空。" }
        val socket = SSLSocketFactory.getDefault().createSocket() as SSLSocket
        activeSocket = socket
        try {
            val host = if (international) "dashscope-intl.aliyuncs.com" else "dashscope.aliyuncs.com"
            socket.sslParameters = socket.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS"; serverNames = listOf(javax.net.ssl.SNIHostName(host)) }
            socket.connect(InetSocketAddress(host, 443), 15_000)
            socket.soTimeout = 60_000
            socket.startHandshake()
            val input = BufferedInputStream(socket.inputStream)
            val output = BufferedOutputStream(socket.outputStream)
            val wsKey = Base64.getEncoder().encodeToString(ByteArray(16).also { SecureRandom().nextBytes(it) })
            val path = "/api-ws/v1/realtime?model=qwen3-tts-flash-realtime"
            output.write(("GET $path HTTP/1.1\r\n" +
                "Host: $host\r\n" +
                "Upgrade: websocket\r\nConnection: Upgrade\r\n" +
                "Sec-WebSocket-Version: 13\r\nSec-WebSocket-Key: $wsKey\r\n" +
                "Authorization: Bearer $apiKey\r\n\r\n").toByteArray(Charsets.US_ASCII))
            output.flush()
            val headers = readHeaders(input)
            if (!headers.startsWith("HTTP/1.1 101")) {
                val status = headers.lineSequence().firstOrNull().orEmpty()
                throw IOException("阿里云百炼 WebSocket 握手失败：$status。请检查百炼 API Key 与地域。")
            }
            val expectedAccept = Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-1").digest((wsKey + GUID).toByteArray(Charsets.US_ASCII)))
            val accept = headers.lineSequence().firstOrNull { it.startsWith("Sec-WebSocket-Accept:", true) }
                ?.substringAfter(':')?.trim()
            if (accept != expectedAccept) throw IOException("阿里云语音连接校验失败，请重试。")

            fun send(event: JSONObject) {
                event.put("event_id", "event_${UUID.randomUUID().toString().replace("-", "")}")
                writeFrame(output, 0x1, event.toString().toByteArray(Charsets.UTF_8))
            }
            fun receiveEvent(): JSONObject {
                val bytes = readTextMessage(input, output)
                return try { JSONObject(bytes.toString(Charsets.UTF_8)) }
                catch (e: Exception) { throw IOException("阿里云语音服务返回了无法识别的事件。", e) }
            }
            fun failIfError(event: JSONObject) {
                if (event.optString("type") == "error") {
                    val detail = event.optJSONObject("error")?.optString("message").orEmpty()
                    throw IOException("阿里云法语语音合成失败：${detail.ifBlank { "服务返回错误" }}")
                }
            }

            awaitEvent(::receiveEvent, "session.created", ::failIfError)
            send(JSONObject().put("type", "session.update").put("session", JSONObject()
                .put("mode", "commit").put("voice", "Cherry").put("language_type", "French")
                .put("response_format", "pcm").put("sample_rate", SAMPLE_RATE)))
            awaitEvent(::receiveEvent, "session.updated", ::failIfError)
            send(JSONObject().put("type", "input_text_buffer.append").put("text", text))
            send(JSONObject().put("type", "input_text_buffer.commit"))

            val pcm = ByteArrayOutputStream()
            var done = false
            while (!done) {
                val event = receiveEvent()
                failIfError(event)
                when (event.optString("type")) {
                    "response.audio.delta" -> {
                        val chunk = try { Base64.getDecoder().decode(event.getString("delta")) }
                        catch (e: Exception) { throw IOException("阿里云语音片段解码失败。", e) }
                        if (pcm.size() + chunk.size > 24 * 1024 * 1024) throw IOException("法语语音片段过长。")
                        pcm.write(chunk)
                    }
                    "response.done" -> { check(event.optJSONObject("response")?.optString("status") == "completed") { "百炼未完成语音合成。" }; done = true }
                }
            }
            if (pcm.size() < 2 || pcm.size() % 2 != 0) throw IOException("阿里云没有返回完整的法语语音。")
            runCatching { send(JSONObject().put("type", "session.finish")) }
            return pcm.toByteArray()
        } finally {
            runCatching { socket.close() }
            activeSocket = null
        }
    }

    private fun awaitEvent(receive: () -> JSONObject, expected: String, checkError: (JSONObject) -> Unit) {
        repeat(12) {
            val event = receive()
            checkError(event)
            if (event.optString("type") == expected) return
        }
        throw IOException("等待阿里云语音会话响应超时。")
    }

    private fun readHeaders(input: BufferedInputStream): String {
        val out = ByteArrayOutputStream()
        var matched = 0
        while (out.size() < 16 * 1024) {
            val value = input.read()
            if (value < 0) throw IOException("阿里云语音服务提前关闭了连接。")
            out.write(value)
            matched = when {
                matched == 0 && value == 13 -> 1
                matched == 1 && value == 10 -> 2
                matched == 2 && value == 13 -> 3
                matched == 3 && value == 10 -> 4
                value == 13 -> 1
                else -> 0
            }
            if (matched == 4) return out.toString(Charsets.US_ASCII.name())
        }
        throw IOException("阿里云语音连接响应头过长。")
    }

    private fun readTextMessage(input: BufferedInputStream, output: BufferedOutputStream): ByteArray {
        val message = ByteArrayOutputStream()
        var started = false
        while (true) {
            val first = input.read()
            val second = input.read()
            if (first < 0 || second < 0) throw IOException("阿里云语音连接已断开。")
            val opcode = first and 0x0f
            val final = first and 0x80 != 0
            var size = (second and 0x7f).toLong()
            if (size == 126L) size = ((readByte(input) shl 8) or readByte(input)).toLong()
            else if (size == 127L) {
                size = 0
                repeat(8) { size = (size shl 8) or readByte(input).toLong() }
            }
            if (size > 8 * 1024 * 1024) throw IOException("阿里云语音事件超出大小限制。")
            val mask = if (second and 0x80 != 0) ByteArray(4).also { readFully(input, it) } else null
            val payload = ByteArray(size.toInt()).also { readFully(input, it) }
            if (mask != null) for (i in payload.indices) payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
            when (opcode) {
                0x8 -> throw IOException("阿里云结束了法语语音会话。")
                0x9 -> writeFrame(output, 0xA, payload)
                0x1 -> { message.write(payload); started = true }
                0x0 -> if (started) message.write(payload)
                0x2 -> throw IOException("阿里云返回了未预期的二进制事件。")
            }
            if (final && started && (opcode == 0x1 || opcode == 0x0)) return message.toByteArray()
            if (final && opcode == 0x9) continue
        }
    }

    private fun writeFrame(output: BufferedOutputStream, opcode: Int, payload: ByteArray) {
        output.write(0x80 or opcode)
        when {
            payload.size < 126 -> output.write(0x80 or payload.size)
            payload.size <= 0xffff -> { output.write(0x80 or 126); output.write(payload.size ushr 8); output.write(payload.size) }
            else -> {
                output.write(0x80 or 127)
                for (shift in 56 downTo 0 step 8) output.write((payload.size.toLong() ushr shift).toInt())
            }
        }
        val mask = ByteArray(4).also { SecureRandom().nextBytes(it) }
        output.write(mask)
        for (i in payload.indices) output.write(payload[i].toInt() xor mask[i % 4].toInt())
        output.flush()
    }

    private fun readByte(input: BufferedInputStream) = input.read().also { if (it < 0) throw IOException("语音事件数据不完整。") }
    private fun readFully(input: BufferedInputStream, destination: ByteArray) {
        var offset = 0
        while (offset < destination.size) {
            val count = input.read(destination, offset, destination.size - offset)
            if (count < 0) throw IOException("语音事件数据不完整。")
            offset += count
        }
    }

    companion object {
        private const val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        const val SAMPLE_RATE = 24_000
    }
}
