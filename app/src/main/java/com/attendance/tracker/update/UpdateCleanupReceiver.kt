package com.attendance.tracker.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires only after this app is replaced by a newer install; drops the downloaded APK. */
class UpdateCleanupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) clearUpdateCache(context)
    }
}
