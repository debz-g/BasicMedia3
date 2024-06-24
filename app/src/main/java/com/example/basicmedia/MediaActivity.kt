package com.example.basicmedia

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.COMMAND_PLAY_PAUSE
import androidx.media3.common.Player.COMMAND_PREPARE
import androidx.media3.common.Player.COMMAND_SET_MEDIA_ITEM
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.basicmedia.constants.Utils
import com.example.basicmedia.constants.Utils.Companion.CURRENT_POSITION
import com.example.basicmedia.constants.Utils.Companion.hide
import com.example.basicmedia.constants.Utils.Companion.show
import com.example.basicmedia.databinding.ActivityMediaBinding
import com.example.basicmedia.service.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class MediaActivity : AppCompatActivity() {

    private val binding: ActivityMediaBinding by lazy {
        ActivityMediaBinding.inflate(layoutInflater)
    }

    var duration: Int = 0
    private lateinit var controller: MediaController
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null

    // Elapsed Time
    private var stopwatchStartTime: Long = 0
    private var elapsedTime: Long = 0
    private var isStopwatchRunning: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        initializeMediaController()
        setupUIControls()
    }

    private fun initializeMediaController() {
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        mediaControllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        mediaControllerFuture?.apply {
            addListener(Runnable {
                controller = get()
                updateUIWithMediaController(controller)
                // Ensure media is played appropriately based on state
                log("INITIAL STATE = ${controller.playbackState}")
                handlePlaybackBasedOnState()
            }, MoreExecutors.directExecutor())
        }
    }

    private fun handlePlaybackBasedOnState() {
        if (controller.playbackState == Player.STATE_IDLE || controller.playbackState == Player.STATE_ENDED) {
            playMedia()
        } else if (controller.playbackState == Player.STATE_READY || controller.playbackState == Player.STATE_BUFFERING) {
            updateUIWithPlayback()
        }
    }

    private fun updateUIWithPlayback() {
        hideBuffering()
        updatePlayPauseButton(controller.playWhenReady)
        if (controller.playWhenReady) {
            val currentposition = controller.currentPosition.toInt() / 1000
            binding.seekbar.progress = currentposition
            binding.time.text = getTimeString(currentposition)
            binding.duration.text = getTimeString(controller.duration.toInt() / 1000)
        }
    }

    private fun playMedia() {
        val mediaItem = MediaItem.Builder()
            .setMediaId("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setFolderType(MediaMetadata.FOLDER_TYPE_ALBUMS)
                    .setArtworkUri(Uri.parse("https://i.pinimg.com/736x/4b/02/1f/4b021f002b90ab163ef41aaaaa17c7a4.jpg"))
                    .setAlbumTitle("SoundHelix")
                    .setDisplayTitle("Song 1")
                    .build()
            ).build()

        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()
    }

    private fun updateUIWithMediaController(controller: MediaController) {
        controller.addListener(object : Player.Listener {

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int
            ) {
                val currentposition = controller.currentPosition.toInt() / 1000
                binding.seekbar.progress = currentposition
                binding.time.text = getTimeString(currentposition)
                binding.duration.text = getTimeString(controller.duration.toInt() / 1000)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        showBuffering()
                        pauseStopwatch()
                    }
                    Player.STATE_READY -> {
                        hideBuffering()
                        updatePlayPauseButton(controller.playWhenReady)
                        if (controller.playWhenReady){
                            startStopwatch()
                        }
                    }

                    Player.STATE_ENDED -> handlePlaybackEnded()
                    Player.STATE_IDLE -> {
                        pauseStopwatch()
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayPauseButton(isPlaying)

                if (isPlaying) {
                    startStopwatch()
                } else {
                    pauseStopwatch()
                }

                duration = controller.duration.toInt() / 1000
                binding.seekbar.max = duration
                binding.time.text = "0:00"
                binding.duration.text = getTimeString(duration)
            }
        })

        updatePlayPauseButton(controller.playWhenReady)

        val handler = Handler(Looper.getMainLooper())
        handler.post(object : Runnable {
            override fun run() {
                val currentposition = controller.currentPosition.toInt() / 1000
                binding.seekbar.progress = currentposition
                binding.time.text = getTimeString(currentposition)
                binding.duration.text = getTimeString(duration)
                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun setupUIControls() {
         binding.btnPlayPause.setOnClickListener {
            if (controller.playWhenReady) {
                controller.pause()
            } else {
                controller.play()
            }
        }

        binding.seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) controller.seekTo(progress.toLong() * 1000)
                binding.time.text = getTimeString(progress)
                binding.duration.text = getTimeString(duration)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                pauseStopwatch()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (controller.playWhenReady) {
                    startStopwatch()
                }
            }
        })
    }

    private fun startStopwatch() {
        if (!isStopwatchRunning) {
            stopwatchStartTime = SystemClock.elapsedRealtime() - elapsedTime
            isStopwatchRunning = true
        }
    }

    private fun pauseStopwatch() {
        if (isStopwatchRunning) {
            elapsedTime = SystemClock.elapsedRealtime() - stopwatchStartTime
            isStopwatchRunning = false
        }
    }

    private fun reportListenTime() {
        if (isStopwatchRunning) {
            elapsedTime = SystemClock.elapsedRealtime() - stopwatchStartTime
        }
        // Convert milliseconds to seconds
        val listenedTimeInSeconds = elapsedTime / 1000

        Toast.makeText(this@MediaActivity, "Total listened time: $listenedTimeInSeconds seconds", Toast.LENGTH_SHORT).show()
        log("Total listened time: $listenedTimeInSeconds seconds")
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        binding.btnPlayPause.setImageResource(if (isPlaying) R.drawable.pause else R.drawable.play)
    }

    private fun getTimeString(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", mins, secs)
    }

    private fun showBuffering() {
        binding.progressBar.show()
    }

    private fun hideBuffering() {
        binding.progressBar.hide()
    }

    private fun handlePlaybackEnded() {
        binding.btnPlayPause.setImageResource(R.drawable.play)
        binding.seekbar.progress = 0
        binding.time.text = getTimeString(duration)
        binding.duration.text = getTimeString(duration)
        binding.progressBar.visibility = View.GONE
        pauseStopwatch()
        reportListenTime()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaControllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }

    private fun log(message: String) {
        Log.e("=====[DebzMediaPlayer]=====", message)
    }
}

