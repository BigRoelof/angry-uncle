package nl.angryuncle.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.random.Random

enum class HairStyle { Bald, Tufts, CombOver, Flat }
enum class MustacheStyle { None, Walrus, Pencil, Beard }
enum class Expression { Calm, Surprised, Angry }

/** How one placeholder uncle looks. Every uncle can be the angry one, so the calm faces must not give it away. */
data class UncleLook(
    val skin: Color,
    val hair: Color,
    val hairStyle: HairStyle,
    val mustache: MustacheStyle,
    val glasses: Boolean,
    /** Head width relative to height, for some round and some long faces. */
    val width: Float,
) {
    companion object {
        private val skins = listOf(0xFFF6D2B4, 0xFFF0C09A, 0xFFE2A77F, 0xFFC68660, 0xFF9C6644, 0xFF7A4E33)
        private val hairs = listOf(0xFF3B2A20, 0xFF6B4B33, 0xFF9A9A9A, 0xFFD9D9D9, 0xFF1E1A18, 0xFFB5652F)

        fun random(rng: Random) = UncleLook(
            skin = Color(skins.random(rng)),
            hair = Color(hairs.random(rng)),
            hairStyle = HairStyle.entries.random(rng),
            mustache = MustacheStyle.entries.random(rng),
            glasses = rng.nextFloat() < 0.3f,
            width = 0.78f + rng.nextFloat() * 0.14f,
        )
    }
}

private val Ink = Color(0xFF2B1D16)
private val AngryRed = Color(0xFFE8443A)
private val Blush = Color(0x55FF7A7A)

/**
 * Draws [look] centred on [center], fitting a square of side [size].
 * [redness] (0..1) tints the skin towards furious red, so the reveal can fade it in.
 */
fun DrawScope.drawUncle(look: UncleLook, center: Offset, size: Float, expression: Expression, redness: Float = 0f) {
    val s = size
    val c = center
    val angry = expression == Expression.Angry
    val skin = lerp(look.skin, AngryRed, redness * 0.8f)
    val skinShade = lerp(skin, Ink, 0.15f)
    val headW = s * 0.8f * look.width
    val headH = s * 0.92f
    val headTop = c.y - headH / 2 + s * 0.03f

    // Ears behind the head.
    val earR = s * 0.09f
    drawCircle(skinShade, earR, Offset(c.x - headW / 2, c.y + s * 0.04f))
    drawCircle(skinShade, earR, Offset(c.x + headW / 2, c.y + s * 0.04f))

    drawOval(skin, Offset(c.x - headW / 2, headTop), Size(headW, headH))
    drawOval(Ink, Offset(c.x - headW / 2, headTop), Size(headW, headH), style = Stroke(s * 0.02f))

    drawHair(look, c, s, headW, headTop)

    if (expression == Expression.Calm) {
        drawCircle(Blush, s * 0.07f, Offset(c.x - headW * 0.32f, c.y + s * 0.12f))
        drawCircle(Blush, s * 0.07f, Offset(c.x + headW * 0.32f, c.y + s * 0.12f))
    }

    // Eyebrows: the inner ends drop when angry, rise when surprised.
    val browY = c.y - s * 0.08f
    val innerDy = when (expression) {
        Expression.Calm -> 0f
        Expression.Surprised -> -s * 0.06f
        Expression.Angry -> s * 0.07f
    }
    val browStroke = s * if (angry) 0.055f else 0.04f
    for (side in listOf(-1f, 1f)) {
        val outer = Offset(c.x + side * s * 0.25f, browY - if (angry) s * 0.04f else 0f)
        val inner = Offset(c.x + side * s * 0.07f, browY + innerDy)
        drawLine(look.hair.takeIf { it != Color(0xFFD9D9D9) } ?: Ink, outer, inner, browStroke, StrokeCap.Round)
    }

    // Eyes.
    val eyeY = c.y + s * 0.02f
    for (side in listOf(-1f, 1f)) {
        val eye = Offset(c.x + side * s * 0.15f, eyeY)
        when (expression) {
            Expression.Calm -> drawCircle(Ink, s * 0.035f, eye)
            Expression.Surprised -> {
                drawCircle(Color.White, s * 0.06f, eye)
                drawCircle(Ink, s * 0.03f, eye)
            }
            Expression.Angry -> {
                drawCircle(Color.White, s * 0.055f, eye)
                drawCircle(Ink, s * 0.022f, eye + Offset(-side * s * 0.012f, 0f))
            }
        }
    }

    if (look.glasses) {
        for (side in listOf(-1f, 1f)) {
            drawCircle(Ink, s * 0.085f, Offset(c.x + side * s * 0.15f, eyeY), style = Stroke(s * 0.018f))
        }
        drawLine(Ink, Offset(c.x - s * 0.065f, eyeY), Offset(c.x + s * 0.065f, eyeY), s * 0.018f)
    }

    // Nose.
    drawOval(skinShade, Offset(c.x - s * 0.05f, c.y + s * 0.06f), Size(s * 0.1f, s * 0.11f))

    drawMouth(c, s, expression)
    drawMustache(look, c, s, headW, headTop, headH)

    if (angry && redness > 0.3f) drawAngerVein(Offset(c.x + headW * 0.22f, headTop + headH * 0.2f), s * 0.16f)
}

