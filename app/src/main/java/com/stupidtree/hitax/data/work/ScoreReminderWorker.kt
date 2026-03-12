package com.stupidtree.hitax.data.work

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.stupidtree.component.data.DataState
import com.stupidtree.hitax.R
import com.stupidtree.hitax.data.model.eas.CourseScoreItem
import com.stupidtree.hitax.data.model.eas.TermItem
import com.stupidtree.hitax.data.repository.EASRepository
import com.stupidtree.hitax.data.source.preference.ScoreReminderStore
import com.stupidtree.hitax.data.source.web.service.EASService
import com.stupidtree.hitax.ui.eas.score.ScoreInquiryActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ScoreReminderWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {

    override fun doWork(): Result {
        val store = ScoreReminderStore.getInstance(applicationContext)
        if (!store.isEnabled()) return Result.success()
        val app = applicationContext as? Application ?: return Result.failure()
        val repository = EASRepository.getInstance(app)
        if (!repository.getEasToken().isLogin()) return Result.success()

        val termsState = awaitLiveData(repository.getAllTerms(), 6)
        if (termsState.state != DataState.STATE.SUCCESS || termsState.data.isNullOrEmpty()) {
            return Result.retry()
        }
        val term = selectTerm(termsState.data!!)
        val scoresState = awaitLiveData(
            repository.getPersonalScoresWithSummary(term, EASService.TestType.ALL),
            12
        )
        if (scoresState.state != DataState.STATE.SUCCESS || scoresState.data == null) {
            return Result.retry()
        }
        val items = scoresState.data?.items ?: emptyList()
        val newKeys = items.map { buildKey(it) }.toSet()
        val known = store.getKnownScores()
        if (known.isEmpty()) {
            store.setKnownScores(newKeys)
            return Result.success()
        }
        val diff = newKeys - known
        if (diff.isNotEmpty()) {
            val newItems = items.filter { diff.contains(buildKey(it)) }
            sendNotification(newItems)
            store.setKnownScores(known + diff)
        }
        return Result.success()
    }

    private fun selectTerm(terms: List<TermItem>): TermItem {
        return terms.firstOrNull { it.isCurrent } ?: terms.first()
    }

    private fun buildKey(item: CourseScoreItem): String {
        val term = item.termName?.trim().orEmpty()
        val code = item.courseCode?.trim().orEmpty()
        val name = item.courseName?.trim().orEmpty()
        return listOf(term, code, name, item.finalScores.toString()).joinToString("|")
    }

    private fun sendNotification(items: List<CourseScoreItem>) {
        if (items.isEmpty()) return
        val title = applicationContext.getString(R.string.score_reminder_notification_title)
        val names = items.mapNotNull { it.courseName?.trim()?.ifEmpty { null } ?: it.courseCode }
        val text = if (names.size <= 3) {
            names.joinToString("、")
        } else {
            val shown = names.take(3).joinToString("、")
            applicationContext.getString(
                R.string.score_reminder_notification_more,
                shown,
                names.size
            )
        }

        val intent = Intent(applicationContext, ScoreInquiryActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bc_score)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        manager.notify(NOTIF_ID, notification)
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.score_reminder_title),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }
    }

    private fun <T> awaitLiveData(
        liveData: androidx.lifecycle.LiveData<DataState<T>>,
        timeoutSeconds: Long
    ): DataState<T> {
        val latch = CountDownLatch(1)
        var result = DataState<T>(DataState.STATE.FETCH_FAILED)
        val observer = androidx.lifecycle.Observer<DataState<T>> { state ->
            result = state
            latch.countDown()
        }
        val handler = Handler(Looper.getMainLooper())
        handler.post { liveData.observeForever(observer) }
        latch.await(timeoutSeconds, TimeUnit.SECONDS)
        handler.post { liveData.removeObserver(observer) }
        return result
    }

    companion object {
        private const val CHANNEL_ID = "score_reminder"
        private const val NOTIF_ID = 2101
    }
}
