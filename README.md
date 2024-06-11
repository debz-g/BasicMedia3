# BasicMedia3

This app is to demonstrate basic Media3 implementation.

# Features
- Player Controls like Play/Pause, Rewind, and Fast-Forward to custom time.
- Background media playback.
- Custom Notification Buttons to Rewind and Fast-Forward

# Documentation References
- Uses the latest Media3 Jetpack Components for publishing this. [Basics](https://developer.android.com/media/implement/playback-app)
- Uses MediaSessionService to handle background playback. [Background Playback](https://developer.android.com/media/media3/session/background-playback)
- Uses MediaController to connect the app UI with the MediaSessiona and the ExoPlayer. [MediaController](https://developer.android.com/media/media3/session/connect-to-media-app)
- Uses custom notification commands to achieve Notification Buttons to Rewind and Fast-Forward. [Custom Commands on Notification](https://developer.android.com/media/implement/surfaces/mobile#config-action-buttons)

# Basic Code Explanation
- There are mainly 2 files: MediaActivity.kt, which hosts the UI, basic logic, and PlaybackService. I have also made Enums and Utils for specific purposes that you can check out.
- The PlaybackService.kt initializes the ExoPlayer and MediaSession and dynamically takes MediaItem from the MediaActivity.kt. It also has overriden function to handle the custom notification commands. It also implements MediaSession.Callback for the custom notification commands for which we have to set callback while creating the MediaSession.
- The MediaActivity.kt has implementation of MediaController using SessionToken and ListenableFuture Controllers that gets the state of the player and session. It helps update the UI and do all the player-related tasks. We can add a listener to the controller and check every state the controller is going thought while performing any actions. 

<p><h2><a id="index8"></a>🖼 App Screenshots :</h2></p>
<table>
  <tr>
     <td><img src="https://github.com/debz-g/BasicMedia3/assets/77199373/38a41c39-ba5e-44e1-bfac-cc0c8869a5fb" width=360 height=720></td>
    <td><img src="https://github.com/debz-g/BasicMedia3/assets/77199373/69187d60-33ab-48f8-b305-074b6779772f" width=360 height=720></td>
  </tr>
</table>

