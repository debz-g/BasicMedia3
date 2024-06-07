package com.example.basicmedia

import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.SeekBar
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
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

    private val mediaUrl =
        "https://firebasestorage.googleapis.com/v0/b/anti-corruption-2104.appspot.com/o/audios%2F-NXJmyWz4O2com2tbzRT?alt=media&token=691a828a-64eb-4773-90ab-433ecaecbaa0&_gl=1*150e5xn*_ga*ODgzNDQ0MTAuMTY4MzkwMzc1Mw..*_ga_CW55HF8NVT*MTY4NjEyMzU5OC43LjEuMTY4NjEyMzY2Ny4wLjAuMA.."
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private lateinit var controller: MediaController

    var duration: Int = 0
    var currentSongPosition: Long = 0L
    var seekForward = 5000
    var seekBackward = 5000

    private var totalSeekTime: Int = 0
    private var currentSeekTime: Int = 0
    private var seekStartTime: Int = 0
    private var seekEndTime: Int = 0

    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                if (controllerFuture.isDone) {
                    controller = controllerFuture.get()
                    initController()
                }
            }, MoreExecutors.directExecutor()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.btnPlayPause.setOnClickListener {
            controller.let { player ->
                player.let {
                    if (player.playbackState == Player.STATE_READY && player.playWhenReady) {
                        pauseMedia()
                    } else {
                        resumeMedia()
                    }
                }
            }
        }

        binding.seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentSeekTime = progress
                    controller.seekTo(progress.toLong() * 1000)
                    binding.time.text = getTimeString(progress) + "/" + getTimeString(duration)
                }
            }

            override fun onStartTrackingTouch(startTrackSeek: SeekBar?) {
                seekStartTime = startTrackSeek!!.progress
            }

            override fun onStopTrackingTouch(stopTrackSeek: SeekBar?) {
                seekEndTime = stopTrackSeek!!.progress

                totalSeekTime += if (seekEndTime > seekStartTime) {
                    (seekEndTime - seekStartTime)
                } else {
                    (seekStartTime - seekEndTime)
                }
            }

        })

        binding.btnSkipForward.setOnClickListener {
            currentSongPosition = controller.currentPosition
            if (currentSongPosition + seekForward <= controller.duration) {
                controller.seekTo(currentSongPosition + seekForward)
            } else {
                controller.seekTo(controller.duration)
            }
        }

        binding.btnSkipBack.setOnClickListener {
            currentSongPosition = controller.currentPosition
            if (currentSongPosition - seekBackward >= 0) {
                controller.seekTo(currentSongPosition - seekBackward)
            } else {
                controller.seekTo(0)
            }
        }
    }

    private fun playMedia(url: String) {
        log("play($url)")
        log("before=${getStateName(controller.playbackState)}")
        val media = MediaItem.Builder().setMediaId(url).build()
        binding.btnPlayPause.setImageDrawable(resources.getDrawable(R.drawable.pause))
        controller.setMediaItem(media)
        controller.prepare()
        controller.play()
        log("after=${getStateName(controller.playbackState)}")
    }

    private fun pauseMedia() {
        binding.btnPlayPause.setImageDrawable(resources.getDrawable(R.drawable.play))
        controller.pause()
    }

    private fun resumeMedia() {
        binding.btnPlayPause.setImageDrawable(resources.getDrawable(R.drawable.pause))
        controller.play()
    }

    private fun stopMedia() {
        controller.stop()
        controller.playWhenReady = false
        MediaController.releaseFuture(controllerFuture)
    }

    @OptIn(UnstableApi::class)
    private fun initController() {
        //controller.playWhenReady = true
        controller.addListener(object : Player.Listener {

            @Deprecated("Deprecated in Java")
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                if (playbackState == Player.STATE_READY && controller.playWhenReady) {
                    binding.btnPlayPause.setImageDrawable(resources.getDrawable(R.drawable.pause))
                } else {
                    binding.btnPlayPause.setImageDrawable(resources.getDrawable(R.drawable.play))
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int
            ) {
                val currentposition = controller.currentPosition.toInt() / 1000
                binding.seekbar.progress = currentposition
                binding.time.text =
                    getTimeString(currentposition) + "/" + getTimeString(controller.duration.toInt() / 1000)
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                super.onMediaMetadataChanged(mediaMetadata)
                log("onMediaMetadataChanged=$mediaMetadata")
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                log("onIsPlayingChanged=$isPlaying")

                duration = controller.duration.toInt() / 1000
                binding.seekbar.max = duration
                binding.time.text = "0:00 / " + getTimeString(duration)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                super.onPlaybackStateChanged(playbackState)
                log("onPlaybackStateChanged=${getStateName(playbackState)}")

                if (Player.STATE_BUFFERING == playbackState) {
                    binding.progressBar.show()
                } else {
                    binding.progressBar.hide()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                log("onPlayerError=${error.stackTraceToString()}")
            }

            override fun onPlayerErrorChanged(error: PlaybackException?) {
                super.onPlayerErrorChanged(error)
                log("onPlayerErrorChanged=${error?.stackTraceToString()}")
            }
        })

        binding.progressBar.hide()
        playMedia(mediaUrl)

        val handler = Handler(Looper.getMainLooper())
        handler.post(object : Runnable {
            override fun run() {
                val currentposition = controller.currentPosition.toInt() / 1000
                binding.seekbar.progress = currentposition
                binding.time.setText(getTimeString(currentposition) + "/" + getTimeString(duration))
                handler.postDelayed(this, 1000)
            }
        })

        log("start=${getStateName(controller.playbackState)}")
        log("COMMAND_PREPARE=${controller.isCommandAvailable(COMMAND_PREPARE)}")
        log("COMMAND_SET_MEDIA_ITEM=${controller.isCommandAvailable(COMMAND_SET_MEDIA_ITEM)}")
        log("COMMAND_PLAY_PAUSE=${controller.isCommandAvailable(COMMAND_PLAY_PAUSE)}")
    }

    private fun getStateName(i: Int): String? {
        return when (i) {
            1 -> "STATE_IDLE"
            2 -> "STATE_BUFFERING"
            3 -> "STATE_READY"
            4 -> "STATE_ENDED"
            else -> null
        }
    }

    private fun log(message: String) {
        Log.e("=====[TestMedia]=====", message)
    }

    override fun onStop() {
        MediaController.releaseFuture(controllerFuture)
        super.onStop()
    }

    private fun getTimeString(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", mins, secs)
    }
}