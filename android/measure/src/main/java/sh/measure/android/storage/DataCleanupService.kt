package sh.measure.android.storage

import sh.measure.android.SessionManager
import sh.measure.android.config.ConfigProvider
import sh.measure.android.executors.MeasureExecutorService
import sh.measure.android.logger.LogLevel
import sh.measure.android.logger.Logger
import java.util.concurrent.RejectedExecutionException

internal interface DataCleanupService {
    fun clearStaleData()
}

/**
 * Cleans up stale data from the database and file storage.
 *
 * This service deletes all events, attachments, and sessions that
 * are not marked for reporting. It also deletes events for the oldest session if
 * the total number of events in database exceeds the maximum allowed.
 */
internal class DataCleanupServiceImpl(
    private val logger: Logger,
    private val fileStorage: FileStorage,
    private val database: Database,
    private val ioExecutor: MeasureExecutorService,
    private val sessionManager: SessionManager,
    private val configProvider: ConfigProvider,
) : DataCleanupService {
    private companion object {
        // The maximum number of sessions to query for deletion.
        // Keeping this to 1 as deleting too many sessions and events at once can lock
        // the db for other operations for too long. Each session can potentially
        // contain thousands of events, spans and attachments.
        const val MAX_SESSIONS_TO_QUERY = 1
    }

    override fun clearStaleData() {
        try {
            ioExecutor.submit {
                val currentSessionId = sessionManager.getSessionId()
                deleteSessionsNotMarkedForReporting(currentSessionId)
                trimEventsAndSpans()
                deleteBugReports(currentSessionId)
            }
        } catch (e: RejectedExecutionException) {
            logger.log(LogLevel.Debug, "Failed to submit data cleanup task to executor", e)
        }
    }

    private fun deleteBugReports(currentSessionId: String) {
        try {
            val bugReportsDir = fileStorage.getBugReportDir()
            if (!bugReportsDir.exists() || !bugReportsDir.isDirectory) {
                return
            }

            bugReportsDir.listFiles()?.forEach { sessionDir ->
                if (sessionDir.isDirectory && sessionDir.name != currentSessionId) {
                    sessionDir.deleteRecursively()
                }
            }
        } catch (e: Exception) {
            logger.log(LogLevel.Debug, "Failed to clean up stale bug reports", e)
        }
    }

    private fun trimEventsAndSpans() {
        val eventsCount = database.getEventsCount()
        val spansCount = database.getSpansCount()
        val totalSignals = eventsCount + spansCount
        if (totalSignals <= configProvider.maxSignalsInDatabase) {
            return
        }
        deleteOldestSession()
    }

    private fun deleteOldestSession() {
        database.getOldestSession()?.let {
            deleteSessions(listOf(it))
        }
    }

    private fun deleteSessionsNotMarkedForReporting(currentSessionId: String) {
        val sessionIds = database.getSessionIds(
            needReporting = false,
            filterSessionIds = listOf(currentSessionId),
            maxCount = MAX_SESSIONS_TO_QUERY,
        )
        deleteSessions(sessionIds)
    }

    private fun deleteSessions(sessionIds: List<String>) {
        if (sessionIds.isEmpty()) {
            return
        }
        val eventIds = database.getEventsForSessions(sessionIds)
        val attachmentIds = database.getAttachmentsForEvents(eventIds)
        fileStorage.deleteEventsIfExist(eventIds, attachmentIds)
        // deleting sessions from db will also delete events for the session as they ar
        // e cascaded deletes.
        database.deleteSessions(sessionIds)
    }
}
