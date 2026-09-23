package com.devy.fleasionshizuku

import android.content.Context

/**
 * Holds runtime configs picked/imported by the user, and pushes them
 * to the live AssetRewriter proxy whenever the proxy is running.
 */
object ConfigBridge {

    private val runtimeConfigs = mutableListOf<FleasionConfig>()
    private var activeRewriter: AssetRewriter? = null

    @Synchronized
    fun registerRuntime(cfg: FleasionConfig) {
        runtimeConfigs.removeAll { it.name == cfg.name }
        runtimeConfigs.add(cfg)
        activeRewriter?.loadFromConfig(allConfigs())
    }

    @Synchronized
    fun attachRewriter(rewriter: AssetRewriter) {
        activeRewriter = rewriter
        rewriter.loadFromConfig(allConfigs())
    }

    @Synchronized
    fun detachRewriter() {
        activeRewriter = null
    }

    @Synchronized
    fun snapshot(): List<FleasionConfig> = runtimeConfigs.toList()

    @Synchronized
    fun allConfigs(): List<FleasionConfig> = runtimeConfigs.toList()

    @Synchronized
    fun pushToProxyIfRunning(ctx: Context) {
        val r = activeRewriter ?: return
        val disk = ConfigRepository.loadAllConfigs(ctx)
        r.loadFromConfig(disk + runtimeConfigs)
    }
}
