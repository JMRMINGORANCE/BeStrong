package com.bestrong.mobile.work

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.bestrong.mobile.backup.AutoBackupWorker
import com.bestrong.mobile.di.WorkerEntryPoint
import com.bestrong.mobile.notify.ReminderWorker
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Los Workers de WorkManager con la app real (Hilt) en Robolectric. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WorkersTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val settings by lazy { EntryPointAccessors.fromApplication(app, WorkerEntryPoint::class.java).settings() }

    @Before fun grant() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun notifications() = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications

    @Test fun reminderNotifiesOnChosenDayWhenNotTrained() = runBlocking {
        settings.update { it.copy(reminderDays = setOf(LocalDate.now().dayOfWeek.value)) }
        val result = TestListenableWorkerBuilder<ReminderWorker>(app).build().doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue("Debe haber una notificación de recordatorio", notifications().isNotEmpty())
    }

    @Test fun reminderSilentOnOtherDays() = runBlocking {
        settings.update { it.copy(reminderDays = setOf(LocalDate.now().plusDays(1).dayOfWeek.value)) }
        TestListenableWorkerBuilder<ReminderWorker>(app).build().doWork()
        assertTrue(notifications().isEmpty())
    }

    @Test fun backupWithoutFolderDoesNothing() = runBlocking {
        settings.update { it.copy(backupFolderUri = null) }
        val result = TestListenableWorkerBuilder<AutoBackupWorker>(app).build().doWork()
        assertEquals(ListenableWorker.Result.success(), result)
    }
}
