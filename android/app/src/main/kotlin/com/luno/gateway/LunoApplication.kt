package com.luno.gateway

import android.app.Application
import com.luno.gateway.di.AgentGraph

/**
 * Application entry point. Builds the [AgentGraph] once for the process so the
 * foreground service and the Flutter Activity share the same agent singletons.
 *
 * Note: this runs on every process start, including a START_STICKY restart after
 * the OS kills the app — so on restart the graph is fresh (agent STOPPED) until
 * the recreated service re-enters the foreground and flips it to RUNNING.
 */
class LunoApplication : Application() {
    lateinit var graph: AgentGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AgentGraph()
        graph.logger.i(TAG, "LunoApplication created")
    }

    companion object {
        private const val TAG = "LunoApplication"
    }
}
