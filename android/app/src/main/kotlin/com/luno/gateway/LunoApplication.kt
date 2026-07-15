package com.luno.gateway

import android.app.Application
import com.luno.gateway.di.AgentGraph

class LunoApplication : Application() {
    lateinit var graph: AgentGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AgentGraph(this)
        graph.logger.i(TAG, "LunoApplication created")
    }

    companion object {
        private const val TAG = "LunoApplication"
    }
}
