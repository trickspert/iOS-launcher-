package dev.liquidglass.launcher

import android.app.Notification
import android.content.ComponentName
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Optional notification access. Counts active notifications per app for iOS-style badges and
 * follows the active media session for the Music widget.
 */
class BadgeService : NotificationListenerService() {
    private var sessions: MediaSessionManager? = null
    private var controller: MediaController? = null
    private val me by lazy { ComponentName(this, BadgeService::class.java) }

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list) }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() {
            pick(runCatching { sessions?.getActiveSessions(me) }.getOrNull())
        }
    }

    override fun onListenerConnected() {
        recount()
        runCatching {
            val msm = getSystemService(MediaSessionManager::class.java)
            sessions = msm
            msm.addOnActiveSessionsChangedListener(sessionsChanged, me)
            pick(msm.getActiveSessions(me))
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = recount()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = recount()

    override fun onListenerDisconnected() {
        BadgeCenter.counts.clear()
        runCatching { sessions?.removeOnActiveSessionsChangedListener(sessionsChanged) }
        runCatching { controller?.unregisterCallback(callback) }
        controller = null
        NowPlaying.clear()
    }

    private fun pick(list: List<MediaController>?) {
        val next = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list?.firstOrNull()
        if (next?.sessionToken != controller?.sessionToken) {
            runCatching { controller?.unregisterCallback(callback) }
            controller = next
            runCatching { next?.registerCallback(callback) }
        }
        publish()
    }

    private fun publish() {
        val c = controller
        if (c == null) {
            NowPlaying.clear()
            return
        }
        val m = runCatching { c.metadata }.getOrNull()
        NowPlaying.controller = c
        NowPlaying.pkg = c.packageName
        NowPlaying.playing = runCatching { c.playbackState?.state == PlaybackState.STATE_PLAYING }.getOrDefault(false)
        NowPlaying.title = m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: m?.getString(MediaMetadata.METADATA_KEY_TITLE)
        NowPlaying.artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        val art = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: m?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        NowPlaying.art = art?.let { small(it) }?.asImageBitmap()
    }

    private fun small(b: Bitmap): Bitmap? = runCatching {
        val max = 320
        val scaled = if (b.width <= max && b.height <= max) b
        else Bitmap.createScaledBitmap(b, max, (b.height * max / b.width.toFloat()).toInt().coerceAtLeast(1), true)
        // Hardware / recycled-on-us bitmaps are unsafe to hold on to, so keep a private software copy.
        scaled.copy(Bitmap.Config.ARGB_8888, false)
    }.getOrNull()

    private fun recount() {
        val counts = HashMap<String, Int>()
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        for (n in active) {
            if (n.isOngoing || !n.isClearable) continue
            if (n.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) continue
            counts[n.packageName] = (counts[n.packageName] ?: 0) + maxOf(1, n.notification.number)
        }
        BadgeCenter.counts.keys.filter { it !in counts }.forEach { BadgeCenter.counts.remove(it) }
        counts.forEach { (k, v) -> if (BadgeCenter.counts[k] != v) BadgeCenter.counts[k] = v }
    }
}
