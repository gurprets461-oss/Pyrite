package dev.gurpret.pyrite.editor

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.gurpret.pyrite.python.StreamKind
import dev.gurpret.pyrite.runner.RunRequest
import dev.gurpret.pyrite.runner.RunResult
import dev.gurpret.pyrite.runner.RunSession
import dev.gurpret.pyrite.runner.Runner
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class Completion(val name: String, val type: String, val description: String)
data class Signature(val name: String, val params: List<String>, val index: Int?)
data class SyntaxIssue(val line: Int, val col: Int, val endLine: Int, val endCol: Int, val message: String)

/**
 * Talks to the analysis server (_pyrite/analysis.py) in the `:analysis` process over
 * JSON lines on its stdin/stdout. The process starts on first use and restarts after
 * dying. Request methods block, so call them off the main thread.
 */
class AnalysisClient(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val nextId = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableFuture<JSONObject>>()
    private var session: RunSession? = null       // main thread
    private val lineBuffer = StringBuilder()      // main thread

    /** Starts the server early, so jedi is warm by the first keystroke. */
    fun start() = main.post { ensureSession() }

    fun stop() = main.post { session?.stop() }

    private fun ensureSession(): RunSession {
        session?.takeIf { !it.isFinished }?.let { return it }
        lineBuffer.setLength(0)
        lateinit var started: RunSession
        started = Runner.start(app, RunRequest.Analysis, object : RunSession.Listener {
            override fun onOutput(kind: StreamKind, text: String) {
                if (kind != StreamKind.STDOUT) {
                    Log.d(TAG, text.trimEnd())
                    return
                }
                lineBuffer.append(text)
                while (true) {
                    val nl = lineBuffer.indexOf("\n")
                    if (nl < 0) break
                    val line = lineBuffer.substring(0, nl)
                    lineBuffer.delete(0, nl + 1)
                    deliver(line)
                }
            }

            override fun onFinished(result: RunResult) {
                Log.i(TAG, "analysis server ended: $result")
                if (session === started) session = null
                val failure = IOException("analysis server ended: $result")
                pending.values.forEach { it.completeExceptionally(failure) }
                pending.clear()
            }
        })
        session = started
        return started
    }

    private fun deliver(line: String) {
        val obj = runCatching { JSONObject(line) }.getOrNull() ?: return
        pending.remove(obj.optInt("id", -1))?.complete(obj)
    }

    private fun request(type: String, fields: Map<String, Any?>, timeoutMs: Long): JSONObject? {
        check(Looper.myLooper() != Looper.getMainLooper()) { "analysis requests block; call off the main thread" }
        val id = nextId.getAndIncrement()
        val req = JSONObject().put("id", id).put("type", type)
        for ((k, v) in fields) req.put(k, v ?: JSONObject.NULL)
        val future = CompletableFuture<JSONObject>()
        pending[id] = future
        main.post { ensureSession().sendInput(req.toString() + "\n") }
        return try {
            val reply = future.get(timeoutMs, TimeUnit.MILLISECONDS)
            when {
                reply.has("error") -> {
                    Log.w(TAG, "$type failed: ${reply.optString("error")}")
                    null
                }
                // A newer request of the same type was already waiting; this one wasn't run.
                reply.optBoolean("superseded") -> null
                else -> reply
            }
        } catch (e: Exception) {
            null
        } finally {
            pending.remove(id)
        }
    }

    fun complete(source: String, line: Int, col: Int, path: String?, timeoutMs: Long = 8000): List<Completion> =
        tryComplete(source, line, col, path, timeoutMs).orEmpty()

    /**
     * Completions, or null if the server gave no answer (superseded by a newer request,
     * timed out, failed or restarted). An empty list means jedi found nothing.
     */
    fun tryComplete(source: String, line: Int, col: Int, path: String?, timeoutMs: Long = 8000): List<Completion>? {
        val r = request("complete", mapOf("source" to source, "line" to line, "col" to col, "path" to path), timeoutMs)
            ?: return null
        return r.optJSONArray("items").objects().map {
            Completion(it.optString("name"), it.optString("type"), it.optString("desc"))
        }
    }

    fun signatures(source: String, line: Int, col: Int, path: String?, timeoutMs: Long = 3000): List<Signature> {
        val r = request("signature", mapOf("source" to source, "line" to line, "col" to col, "path" to path), timeoutMs)
            ?: return emptyList()
        return r.optJSONArray("signatures").objects().map { s ->
            val params = s.optJSONArray("params")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
            Signature(s.optString("name"), params, if (s.isNull("index")) null else s.optInt("index"))
        }
    }

    /** Null if the server didn't answer (unknown), empty if the code compiles. */
    fun check(source: String, path: String?, timeoutMs: Long = 30_000): List<SyntaxIssue>? {
        val r = request("check", mapOf("source" to source, "path" to path), timeoutMs) ?: return null
        return r.optJSONArray("errors").objects().map {
            SyntaxIssue(it.optInt("line", 1), it.optInt("col", 1), it.optInt("end_line", 1), it.optInt("end_col", 2), it.optString("msg"))
        }
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    companion object {
        private const val TAG = "AnalysisClient"
    }
}