private fun DrawScope.drawHair(look: UncleLook, c: Offset, s: Float, headW: Float, headTop: Float) {
    val hair = look.hair
    when (look.hairStyle) {
        HairStyle.Bald -> {
            // A shine on the dome is all there is.
            drawOval(Color.White.copy(alpha = 0.35f), Offset(c.x - s * 0.14f, headTop + s * 0.06f), Size(s * 0.14f, s * 0.07f))
        }
        HairStyle.Tufts -> for (side in listOf(-1f, 1f)) {
            val x = c.x + side * headW * 0.46f
            drawOval(hair, Offset(x - s * 0.065f, c.y - s * 0.33f), Size(s * 0.13f, s * 0.22f))
        }
        HairStyle.CombOver -> {
            val y = headTop + s * 0.1f
            for (i in 0..3) {
                val dy = i * s * 0.035f
                drawLine(
                    hair,
                    Offset(c.x - headW * 0.38f, y + dy + s * 0.04f),
                    Offset(c.x + headW * 0.3f, y + dy - s * 0.02f),
                    s * 0.02f,
                    StrokeCap.Round,
                )
            }
            drawOval(hair, Offset(c.x - headW * 0.5f, c.y - s * 0.2f), Size(s * 0.12f, s * 0.22f))
        }
        HairStyle.Flat -> {
            drawArc(hair, 180f, 180f, true, Offset(c.x - headW / 2, headTop - s * 0.02f), Size(headW, s * 0.42f))
            drawRect(hair, Offset(c.x - headW / 2, headTop + s * 0.17f), Size(headW, s * 0.03f))
        }
    }
}

private fun DrawScope.drawMouth(c: Offset, s: Float, expression: Expression) {
    val y = c.y + s * 0.27f
    when (expression) {
        Expression.Calm -> {
            val p = Path().apply {
                moveTo(c.x - s * 0.1f, y)
                quadraticTo(c.x, y + s * 0.07f, c.x + s * 0.1f, y)
            }
            drawPath(p, Ink, style = Stroke(s * 0.025f, cap = StrokeCap.Round))
        }
        Expression.Surprised -> drawOval(Ink, Offset(c.x - s * 0.05f, y - s * 0.03f), Size(s * 0.1f, s * 0.12f))
        Expression.Angry -> {
            // Yelling: a wide open mouth with a row of teeth.
            val topLeft = Offset(c.x - s * 0.14f, y - s * 0.04f)
            val mouth = Size(s * 0.28f, s * 0.15f)
            drawRoundRect(Ink, topLeft, mouth, CornerRadius(s * 0.06f))
            drawRoundRect(Color.White, topLeft + Offset(s * 0.03f, s * 0.012f), Size(mouth.width - s * 0.06f, s * 0.035f), CornerRadius(s * 0.01f))
            drawOval(Color(0xFFD9534F), Offset(c.x - s * 0.06f, y + s * 0.05f), Size(s * 0.12f, s * 0.05f))
        }
    }
}

private fun DrawScope.drawMustache(look: UncleLook, c: Offset, s: Float, headW: Float, headTop: Float, headH: Float) {
    val hair = look.hair
    val y = c.y + s * 0.19f
    when (look.mustache) {
        MustacheStyle.None -> Unit
        MustacheStyle.Pencil -> drawLine(hair, Offset(c.x - s * 0.1f, y), Offset(c.x + s * 0.1f, y), s * 0.025f, StrokeCap.Round)
        MustacheStyle.Walrus -> {
            val p = Path().apply {
                moveTo(c.x, y - s * 0.03f)
                quadraticTo(c.x - s * 0.14f, y - s * 0.07f, c.x - s * 0.2f, y + s * 0.06f)
                quadraticTo(c.x - s * 0.1f, y + s * 0.03f, c.x, y + s * 0.03f)
                quadraticTo(c.x + s * 0.1f, y + s * 0.03f, c.x + s * 0.2f, y + s * 0.06f)
                quadraticTo(c.x + s * 0.14f, y - s * 0.07f, c.x, y - s * 0.03f)
                close()
            }
            drawPath(p, hair)
        }
        MustacheStyle.Beard -> {
            // Chin beard hugging the bottom of the face, leaving room for the mouth.
            drawArc(
                hair, 15f, 150f, false,
                Offset(c.x - headW * 0.42f, headTop + headH * 0.3f), Size(headW * 0.84f, headH * 0.66f),
                style = Stroke(s * 0.09f, cap = StrokeCap.Round),
            )
        }
    }
}

/** The manga "💢" cross-popping vein: four bent strokes around a centre. */
fun DrawScope.drawAngerVein(center: Offset, size: Float) {
    val h = size / 2
    val gap = size * 0.12f
    val stroke = Stroke(size * 0.16f, cap = StrokeCap.Round)
    for ((sx, sy) in listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)) {
        val p = Path().apply {
            moveTo(center.x + sx * h, center.y + sy * gap)
            lineTo(center.x + sx * gap, center.y + sy * gap)
            lineTo(center.x + sx * gap, center.y + sy * h)
        }
        drawPath(p, Color(0xFFB0201A), style = stroke)
    }
}
