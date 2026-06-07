package com.ortuspro.service

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Simple in-memory sliding-window rate limiter.
 * Limits each IP to [maxRequests] per 60-second window.
 * Production deployments behind a proxy should use X-Forwarded-For.
 */
class RateLimiter(private val maxRequests: Int) {

    private data class Window(val requests: MutableList<Long> = mutableListOf())
    private val windows = ConcurrentHashMap<String, Window>()

    fun allow(ip: String): Boolean {
        val now    = Instant.now().epochSecond
        val cutoff = now - 60L

        val window = windows.getOrPut(ip) { Window() }
        synchronized(window) {
            // Remove entries older than 60 seconds
            window.requests.removeAll { it < cutoff }

            if (window.requests.size >= maxRequests) return false

            window.requests.add(now)
            return true
        }
    }

    /** Periodically clean up stale entries (call from a background job if needed). */
    fun cleanup() {
        val cutoff = Instant.now().epochSecond - 60L
        windows.entries.removeIf { (_, window) ->
            synchronized(window) {
                window.requests.removeAll { it < cutoff }
                window.requests.isEmpty()
            }
        }
    }
}
