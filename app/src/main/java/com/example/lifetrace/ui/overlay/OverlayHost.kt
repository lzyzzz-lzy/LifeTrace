package com.example.lifetrace.ui.overlay

import androidx.compose.runtime.Composable
import com.example.lifetrace.ui.components.AudioRecordingScreen
import com.example.lifetrace.ui.screen.share.ShareTripScreen

@Composable
fun OverlayHost(
    overlayState: OverlayState?,
    onDismiss: () -> Unit,
    onAudioRecorded: (String, Long) -> Unit, // 建议加：录音完成回传
) {
    when (val s = overlayState) {
        is OverlayState.ImagePreview -> {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = onDismiss,
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                FullScreenImagePreview(uri = s.uri, onDismiss = onDismiss)
            }
        }

        is OverlayState.VideoPlayer -> {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = onDismiss,
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                FullScreenVideoPlayerMinimal(uri = s.uri, onDismiss = onDismiss)
            }
        }

        is OverlayState.AudioPlayer -> {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = onDismiss,
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                AudioPlayerSheet(uri = s.uri, onDismiss = onDismiss)
            }
        }

        is OverlayState.AudioRecorder -> {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = onDismiss,
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                AudioRecordingScreen(
                    onRecordingComplete = { uri, duration ->
                        onAudioRecorded(uri, duration)
                        onDismiss()
                    },
                    onCancel = onDismiss
                )
            }
        }

        is OverlayState.ShareTrip -> {
            ShareTripScreen(
                tripId = s.tripId,
                onDismiss = onDismiss
            )
        }

        null -> Unit
    }
}