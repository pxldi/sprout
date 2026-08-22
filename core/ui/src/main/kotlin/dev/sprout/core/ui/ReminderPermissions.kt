/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.ui

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * Whether a reminder can reach the screen, and what would change that if it cannot.
 *
 * The distinction that matters is [ASKABLE] versus [BLOCKED]. Android stops showing the
 * permission dialog after the user has refused twice, and a button that silently does nothing is
 * worse than no button — past that point the only honest offer is a trip to Settings.
 */
public enum class NotificationAccess {
    /** Reminders will appear. */
    GRANTED,

    /** The system dialog will still be shown if asked. */
    ASKABLE,

    /** Nothing will appear, and only Settings can undo it. */
    BLOCKED,
}

/** Whether reminders land on the minute, or inside a window the system picks. */
public enum class ExactAlarmAccess { ALLOWED, DENIED }

/**
 * Works out which of the three states the app is in.
 *
 * Separated from the framework so the awkward rows are testable: a fresh install on API 33+ is
 * *not* granted but has never been asked, which reads identically to a permanent refusal unless
 * [asked] is taken into account.
 *
 * Below API 33 there is no runtime permission and so no dialog. Notifications are on by default,
 * so reaching here at all means the user turned them off in Settings, and Settings is where they
 * would turn them back on — hence [NotificationAccess.BLOCKED] rather than a request that no
 * version of Android would honour.
 */
internal fun notificationAccess(
    enabled: Boolean,
    sdkInt: Int,
    showRationale: Boolean,
    asked: Boolean,
): NotificationAccess = when {
    enabled -> NotificationAccess.GRANTED
    sdkInt < Build.VERSION_CODES.TIRAMISU -> NotificationAccess.BLOCKED
    showRationale || !asked -> NotificationAccess.ASKABLE
    else -> NotificationAccess.BLOCKED
}

/**
 * What the app is currently allowed to do with reminders, and how to ask for more.
 *
 * Held as one object rather than two because the two permissions are not peers: without
 * [notifications] a reminder cannot appear at all, and [exactAlarms] only decides how punctual
 * something that does appear will be. Callers should resolve them in that order.
 */
@Stable
public class ReminderPermissions internal constructor(
    public val notifications: NotificationAccess,
    public val exactAlarms: ExactAlarmAccess,
    private val onRequest: () -> Unit,
    private val onOpenNotificationSettings: () -> Unit,
    private val onRequestExactAlarms: () -> Unit,
) {
    /**
     * Does whatever would actually help: shows the dialog while Android still shows it, and
     * opens Settings once it will not. Callers get one action and never have to branch.
     */
    public fun resolveNotifications() {
        when (notifications) {
            NotificationAccess.GRANTED -> Unit
            NotificationAccess.ASKABLE -> onRequest()
            NotificationAccess.BLOCKED -> onOpenNotificationSettings()
        }
    }

    /** Opens the special-access screen. Only meaningful while [exactAlarms] is DENIED. */
    public fun resolveExactAlarms(): Unit = onRequestExactAlarms()
}

/**
 * Reads the reminder permissions, and keeps reading them.
 *
 * Both of the fixes on offer are other apps' screens, so returning from one is the only signal
 * that anything changed — hence the resume hook. Granting exact alarms also fires a system
 * broadcast the app already listens for, which is what actually re-registers the alarm; this is
 * only so the prompt stops being shown.
 */
@Composable
public fun rememberReminderPermissions(): ReminderPermissions {
    val context = LocalContext.current
    val activity = LocalActivity.current

    // Survives rotation so a refusal is not re-asked on the way back up. Deliberately not
    // persisted: across a cold start the state falls back to ASKABLE, which at worst shows a
    // dialog Android declines to draw, and the result callback immediately corrects it.
    var asked by rememberSaveable { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(notificationsEnabled(context)) }
    var exact by remember { mutableStateOf(exactAlarmsAllowed(context)) }
    // Held as state rather than read inline. Nothing else changes between the first refusal and
    // the second — `asked` is already true and notifications are already off — so reading it
    // during composition would leave the first refusal's answer on screen forever, offering a
    // dialog Android had by then stopped showing.
    var rationale by remember { mutableStateOf(activity.wouldShowNotificationDialog()) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        asked = true
        // Re-read rather than trusting the result: the permission is one input to
        // areNotificationsEnabled, not the whole of it.
        enabled = notificationsEnabled(context)
        rationale = activity.wouldShowNotificationDialog()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        enabled = notificationsEnabled(context)
        exact = exactAlarmsAllowed(context)
        rationale = activity.wouldShowNotificationDialog()
    }

    return ReminderPermissions(
        notifications = notificationAccess(
            enabled = enabled,
            sdkInt = Build.VERSION.SDK_INT,
            showRationale = rationale,
            asked = asked,
        ),
        exactAlarms = if (exact) ExactAlarmAccess.ALLOWED else ExactAlarmAccess.DENIED,
        onRequest = { launcher.launch(POST_NOTIFICATIONS) },
        onOpenNotificationSettings = {
            (activity ?: context).startActivity(notificationSettings(context))
        },
        onRequestExactAlarms = {
            (activity ?: context).startActivity(exactAlarmSettings(context))
        },
    )
}

/**
 * Whether Android would still draw the permission dialog.
 *
 * It reports a rationale exactly while it intends to keep asking, and stops once the user has
 * refused for the last time — which makes it the only reliable way to tell a refusal that can be
 * revisited from one that can only be undone in Settings.
 */
private fun Activity?.wouldShowNotificationDialog(): Boolean =
    this != null &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        shouldShowRequestPermissionRationale(POST_NOTIFICATIONS)

/**
 * The one honest answer to "will a notification be seen", on every API level.
 *
 * Not a permission check: below API 33 there is no permission to check, and above it the user
 * can still switch the app off in Settings without the permission changing.
 */
private fun notificationsEnabled(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

private fun exactAlarmsAllowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

private fun notificationSettings(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

private fun exactAlarmSettings(context: Context): Intent =
    Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        "package:${context.packageName}".toUri(),
    )

private const val POST_NOTIFICATIONS = Manifest.permission.POST_NOTIFICATIONS
