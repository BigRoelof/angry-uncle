package nl.angryuncle.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.angryuncle.game.BoardSize
import nl.angryuncle.game.Expression
import nl.angryuncle.game.Haptics
import nl.angryuncle.game.Sounds
import nl.angryuncle.game.Round
import nl.angryuncle.game.TapResult
import nl.angryuncle.game.Uncle
import nl.angryuncle.game.Face
import nl.angryuncle.game.drawFace
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** A tapped uncle on its way off the screen. Units are board pixels and seconds. */
private class Flyer(
    val uncle: Uncle,
    var x: Float,
    var y: Float,
    val vx: Float,
    var vy: Float,
    val gravity: Float,
    val spin: Float,
    var rotation: Float,
) {
    var age = 0f

    fun step(dt: Float) {
        age += dt
        x += vx * dt
        y += vy * dt
        vy += gravity * dt
        rotation += spin * dt
    }

    /** A quick squish on the tap, then it grows a little as it "jumps towards the camera". */
    val scale get() = (1f + 0.35f * min(age, 1f)) * (1f - 0.15f * (1f - age / 0.06f).coerceAtLeast(0f))
}

private const val FlyerLifetime = 1.6f
private const val ScrimReach = 10_000f

/** Allowed row height relative to column width. */
private val RowAspect = 1.0f..1.4f

/**
 * Head size such that the face (about 0.68 x 0.92 of it, see drawUncle) slightly overflows its cell,
 * so neighbours overlap like a packed crowd.
 */
private const val FaceWidth = 0.72f
private const val FaceHeight = 0.85f

@Composable
fun GameScreen(boardSize: BoardSize, cast: List<Face>, sounds: Sounds, onExit: () -> Unit) {
    val context = LocalContext.current
    val haptics = remember { Haptics(context) }
    val scope = rememberCoroutineScope()

    var round by remember { mutableStateOf(Round.new(boardSize, cast)) }
    val flyers = remember { mutableListOf<Flyer>() }
    var flying by remember { mutableStateOf(false) }
    // Bumped every physics frame so the board redraws; the flyers themselves are plain objects.
    var frame by remember { mutableLongStateOf(0L) }
    val reveal = remember { Animatable(0f) }
    val shake = remember { Animatable(0f) }
    var showLost by remember { mutableStateOf(false) }

    LaunchedEffect(flying) {
        if (!flying) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (flyers.isNotEmpty()) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                flyers.forEach { it.step(dt) }
                flyers.removeAll { it.age > FlyerLifetime }
                frame = now
            }
        }
        flying = false
    }

    // The lose sequence: a beat of silence, then uncle turns red, rumbles and fills the screen.
    LaunchedEffect(round.lost) {
        if (!round.lost) return@LaunchedEffect
        delay(150)
        haptics.angry()
        sounds.angryReveal()
        launch {
            // The yell lands as his face goes red, just after the hit.
            delay(250)
            sounds.yell((round.uncles[round.angryId].face as? Face.Photo)?.head?.yell)
        }
        launch {
            shake.snapTo(1f)
            shake.animateTo(0f, tween(800))
        }
        reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        showLost = true
    }

    fun restart() {
        scope.launch {
            showLost = false
            flyers.clear()
            reveal.snapTo(0f)
            round = Round.new(boardSize, cast)
        }
    }

    val density = LocalDensity.current
    val shakePx = with(density) { 14.dp.toPx() }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .graphicsLayer {
                val s = shake.value
                translationX = sin(s * 60f) * shakePx * s
                translationY = sin(s * 47f) * shakePx * 0.5f * s
            },
    ) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onExit) { Text("‹ Menu", style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${round.remaining} uncles left",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
            Text(
                text = when {
                    round.lost -> "Uh oh…"
                    round.remaining == 1 -> "Only one left. Good luck."
                    else -> "Tap an uncle… if you dare"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )

            // A background rather than a Surface: a Surface clips, and flyers should leave the board.
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp)),
            ) {
                Board(
                    round = round,
                    flyers = flyers,
                    frame = { frame },
                    reveal = { reveal.value },
                    showLost = showLost,
                    onTap = { uncle, center, head ->
                        val (next, result) = round.tap(uncle.id)
                        round = next
                        when (result) {
                            TapResult.Safe -> {
                                haptics.safeTap()
                                sounds.safeTap()
                                val dir = if (Random.nextBoolean()) 1f else -1f
                                flyers += Flyer(
                                    uncle = uncle,
                                    x = center.x,
                                    y = center.y,
                                    vx = dir * head * (1.5f + Random.nextFloat() * 2.5f),
                                    vy = -head * (7f + Random.nextFloat() * 2f),
                                    gravity = head * 24f,
                                    spin = dir * (360f + Random.nextFloat() * 540f),
                                    rotation = uncle.tilt,
                                )
                                flying = true
                            }
                            TapResult.Angry, TapResult.Ignored -> Unit
                        }
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = showLost,
            enter = slideInVertically { it } + fadeIn(),
            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding(),
        ) {
            LostPanel(
                name = round.uncles[round.angryId].face.name,
                safeTaps = round.safeTaps,
                onPlayAgain = ::restart,
                onMenu = onExit,
            )
        }
    }
}

