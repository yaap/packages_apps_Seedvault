package com.stevesoltys.seedvault.ui.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.NotificationManager.IMPORTANCE_LOW
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException
import android.text.format.Formatter
import android.util.Log
import androidx.core.app.NotificationCompat.Action
import androidx.core.app.NotificationCompat.Builder
import androidx.core.app.NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
import androidx.core.app.NotificationCompat.PRIORITY_DEFAULT
import androidx.core.app.NotificationCompat.PRIORITY_HIGH
import androidx.core.app.NotificationCompat.PRIORITY_LOW
import com.stevesoltys.seedvault.R
import com.stevesoltys.seedvault.restore.ACTION_RESTORE_ERROR_UNINSTALL
import com.stevesoltys.seedvault.restore.EXTRA_PACKAGE_NAME
import com.stevesoltys.seedvault.restore.REQUEST_CODE_UNINSTALL
import com.stevesoltys.seedvault.settings.ACTION_APP_STATUS_LIST
import com.stevesoltys.seedvault.settings.SettingsActivity
import kotlin.math.min

private const val CHANNEL_ID_OBSERVER = "NotificationBackupObserver"
private const val CHANNEL_ID_APK = "NotificationApkBackup"
private const val CHANNEL_ID_SUCCESS = "NotificationBackupSuccess"
private const val CHANNEL_ID_ERROR = "NotificationError"
private const val CHANNEL_ID_RESTORE_ERROR = "NotificationRestoreError"
private const val NOTIFICATION_ID_OBSERVER = 1
internal const val NOTIFICATION_ID_APK = 2
private const val NOTIFICATION_ID_SUCCESS = 3
private const val NOTIFICATION_ID_ERROR = 4
private const val NOTIFICATION_ID_RESTORE_ERROR = 5
private const val NOTIFICATION_ID_BACKGROUND = 6
private const val NOTIFICATION_ID_NO_MAIN_KEY_ERROR = 7

private val TAG = BackupNotificationManager::class.java.simpleName

internal class BackupNotificationManager(private val context: Context) {

    private val nm = context.getSystemService(NotificationManager::class.java)!!.apply {
        createNotificationChannel(getObserverChannel())
        createNotificationChannel(getApkChannel())
        createNotificationChannel(getSuccessChannel())
        createNotificationChannel(getErrorChannel())
        createNotificationChannel(getRestoreErrorChannel())
    }

    private fun getObserverChannel(): NotificationChannel {
        val title = context.getString(R.string.notification_channel_title)
        return NotificationChannel(CHANNEL_ID_OBSERVER, title, IMPORTANCE_LOW).apply {
            enableVibration(false)
        }
    }

    private fun getApkChannel(): NotificationChannel {
        val title = context.getString(R.string.notification_apk_channel_title)
        return NotificationChannel(CHANNEL_ID_APK, title, IMPORTANCE_LOW).apply {
            enableVibration(false)
        }
    }

    private fun getSuccessChannel(): NotificationChannel {
        val title = context.getString(R.string.notification_success_channel_title)
        return NotificationChannel(CHANNEL_ID_SUCCESS, title, IMPORTANCE_LOW).apply {
            enableVibration(false)
        }
    }

    private fun getErrorChannel(): NotificationChannel {
        val title = context.getString(R.string.notification_error_channel_title)
        return NotificationChannel(CHANNEL_ID_ERROR, title, IMPORTANCE_DEFAULT)
    }

    private fun getRestoreErrorChannel(): NotificationChannel {
        val title = context.getString(R.string.notification_restore_error_channel_title)
        return NotificationChannel(CHANNEL_ID_RESTORE_ERROR, title, IMPORTANCE_HIGH)
    }

    /**
     * This should get called for each APK we are backing up.
     */
    fun onApkBackup(packageName: String, name: CharSequence, transferred: Int, expected: Int) {
        Log.i(TAG, "$transferred/$expected - $name ($packageName)")
        val text = context.getString(R.string.notification_apk_text, name)
        val notification = getApkBackupNotification(text, transferred, expected)
        nm.notify(NOTIFICATION_ID_APK, notification)
    }

    /**
     * This should get called for recording apps we don't back up.
     */
    fun onAppsNotBackedUp() {
        Log.i(TAG, "onAppsNotBackedUp")
        val notification =
            getApkBackupNotification(context.getString(R.string.notification_apk_not_backed_up))
        nm.notify(NOTIFICATION_ID_APK, notification)
    }

