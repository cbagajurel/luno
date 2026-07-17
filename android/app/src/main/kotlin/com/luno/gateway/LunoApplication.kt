package com.luno.gateway

import android.app.Application
import com.luno.gateway.di.AgentGraph
import com.luno.gateway.work.AgentWatchdogWorker

class LunoApplication : Application() {
    lateinit var graph: AgentGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AgentGraph(this)
        AgentWatchdogWorker.schedule(this)
        graph.logger.i(TAG, "LunoApplication created")
    }

    companion object {
        private const val TAG = "LunoApplication"
    }
}
