package nl.angryuncle.heads

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A square selfie viewfinder with a head-shaped guide, a shutter, a camera flip and a gallery fallback.
 * Hands the upright photo to [onPhoto]; [onPickFromGallery] opens the system photo picker instead.
 */
@Composable
fun CameraCapture(
    onPhoto: (Bitmap) -> Unit,
    onPickFromGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    var front by rememberSaveable { mutableStateOf(true) }
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }

    LaunchedEffect(granted, front) {
        if (!granted) return@LaunchedEffect
        val provider = ProcessCameraProvider.awaitInstance(context)
        val preview = Preview.Builder().build().apply { setSurfaceProvider { surfaceRequest = it } }
        val wanted = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        // Some devices (and emulators) only have one camera.
        val selector = if (provider.hasCamera(wanted)) wanted else {
            if (front) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
        }
        try {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            error = null
            awaitCancellation()
        } catch (e: IllegalArgumentException) {
            Log.w("CameraCapture", "No usable camera", e)
            error = "No camera available. Pick a photo instead."
        } finally {
            provider.unbindAll()
            surfaceRequest = null
        }
    }

    fun shoot() {
        busy = true
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    scope.launch {
                        val photo = withContext(Dispatchers.Default) { HeadProcessor.fromCapture(image, mirror = front) }
                        busy = false
                        onPhoto(photo)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.w("CameraCapture", "Capture failed", exception)
                    busy = false
                    error = "That didn't work. Try again?"
                }
            },
        )
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            val request = surfaceRequest
            when {
                !granted -> CameraMessage("The camera is off. Allow it, or pick a photo from your gallery.") {
                    Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                }
                error != null -> CameraMessage(error!!)
                request != null -> {
                    CameraXViewfinder(surfaceRequest = request, modifier = Modifier.matchParentSize())
                    HeadGuide(Modifier.matchParentSize())
                }
                else -> CircularProgressIndicator(color = Color.White)
            }
            if (busy) CircularProgressIndicator(color = Color.White)
        }

        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onPickFromGallery) { Text("Gallery") }
            Shutter(enabled = granted && surfaceRequest != null && !busy, onClick = ::shoot)
            OutlinedButton(onClick = { front = !front }, enabled = granted) { Text("Flip") }
        }
    }
}

@Composable
private fun CameraMessage(text: String, action: @Composable () -> Unit = {}) {
    Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        action()
    }
}

/** A dashed oval where the head should go, matching where the auto-crop will look. */
@Composable
private fun HeadGuide(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width * 0.58f
        val h = size.height * 0.74f
        drawOval(
            Color.White.copy(alpha = 0.8f),
            topLeft = center - Offset(w / 2, h / 2),
            size = Size(w, h),
            style = Stroke(width = 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f))),
        )
    }
}

@Composable
private fun Shutter(enabled: Boolean, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f)
    Box(
        Modifier
            .size(76.dp)
            .border(4.dp, color, CircleShape)
            .padding(8.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Take photo" },
    )
}