    fun getApkBackupNotification(
        text: String?,
        expected: Int = 0,
        transferred: Int = 0,
    ): Notification = Builder(context, CHANNEL_ID_APK).apply {
        setSmallIcon(R.drawable.ic_cloud_upload)
        setContentTitle(context.getString(R.string.notification_title))
        setContentText(text)
        setOngoing(true)
        setShowWhen(false)
        setWhen(System.currentTimeMillis())
        setProgress(expected, transferred, false)
        priority = PRIORITY_DEFAULT
        foregroundServiceBehavior = FOREGROUND_SERVICE_IMMEDIATE
    }.build()

    /**
     * Call after [onApkBackup] or [onAppsNotBackedUp] were called.
     */
    fun onApkBackupDone() {
        nm.cancel(NOTIFICATION_ID_APK)
    }

    /**
     * Call this right after starting a backup.
     */
    fun onBackupStarted(expectedPackages: Int) {
        updateBackupNotification(
            infoText = "", // This passes quickly, no need to show something here
            transferred = 0,
            expected = expectedPackages
        )
        Log.i(TAG, "onBackupStarted - Expecting $expectedPackages apps")
    }

    /**
     * In the series of notification updates,
     * this type is is expected to get called after [onApkBackup].
     */
    fun onBackupUpdate(app: CharSequence, transferred: Int, total: Int) {
        updateBackupNotification(
            infoText = app,
            transferred = min(transferred, total),
            expected = total
        )
    }

    private fun updateBackupNotification(
        infoText: CharSequence,
        transferred: Int,
        expected: Int,
    ) {
        @Suppress("MagicNumber")
        val percentage = (transferred.toFloat() / expected) * 100
        val percentageStr = "%.0f%%".format(percentage)
        Log.i(TAG, "$transferred/$expected - $percentageStr - $infoText")
        val notification = Builder(context, CHANNEL_ID_OBSERVER).apply {
            setSmallIcon(R.drawable.ic_cloud_upload)
            setContentTitle(context.getString(R.string.notification_title))
            setContentText(percentageStr)
            setOngoing(true)
            setShowWhen(false)
            setWhen(System.currentTimeMillis())
            setProgress(expected, transferred, false)
            priority = PRIORITY_DEFAULT
            foregroundServiceBehavior = FOREGROUND_SERVICE_IMMEDIATE
        }.build()
        nm.notify(NOTIFICATION_ID_OBSERVER, notification)
    }

    private fun updateBackgroundBackupNotification(infoText: CharSequence) {
        Log.i(TAG, "$infoText")
        val notification = Builder(context, CHANNEL_ID_OBSERVER).apply {
            setSmallIcon(R.drawable.ic_cloud_upload)
            setContentTitle(context.getString(R.string.notification_title))
            setOngoing(true)
            setShowWhen(false)
            setWhen(System.currentTimeMillis())
            setProgress(0, 0, true)
            priority = PRIORITY_LOW
        }.build()
        nm.notify(NOTIFICATION_ID_BACKGROUND, notification)
    }

    fun onServiceDestroyed() {
        nm.cancel(NOTIFICATION_ID_BACKGROUND)
        // Cancel left-over notifications that are still ongoing.
        //
        // We have seen a race condition where the service was taken down at the same time
        // as BackupObserver#backupFinished() was called, early enough to miss the cancel.
        //
        // This won't bring back the expected finish notification in this case,
        // but at least we don't leave stuck notifications laying around.
        // FIXME the service gets destroyed for each chunk when requesting backup in chunks
        //  This leads to the cancellation of an ongoing backup notification.
        //  So for now, we'll remove automatic notification clean-up
        //  and find out if it is still necessary. If not, this comment can be removed.
        // nm.activeNotifications.forEach { notification ->
        //     // only consider ongoing notifications in our ID space (storage backup uses > 1000)
        //     if (notification.isOngoing && notification.id < 1000) {
        //         Log.w(TAG, "Needed to clean up notification with ID ${notification.id}")
        //         nm.cancel(notification.id)
        //     }
        // }
    }

