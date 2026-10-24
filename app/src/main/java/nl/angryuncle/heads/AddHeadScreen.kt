package nl.angryuncle.heads

import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import nl.angryuncle.game.Expression
import nl.angryuncle.game.drawPhotoHead

private enum class Step { CalmPhoto, CalmFrame, AngryPhoto, AngryFrame, Finish }

/** One analysed photo plus how the user framed it. */
private class Shot(val analysis: Analysis) {
    var crop by mutableStateOf(analysis.crop)
    var removeBackground by mutableStateOf(analysis.cutout != null)
    val source: ImageBitmap = analysis.source.asImageBitmap()
    val cutout: ImageBitmap? = analysis.cutout?.asImageBitmap()
}

/** The finished head, rendered and ready to save. */
private class Result(val calm: Bitmap, val angry: Bitmap?, val eyes: Pair<Offset, Offset>?)

/**
 * Making a head: a normal photo, its framing, an angry photo (or skip to fake it), its framing, and a name.
 * Calls [onSaved] once the head is on disk.
 */
@Composable
fun AddHeadScreen(onSaved: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(Step.CalmPhoto) }
    var calm by remember { mutableStateOf<Shot?>(null) }
    var angry by remember { mutableStateOf<Shot?>(null) }
    var working by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun accept(photo: Bitmap) {
        val forAngry = step == Step.AngryPhoto
        working = "Looking for a face…"
        error = null
        scope.launch {
            try {
                val shot = Shot(HeadProcessor.analyze(photo))
                if (forAngry) {
                    angry = shot
                    step = Step.AngryFrame
                } else {
                    calm = shot
                    step = Step.CalmFrame
                }
            } catch (e: Exception) {
                Log.w("AddHead", "Analysis failed", e)
                error = "Couldn't process that photo. Try another one?"
            } finally {
                working = null
            }
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        working = "Loading photo…"
        scope.launch {
            try {
                accept(HeadProcessor.loadUri(context, uri))
            } catch (e: Exception) {
                Log.w("AddHead", "Could not load $uri", e)
                working = null
                error = "Couldn't open that photo."
            }
        }
    }
    fun pickFromGallery() = gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    fun back() {
        error = null
        when (step) {
            Step.CalmPhoto -> onCancel()
            Step.CalmFrame -> step = Step.CalmPhoto
            Step.AngryPhoto -> step = Step.CalmFrame
            Step.AngryFrame -> step = Step.AngryPhoto
            Step.Finish -> step = if (angry != null) Step.AngryFrame else Step.AngryPhoto
        }
    }
    BackHandler(onBack = ::back)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = ::back) {
                Text(if (step == Step.CalmPhoto) "‹ Cancel" else "‹ Back", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Step ${stepNumber(step)} of 3",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.height(8.dp))

        val (title, subtitle) = texts(step, calm, angry)
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(16.dp))

        Box(contentAlignment = Alignment.Center) {
            when (step) {
                Step.CalmPhoto, Step.AngryPhoto -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Keyed on the step so the angry shot starts fresh rather than reusing the calm camera session.
                    key(step) {
                        CameraCapture(onPhoto = ::accept, onPickFromGallery = ::pickFromGallery)
                    }
                    if (step == Step.AngryPhoto) {
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { angry = null; step = Step.Finish }) {
                            Text("Skip: fake an angry face for me", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                Step.CalmFrame -> FrameStep(calm!!, onRetake = { step = Step.CalmPhoto }, onNext = { step = Step.AngryPhoto })
                Step.AngryFrame -> FrameStep(angry!!, onRetake = { step = Step.AngryPhoto }, onNext = { step = Step.Finish })
                Step.Finish -> FinishStep(calm!!, angry, onSaved = onSaved)
            }
            working?.let { WorkingOverlay(it) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun stepNumber(step: Step) = when (step) {
    Step.CalmPhoto, Step.CalmFrame -> 1
    Step.AngryPhoto, Step.AngryFrame -> 2
    Step.Finish -> 3
}

private fun texts(step: Step, calm: Shot?, angry: Shot?): Pair<String, String> = when (step) {
    Step.CalmPhoto -> "Normal face" to "Put the head in the oval and look normal. Or pick a photo from the gallery."
    Step.AngryPhoto -> "Now look ANGRY!" to "Frown, glare, yell, show some teeth. The angrier, the funnier."
    Step.CalmFrame, Step.AngryFrame -> {
        val found = (if (step == Step.CalmFrame) calm else angry)?.analysis?.faceFound == true
        "Fit the head in the circle" to
            if (found) "Drag and pinch to fine-tune." else "Couldn't spot a face automatically. Drag and pinch to frame it."
    }
    Step.Finish -> "Who is this?" to "Give them a name, so everyone knows who's furious. And maybe a yell."
}

@Composable
private fun FrameStep(shot: Shot, onRetake: () -> Unit, onNext: () -> Unit) {
    Column {
        val image = if (shot.removeBackground) shot.cutout ?: shot.source else shot.source
        FrameEditor(image = image, crop = shot.crop, onCropChange = { shot.crop = it })
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Remove background", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                if (shot.cutout == null) {
                    Text("No person found in this photo", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                }
            }
            Switch(
                checked = shot.removeBackground,
                onCheckedChange = { shot.removeBackground = it },
                enabled = shot.cutout != null,
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) { Text("Retake") }
            Button(onClick = onNext, modifier = Modifier.weight(1f)) { Text("Next") }
        }
    }
}

@Composable
private fun FinishStep(calm: Shot, angry: Shot?, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<Result?>(null) }
    var name by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    // A fresh draft for every new head; it is copied into the head's folder on save.
    val yellDraft = remember { File(context.cacheDir, "yell_draft.m4a").apply { delete() } }
    var hasYell by remember { mutableStateOf(false) }

    LaunchedEffect(calm, angry) {
        result = withContext(Dispatchers.Default) {
            Result(
                calm = HeadProcessor.render(calm.analysis, calm.crop, calm.removeBackground),
                angry = angry?.let { HeadProcessor.render(it.analysis, it.crop, it.removeBackground) },
                eyes = HeadProcessor.eyesIn(calm.analysis, calm.crop),
            )
        }
    }

    val r = result
    if (r == null) {
        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val preview = remember(r, name) { CustomHead("preview", name, r.calm.asImageBitmap(), r.angry?.asImageBitmap(), r.eyes) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeadPreview(preview, Expression.Calm, "Normal", Modifier.weight(1f))
            HeadPreview(preview, Expression.Angry, if (r.angry != null) "Angry" else "Angry (faked)", Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(24) },
            label = { Text("Name") },
            placeholder = { Text("e.g. Uncle Bob") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        YellRecorder(file = yellDraft, hasRecording = hasYell, onRecorded = { hasYell = true })
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                saving = true
                scope.launch {
                    HeadStore.save(context, name, r.calm, r.angry, r.eyes, yellDraft.takeIf { hasYell })
                    yellDraft.delete()
                    onSaved()
                }
            },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Save head", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun HeadPreview(head: CustomHead, expression: Expression, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp)),
        ) {
            drawPhotoHead(head, center, size.minDimension * 0.85f, expression, redness = if (expression == Expression.Angry) 1f else 0f)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun BoxScope.WorkingOverlay(text: String) {
    Box(
        Modifier
            .matchParentSize()
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(28.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Spacer(Modifier.height(12.dp))
            Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}