@Composable
private fun Board(
    round: Round,
    flyers: List<Flyer>,
    frame: () -> Long,
    reveal: () -> Float,
    showLost: Boolean,
    onTap: (uncle: Uncle, center: Offset, head: Float) -> Unit,
) {
    val currentRound by rememberUpdatedState(round)
    val currentOnTap by rememberUpdatedState(onTap)

    BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp)) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        // Faces are taller than wide, so rows may be up to 1.4x as tall as columns are wide: fills tall phones.
        val cellX = min(w / round.columns, h / round.rows / RowAspect.start)
        val cellY = min(h / round.rows, cellX * RowAspect.endInclusive)
        val head = min(cellX / FaceWidth, cellY / FaceHeight)
        val origin = Offset((w - cellX * round.columns) / 2, (h - cellY * round.rows) / 2)

        fun centerOf(u: Uncle) = origin + Offset(
            (u.id % round.columns + 0.5f + u.jitterX) * cellX,
            (u.id / round.columns + 0.5f + u.jitterY) * cellY,
        )

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(cellX, cellY, origin) {
                    detectTapGestures { p ->
                        val r = currentRound
                        // Later uncles are drawn on top, so search from the end to hit the visible one.
                        r.uncles.lastOrNull { r.isOnBoard(it.id) && (centerOf(it) - p).getDistance() < head * 0.45f }
                            ?.let { currentOnTap(it, centerOf(it), head) }
                    }
                },
        ) {
            frame()
            for (u in round.uncles) {
                if (!round.isOnBoard(u.id) || (round.lost && u.id == round.angryId)) continue
                rotate(u.tilt, centerOf(u)) { drawFace(u.face, centerOf(u), head, Expression.Calm) }
            }
            for (f in flyers) {
                val c = Offset(f.x, f.y)
                rotate(f.rotation, c) { drawFace(f.uncle.face, c, head * f.scale, Expression.Surprised) }
            }
        }

        if (round.lost) {
            val angry = round.uncles[round.angryId]
            AngryReveal(angry, from = centerOf(angry), head = head, reveal = reveal, steaming = showLost)
        }
    }
}

/** Dims the board and blows the angry uncle up to fill it. */
@Composable
private fun AngryReveal(uncle: Uncle, from: Offset, head: Float, reveal: () -> Float, steaming: Boolean) {
    val steam = if (steaming) {
        rememberInfiniteTransition(label = "steam")
            .animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "puff")
    } else {
        null
    }

    Canvas(Modifier.fillMaxSize()) {
        val t = reveal()
        // First quarter: turn red in place. Rest: grow towards the centre.
        val redness = (t / 0.25f).coerceIn(0f, 1f)
        val grow = ((t - 0.25f) / 0.75f).coerceIn(0f, 1f)
        val big = min(size.width, size.height) * 0.85f
        val sz = lerp(head, big, grow)
        val tremble = steam?.value?.let { sin(it * 6.283f * 4) * sz * 0.008f } ?: 0f
        // Sit above the middle, clear of the lose panel at the bottom.
        val c = lerp(from, Offset(center.x, size.height * 0.4f), grow) + Offset(tremble, 0f)

        // Canvas does not clip, so the scrim can darken the whole screen, not just the board.
        drawRect(Color.Black.copy(alpha = 0.55f * grow), Offset(-ScrimReach, -ScrimReach), Size(ScrimReach * 3, ScrimReach * 3))
        rotate(lerp(uncle.tilt, 0f, grow), c) {
            drawFace(uncle.face, c, sz, if (t > 0f) Expression.Angry else Expression.Calm, redness)
        }
        steam?.let { drawSteam(c, sz, it.value) }
    }
}

/** Three puffs rising from each side of the head, looping. */
private fun DrawScope.drawSteam(c: Offset, sz: Float, t: Float) {
    for (side in listOf(-1f, 1f)) {
        for (i in 0 until 3) {
            val p = (t + i / 3f) % 1f
            val origin = c + Offset(side * sz * 0.42f, -sz * 0.02f)
            val pos = origin + Offset(side * p * sz * 0.12f, -p * sz * 0.3f)
            drawCircle(Color.White.copy(alpha = (1f - p) * 0.9f), sz * (0.02f + 0.04f * p), pos)
        }
    }
}

@Composable
private fun LostPanel(name: String?, safeTaps: Int, onPlayAgain: () -> Unit, onMenu: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 8.dp,
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${(name ?: "Uncle").uppercase()} IS FURIOUS!",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when (safeTaps) {
                    0 -> "First tap, and you found him. Impressive."
                    1 -> "The table survived 1 tap."
                    else -> "The table survived $safeTaps taps."
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onMenu) { Text("Menu") }
                Spacer(Modifier.width(12.dp))
                Button(onClick = onPlayAgain) { Text("Play again") }
            }
        }
    }
}