    fun onBackupFinished(success: Boolean, numBackedUp: Int?, total: Int, size: Long) {
        val titleRes =
            if (success) R.string.notification_success_title else R.string.notification_failed_title
        val contentText = if (numBackedUp == null) null else {
            val sizeStr = Formatter.formatShortFileSize(context, size)
            context.getString(R.string.notification_success_text, numBackedUp, total, sizeStr)
        }
        val iconRes = if (success) R.drawable.ic_cloud_done else R.drawable.ic_cloud_error
        val intent = Intent(context, SettingsActivity::class.java).apply {
            if (success) action = ACTION_APP_STATUS_LIST
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, FLAG_IMMUTABLE)
        val notification = Builder(context, CHANNEL_ID_SUCCESS).apply {
            setSmallIcon(iconRes)
            setContentTitle(context.getString(titleRes))
            setContentText(contentText)
            setOngoing(false)
            setShowWhen(true)
            setAutoCancel(true)
            setContentIntent(pendingIntent)
            setWhen(System.currentTimeMillis())
            setProgress(0, 0, false)
            priority = PRIORITY_LOW
        }.build()
        nm.cancel(NOTIFICATION_ID_OBSERVER)
        nm.notify(NOTIFICATION_ID_SUCCESS, notification)
    }

    fun hasActiveBackupNotifications(): Boolean {
        nm.activeNotifications.forEach {
            if (it.packageName == context.packageName) {
                if (it.id == NOTIFICATION_ID_BACKGROUND) return true
                if (it.id == NOTIFICATION_ID_OBSERVER) return it.isOngoing
            }
        }
        return false
    }

    @SuppressLint("RestrictedApi")
    fun onBackupError() {
        val intent = Intent(context, SettingsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, FLAG_IMMUTABLE)
        val actionText = context.getString(R.string.notification_error_action)
        val action = Action(R.drawable.ic_storage, actionText, pendingIntent)
        val notification = Builder(context, CHANNEL_ID_ERROR).apply {
            setSmallIcon(R.drawable.ic_cloud_error)
            setContentTitle(context.getString(R.string.notification_error_title))
            setContentText(context.getString(R.string.notification_error_text))
            setWhen(System.currentTimeMillis())
            setOnlyAlertOnce(true)
            setAutoCancel(true)
            mActions = arrayListOf(action)
        }.build()
        nm.notify(NOTIFICATION_ID_ERROR, notification)
    }

    fun onBackupErrorSeen() {
        nm.cancel(NOTIFICATION_ID_ERROR)
    }

    @SuppressLint("RestrictedApi")
    fun onRemovableStorageNotAvailableForRestore(packageName: String, storageName: String) {
        val appName = try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo)
        } catch (e: NameNotFoundException) {
            packageName
        }
        val intent = Intent(ACTION_RESTORE_ERROR_UNINSTALL).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_PACKAGE_NAME, packageName)
        }
        val flags = FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
        val pendingIntent =
            PendingIntent.getBroadcast(context, REQUEST_CODE_UNINSTALL, intent, flags)
        val actionText = context.getString(R.string.notification_restore_error_action)
        val action = Action(R.drawable.ic_warning, actionText, pendingIntent)
        val notification = Builder(context, CHANNEL_ID_RESTORE_ERROR).apply {
            setSmallIcon(R.drawable.ic_cloud_error)
            setContentTitle(context.getString(R.string.notification_restore_error_title, appName))
            setContentText(context.getString(R.string.notification_restore_error_text, storageName))
            setWhen(System.currentTimeMillis())
            setAutoCancel(true)
            priority = PRIORITY_HIGH
            mActions = arrayListOf(action)
        }.build()
        nm.notify(NOTIFICATION_ID_RESTORE_ERROR, notification)
    }

    fun onRestoreErrorSeen() {
        nm.cancel(NOTIFICATION_ID_RESTORE_ERROR)
    }

    @SuppressLint("RestrictedApi")
    fun onNoMainKeyError() {
        val intent = Intent(context, SettingsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, FLAG_IMMUTABLE)
        val actionText = context.getString(R.string.notification_error_action)
        val action = Action(0, actionText, pendingIntent)
        val notification = Builder(context, CHANNEL_ID_ERROR).apply {
            setSmallIcon(R.drawable.ic_cloud_error)
            setContentTitle(context.getString(R.string.notification_error_no_main_key_title))
            setContentText(context.getString(R.string.notification_error_no_main_key_text))
            setWhen(System.currentTimeMillis())
            setOnlyAlertOnce(true)
            setAutoCancel(false)
            setOngoing(true)
            setContentIntent(pendingIntent)
            mActions = arrayListOf(action)
        }.build()
        nm.notify(NOTIFICATION_ID_NO_MAIN_KEY_ERROR, notification)
    }

    fun onNoMainKeyErrorFixed() {
        nm.cancel(NOTIFICATION_ID_NO_MAIN_KEY_ERROR)
    }

}
