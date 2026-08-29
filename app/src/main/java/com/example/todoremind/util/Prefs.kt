package com.example.todoremind.util

import android.content.Context

object Prefs {
    private const val NAME = "widget_prefs"
    private fun p(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    fun showDone(ctx: Context) = p(ctx).getBoolean("show_done", true)
    fun setShowDone(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("show_done", v).apply() }
}
