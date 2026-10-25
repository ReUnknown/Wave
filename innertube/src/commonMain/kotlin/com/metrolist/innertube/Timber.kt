package com.metrolist.innertube

/**
 * Stand-in for Android's Timber so the YouTube client also builds for desktop. The library's
 * debug chatter isn't needed in Wave; warnings and errors go to the standard error stream.
 */
internal object Timber {
    fun d(message: String, vararg args: Any?) = Unit
    fun w(message: String, vararg args: Any?) = Unit
    fun w(t: Throwable?, message: String = "", vararg args: Any?) = Unit
    fun e(message: String, vararg args: Any?) = System.err.println("innertube: $message")
    fun e(t: Throwable?, message: String = "", vararg args: Any?) = System.err.println("innertube: $message ${t?.message.orEmpty()}")
}
