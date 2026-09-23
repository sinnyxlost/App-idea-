package com.devy.fleasionshizuku

import android.content.Context
import android.content.Intent

object RobloxLauncher {

    fun launch(ctx: Context, pkg: String = "com.roblox.client"): Boolean {
        return try {
            val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
                true
            } else false
        } catch (_: Throwable) { false }
    }

    fun launchAny(ctx: Context): Boolean {
        for (r in RobloxPathResolver.findAll(ctx)) {
            if (launch(ctx, r.packageName)) return true
        }
        return false
    }
}
