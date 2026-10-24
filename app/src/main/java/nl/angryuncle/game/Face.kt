package nl.angryuncle.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import nl.angryuncle.heads.CustomHead
import kotlin.math.roundToInt

/** Whoever sits at the table: a drawn cartoon uncle or a head the user photographed. */
sealed interface Face {
    /** Shown in "… IS FURIOUS!"; null for the anonymous cartoon uncles. */
    val name: String?

    data class Cartoon(val look: UncleLook) : Face {
        override val name: String? get() = null
    }

    class Photo(val head: CustomHead) : Face {
        override val name get() = head.name.ifBlank { null }
    }
}

/** The face fills only the middle of a photo head (the rest is hair, neck, transparency), so draw it a bit larger. */
private const val PhotoScale = 1.05f

/** Where eyes sit in a framed head when face detection did not report them. */
private val DefaultEyes = Offset(0.38f, 0.47f) to Offset(0.62f, 0.47f)

fun DrawScope.drawFace(face: Face, center: Offset, size: Float, expression: Expression, redness: Float = 0f) {
    when (face) {
        is Face.Cartoon -> drawUncle(face.look, center, size, expression, redness)
        is Face.Photo -> drawPhotoHead(face.head, center, size * PhotoScale, expression, redness)
    }
}

/**
 * Draws a photo head. Angry uses the angry photo when there is one; otherwise it fakes it on the calm photo
 * with a red flush and furious eyebrows. Either way the anger vein pops up once he is red enough.
 */
fun DrawScope.drawPhotoHead(head: CustomHead, center: Offset, size: Float, expression: Expression, redness: Float = 0f) {
    val angry = expression == Expression.Angry
    val image = if (angry) head.angry ?: head.calm else head.calm
    // A real angry photo already shows the emotion, so it gets a lighter flush than a faked one.
    val flush = redness * if (angry && head.angry != null) 0.4f else 0.75f
    drawFitted(image, center, size, redFlush(flush))

    if (angry && head.angry == null) drawFakeBrows(head, center, size, (redness * 2f).coerceAtMost(1f))
    if (angry && redness > 0.3f) drawAngerVein(center + Offset(size * 0.2f, -size * 0.3f), size * 0.18f)
}

private fun DrawScope.drawFitted(image: ImageBitmap, center: Offset, size: Float, filter: ColorFilter?) {
    val s = size.roundToInt()
    drawImage(
        image,
        dstOffset = IntOffset((center.x - size / 2).roundToInt(), (center.y - size / 2).roundToInt()),
        dstSize = IntSize(s, s),
        colorFilter = filter,
        filterQuality = FilterQuality.Medium,
    )
}

/** Multiplies away green and blue and adds some red: a face going purple-red, transparent pixels untouched. */
private fun redFlush(amount: Float): ColorFilter? {
    if (amount <= 0f) return null
    val keep = 1f - 0.5f * amount
    return ColorFilter.lighting(multiply = Color(1f, keep, keep), add = Color(0.18f * amount, 0f, 0f))
}

/** Thick eyebrows slanting down towards the nose, drawn just above the detected eyes. */
private fun DrawScope.drawFakeBrows(head: CustomHead, center: Offset, size: Float, alpha: Float) {
    val (left, right) = head.eyes ?: DefaultEyes
    val origin = center - Offset(size / 2, size / 2)
    // Scale everything by the distance between the eyes, so brows fit close-ups and far shots alike.
    val eyeGap = (right.x - left.x).coerceIn(0.12f, 0.5f) * size
    for ((eye, toNose) in listOf(left to 1f, right to -1f)) {
        val e = origin + Offset(eye.x * size, eye.y * size)
        val outer = e + Offset(-toNose * eyeGap * 0.45f, -eyeGap * 0.5f)
        val inner = e + Offset(toNose * eyeGap * 0.35f, -eyeGap * 0.18f)
        drawLine(Color(0xFF1E1410).copy(alpha = alpha), outer, inner, eyeGap * 0.22f, StrokeCap.Round)
    }
}