//    var duration: Int = 0
//    private var currentSongPosition: Long = 0L
//    private var seekForward = 5000
//    private var seekBackward = 5000
//
//    private var startTime: Long = 0
//    private var elapsedTime: Long = 0

//    override fun onStart() {
//        super.onStart()
//        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
//        log("Session Token = $sessionToken")
//        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
//        controllerFuture.addListener(
//            {
//                if (controllerFuture.isDone) {
//                    controller = controllerFuture.get()
//                    initController()
//                }
//            }, MoreExecutors.directExecutor()
//        )
//    }

//    private fun initListeners() {
//        binding.btnPlayPause.setOnClickListener {
//            controller.let { player ->
//                player.let {
//                    if (player.playbackState == Player.STATE_READY && player.playWhenReady) {
//                        pauseMedia()
//                    } else {
//                        resumeMedia()
//                    }
//                }
//            }
//        }
//
//        binding.seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
//            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
//                if (fromUser) {
//                    controller.seekTo(progress.toLong() * 1000)
//                    binding.time.text = getTimeString(progress) + "/" + getTimeString(duration)
//                }
//            }
//
//            override fun onStartTrackingTouch(startTrackSeek: SeekBar?) {
//                pauseStopwatch()
//            }
//
//            override fun onStopTrackingTouch(stopTrackSeek: SeekBar?) {
//                resumeStopwatch()
//            }
//
//        })
//
//        binding.btnSkipForward.setOnClickListener {
//            currentSongPosition = controller.currentPosition
//            if (currentSongPosition + seekForward <= controller.duration) {
//                controller.seekTo(currentSongPosition + seekForward)
//            } else {
//                controller.seekTo(controller.duration)
//            }
//        }
//
//        binding.btnSkipBack.setOnClickListener {
//            currentSongPosition = controller.currentPosition
//            if (currentSongPosition - seekBackward >= 0) {
//                controller.seekTo(currentSongPosition - seekBackward)
//            } else {
//                controller.seekTo(0)
//            }
//        }
//    }
//    private fun pauseStopwatch() {
//        elapsedTime += SystemClock.elapsedRealtime() - startTime
//        log("StopWatchTimePause = $elapsedTime")
//        log("StopWatchTimePauseClock = ${SystemClock.elapsedRealtime()}")
//    }
//
//    private fun resumeStopwatch() {
//        startTime = SystemClock.elapsedRealtime()
//        log("StopWatchTimeResume = ${SystemClock.elapsedRealtime()}")
//        log("StopWatchTimeResumeClock = $startTime")
//    }
//
//
//    private fun playMedia(url: String) {
//        log("play($url)")
//        log("before=${getStateName(controller.playbackState)}")
//        val media = MediaItem.Builder().setMediaId(url).build()
//        binding.btnPlayPause.setImageDrawable(resources.getDrawable(R.drawable.pause))
//
//        controller.let { player ->
//            player.setMediaItem(media)
//            player.prepare()
//            player.play()
//            log("after=${getStateName(player.playbackState)}")
//        }
//    }
//
//    private fun pauseMedia() {
//        binding.btnPlayPause.setImageDrawable(resources.getDrawable(R.drawable.play))
//        controller.pause()
//        pauseStopwatch()
//    }
//
//    private fun resumeMedia() {
//        binding.btnPlayPause.setImageDrawable(resources.getDrawable(R.drawable.pause))
//        controller.play()
//
//        resumeStopwatch()
//    }
//
//
//    @OptIn(UnstableApi::class)
//    private fun initController() {
//        //controller.playWhenReady = true
//        controller.addListener(object : Player.Listener {
//
//            override fun onPositionDiscontinuity(
//                oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int
//            ) {
//                val currentposition = controller.currentPosition.toInt() / 1000
//                binding.seekbar.progress = currentposition
//                binding.time.text =
//                    getTimeString(currentposition) + "/" + getTimeString(controller.duration.toInt() / 1000)
//            }
//
//            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
//                super.onMediaMetadataChanged(mediaMetadata)
//                log("onMediaMetadataChanged=$mediaMetadata")
//            }
//
//            override fun onIsPlayingChanged(isPlaying: Boolean) {
//                super.onIsPlayingChanged(isPlaying)
//                log("onIsPlayingChanged=$isPlaying")
//
//                duration = controller.duration.toInt() / 1000
//                binding.seekbar.max = duration
//                binding.time.text = "0:00 / " + getTimeString(duration)
//            }
//
//            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
//                super.onPlayWhenReadyChanged(playWhenReady, reason)
//                if (playWhenReady){
//                    binding.btnPlayPause.setImageDrawable(resources.getDrawable(R.drawable.pause))
//                    resumeStopwatch()
//                } else {
//                    binding.btnPlayPause.setImageDrawable(resources.getDrawable(R.drawable.play))
//                    pauseStopwatch()
//                }
//            }
//
//            override fun onPlaybackStateChanged(playbackState: Int) {
//                super.onPlaybackStateChanged(playbackState)
//                log("onPlaybackStateChanged=${getStateName(playbackState)}")
//
//                if (Player.STATE_BUFFERING == playbackState) {
//                    binding.progressBar.show()
//                } else {
//                    binding.progressBar.hide()
//                }
//
//
//                when (playbackState) {
//                    Player.STATE_READY -> {
//
//                    } // Start when playback starts
//                    Player.STATE_ENDED -> {
//                        pauseStopwatch() // Pause on playback end
//                        val listenedTimeInSeconds = elapsedTime / 1000
//                        Toast.makeText(
//                            this@MediaActivity,
//                            "Listening Time : $listenedTimeInSeconds",
//                            Toast.LENGTH_SHORT
//                        ).show()
//                        log("Listening Time : $listenedTimeInSeconds")
//                        elapsedTime = 0
//                    }
//
//                    Player.STATE_BUFFERING -> {
//                        //No Ops
//                    }
//
//                    Player.STATE_IDLE -> {
//                        //No Ops
//                    }
//                }
//            }
//
//            override fun onPlayerError(error: PlaybackException) {
//                super.onPlayerError(error)
//                log("onPlayerError=${error.stackTraceToString()}")
//            }
//
//            override fun onPlayerErrorChanged(error: PlaybackException?) {
//                super.onPlayerErrorChanged(error)
//                log("onPlayerErrorChanged=${error?.stackTraceToString()}")
//            }
//        })
//        binding.progressBar.hide()
//        if (!binding.progressBar.isVisible) {
//            playMedia(mediaUrl)
//        }
//
//        val handler = Handler(Looper.getMainLooper())
//        handler.post(object : Runnable {
//            override fun run() {
//                val currentposition = controller.currentPosition.toInt() / 1000
//                binding.seekbar.progress = currentposition
//                binding.time.setText(getTimeString(currentposition) + "/" + getTimeString(duration))
//                handler.postDelayed(this, 1000)
//            }
//        })
//    }
//
//    private fun getStateName(i: Int): String? {
//        return when (i) {
//            1 -> "STATE_IDLE"
//            2 -> "STATE_BUFFERING"
//            3 -> "STATE_READY"
//            4 -> "STATE_ENDED"
//            else -> null
//        }
//    }
//
//    private fun log(message: String) {
//        Log.e("=====[TestMedia]=====", message)
//    }
//
//    private fun getTimeString(seconds: Int): String {
//        val mins = seconds / 60
//        val secs = seconds % 60
//        return String.format("%02d:%02d", mins, secs)
//    }