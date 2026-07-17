package com.luno.gateway.work

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import com.luno.gateway.LunoApplication
import com.luno.gateway.agent.GatewayForegroundService
import java.util.concurrent.TimeUnit

enum class WatchdogAction { NONE, REVIVE }

/** Pure backstop policy: revive only when the node is paired but the agent isn't running. */
object WatchdogDecision {
    fun decide(paired: Boolean, running: Boolean): WatchdogAction =
        if (paired && !running) WatchdogAction.REVIVE else WatchdogAction.NONE
}

/**
 * The deferred safety net (§7.1, pitfalls "Android background limitations"): a
 * periodic job that revives the agent when the foreground service was killed and,
 * failing that, drains the durable outbox headless so queued sends still go out
 * after a long offline gap. The real-time path is the FGS + socket; this only
 * backstops longer gaps within WorkManager's allowed windows (≥15-min floor).
 */
class AgentWatchdogWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val graph = (applicationContext as LunoApplication).graph
        val paired = graph.credentialStore.load() != null
        if (WatchdogDecision.decide(paired, graph.agentController.isRunning) == WatchdogAction.NONE) {
            return Result.success()
        }
        return try {
            GatewayForegroundService.start(applicationContext)
            graph.logger.i(TAG, "watchdog revived the foreground service")
            Result.success()
        } catch (e: Exception) {
            if (isFgsStartNotAllowed(e)) {
                // Can't foreground from here (Android 12+ background-start rules); do the
                // offline-safe part directly — SMS send needs no FGS, and its events are durable.
                graph.logger.w(TAG, "FGS start not allowed; draining outbox headless")
                graph.outboxDispatcher.drainQueued()
                Result.success()
            } else {
                graph.logger.w(TAG, "watchdog revive failed: ${e.message}")
                Result.retry()
            }
        }
    }

    private fun isFgsStartNotAllowed(e: Exception): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException

    companion object {
        private const val TAG = "AgentWatchdog"
        private const val WORK_NAME = "luno-agent-watchdog"

        fun schedule(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<AgentWatchdogWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                    )
                    .setBackoffCriteria(
                        BackoffPolicy.LINEAR,
                        WorkRequest.MIN_BACKOFF_MILLIS,
                        TimeUnit.MILLISECONDS,
                    )
                    .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
