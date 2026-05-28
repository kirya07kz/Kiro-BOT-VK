package com.vkbot.manager.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.delay
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Хелпер для проверки сети и ожидания её появления.
 * Использует ConnectivityManager (API 28+).
 */
object NetworkHelper {

    /**
     * Проверяет, является ли исключение сетевым (нет интернета, таймаут, DNS).
     */
    fun isNetworkError(e: Throwable): Boolean {
        if (e is UnknownHostException) return true
        if (e is ConnectException) return true
        if (e is SocketTimeoutException) return true
        val cause = e.cause
        return cause != null && isNetworkError(cause)
    }

    /**
     * Проверяет наличие активного интернет-соединения.
     */
    fun hasNetwork(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Ждёт появления сети, проверяя каждые 2 секунды.
     * @param initialDelayMs пауза перед первой проверкой (по умолчанию 5 сек)
     * @param maxWaitMs максимальное время ожидания; после истечения возвращает управление,
     *                  чтобы вызвавший код мог повторить попытку (по умолчанию 60 сек)
     */
    suspend fun waitForNetwork(
        context: Context,
        initialDelayMs: Long = 5000L,
        maxWaitMs: Long = 60_000L
    ) {
        delay(initialDelayMs)
        val startTime = System.currentTimeMillis()
        while (!hasNetwork(context)) {
            if (System.currentTimeMillis() - startTime >= maxWaitMs) return
            delay(2000L)
        }
    }
}
