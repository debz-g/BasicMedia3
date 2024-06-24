package com.example.basicmedia.service

import android.content.Intent
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.basicmedia.constants.Enums
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class PlaybackService : MediaSessionService(), MediaSession.Callback {
    // Create your Player and MediaSession in the onCreate lifecycle event

    companion object {
        private var playerInstance: Player? = null
        private var mediaSessionInstance: MediaSession? = null
    }

    private lateinit var player: Player
    private var mediaSession: MediaSession? = null
    private var currentSongPosition: Long = 0L
    var seekForward = 5000
    var seekBackward = 5000

    //TODO If entries don't work then use values().map
    private val notificationPlayerCustomCommandButtons =
        Enums.Companion.NotificationPlayerCustomCommandButton.entries.map { command -> command.commandButton }

    override fun onCreate() {
        super.onCreate()
        initializeSessionAndPlayer()
    }


    private fun initializeSessionAndPlayer() {
        if (playerInstance == null) {
            playerInstance = ExoPlayer.Builder(this).build()
        }
        player = playerInstance!!

        if (mediaSessionInstance == null) {
            mediaSessionInstance = MediaSession.Builder(this, player).setCallback(this).build()
        }
        mediaSession = mediaSessionInstance

        mediaSession?.setCustomLayout(notificationPlayerCustomCommandButtons )
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<MutableList<MediaItem>> {
        val updatedMediaItems = mediaItems.map { it.buildUpon().setUri(it.mediaId).build() }.toMutableList()
        return Futures.immediateFuture(updatedMediaItems)
    }

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        val connectionResult = super.onConnect(session, controller)
        val availableSessionCommands = connectionResult.availableSessionCommands.buildUpon()

        /* Registering custom player command buttons for player notification. */
        notificationPlayerCustomCommandButtons.forEach { commandButton ->
            commandButton.sessionCommand?.let(availableSessionCommands::add)
        }

        return MediaSession.ConnectionResult.accept(
            availableSessionCommands.build(),
            connectionResult.availablePlayerCommands
        )
    }

    override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
        super.onPostConnect(session, controller)
        if (notificationPlayerCustomCommandButtons.isNotEmpty()) {
            /* Setting custom player command buttons to mediaLibrarySession for player notification. */
            mediaSession?.setCustomLayout(notificationPlayerCustomCommandButtons)
        }
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        /* Handling custom command buttons from player notification. */
        currentSongPosition = session.player.currentPosition
        if (customCommand.customAction == Enums.Companion.NotificationPlayerCustomCommandButton.REWIND.customAction) {
            if (currentSongPosition - seekBackward >= 0) {
                session.player.seekTo(currentSongPosition - seekBackward)
            } else {
                session.player.seekTo(0)
            }
        }
        if (customCommand.customAction == Enums.Companion.NotificationPlayerCustomCommandButton.FORWARD.customAction) {
            if (currentSongPosition + seekForward <= session.player.duration) {
                session.player.seekTo(currentSongPosition + seekForward)
            } else {
                session.player.seekTo(player.duration)
            }
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }


    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.run {
            release()
            player.release()
            mediaSession = null
            playerInstance = null
            mediaSessionInstance = null
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        mediaSession?.player?.let { player ->
            if (player.playWhenReady) {
                player.pause()
            }
            stopSelf()
        }
    }
}