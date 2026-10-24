package nl.angryuncle.heads

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shows [image] through a square window that is [crop], with everything outside the inscribed circle dimmed.
 * Drag to move, pinch to zoom; the head ends up as exactly what is inside the circle.
 */
@Composable
fun FrameEditor(image: ImageBitmap, crop: Crop, onCropChange: (Crop) -> Unit, modifier: Modifier = Modifier) {
    val currentCrop by rememberUpdatedState(crop)
    val minSide = min(image.width, image.height) * 0.15f
    val maxSide = max(image.width, image.height) * 2f

    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(image) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val c = currentCrop
                    val side = (c.side / zoom).coerceIn(minSide, maxSide)
                    // Screen pixels to photo pixels: the window is size.width wide and shows c.side photo pixels.
                    val perPx = c.side / size.width
                    onCropChange(
                        Crop(
                            cx = (c.cx - pan.x * perPx).coerceIn(0f, image.width.toFloat()),
                            cy = (c.cy - pan.y * perPx).coerceIn(0f, image.height.toFloat()),
                            side = side,
                        ),
                    )
                }
            },
    ) {
        val scale = size.width / crop.side
        drawImage(
            image,
            dstOffset = IntOffset(
                ((crop.side / 2 - crop.cx) * scale).roundToInt(),
                ((crop.side / 2 - crop.cy) * scale).roundToInt(),
            ),
            dstSize = IntSize((image.width * scale).roundToInt(), (image.height * scale).roundToInt()),
            filterQuality = FilterQuality.Medium,
        )

        val outsideCircle = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, size))
            addOval(Rect(center, size.minDimension / 2))
        }
        drawPath(outsideCircle, Color.Black.copy(alpha = 0.55f))
        drawCircle(Color.White, size.minDimension / 2 - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
    }
}
