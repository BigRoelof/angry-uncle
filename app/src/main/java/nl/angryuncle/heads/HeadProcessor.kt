package nl.angryuncle.heads

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import androidx.camera.core.ImageProxy
import androidx.compose.ui.geometry.Offset
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min

/** A square region of the source photo, in source pixels. The head is the circle inscribed in it. */
data class Crop(val cx: Float, val cy: Float, val side: Float)

/**
 * What ML Kit found in a photo.
 * [cutout] is the photo with the background made transparent, or null if no person was found.
 * [crop] is the suggested framing around the largest face; [faceFound] says whether it came from a real face.
 */
class Analysis(
    val source: Bitmap,
    val cutout: Bitmap?,
    val crop: Crop,
    val faceFound: Boolean,
    val leftEye: PointF?,
    val rightEye: PointF?,
)

object HeadProcessor {
    /** Saved heads are this many pixels square: sharp on a big reveal, small enough to keep 40 on screen. */
    const val OutputSize = 512

    /** Photos are scaled down to this on their longest side before any processing. */
    private const val MaxSide = 1600

    suspend fun loadUri(context: Context, uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        fun open() = resolver.openInputStream(uri) ?: error("Could not open $uri")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MaxSide) sample *= 2
        val decoded = open().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: error("Not an image: $uri")

        val matrix = Matrix()
        fitScale(decoded)?.let { matrix.postScale(it, it) }
        // Gallery photos are often stored sideways with an EXIF note saying so.
        when (open().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
        }
        transformed(decoded, matrix)
    }

    /** Turns a camera capture upright, mirrored like the preview was for the front camera. Closes [image]. */
    fun fromCapture(image: ImageProxy, mirror: Boolean): Bitmap {
        val rotation = image.imageInfo.rotationDegrees
        val raw = image.use { it.toBitmap() }
        val matrix = Matrix()
        fitScale(raw)?.let { matrix.postScale(it, it) }
        matrix.postRotate(rotation.toFloat())
        if (mirror) matrix.postScale(-1f, 1f)
        return transformed(raw, matrix)
    }

    private fun fitScale(b: Bitmap): Float? = (MaxSide.toFloat() / max(b.width, b.height)).takeIf { it < 1f }

    private fun transformed(b: Bitmap, matrix: Matrix): Bitmap =
        if (matrix.isIdentity) b else Bitmap.createBitmap(b, 0, 0, b.width, b.height, matrix, true)

    suspend fun analyze(source: Bitmap): Analysis = withContext(Dispatchers.Default) {
        val bitmap = if (source.config == Bitmap.Config.ARGB_8888) source else source.copy(Bitmap.Config.ARGB_8888, false)
        val input = InputImage.fromBitmap(bitmap, 0)

        val faceDetector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .build(),
        )
        val face = try {
            faceDetector.process(input).await().maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
        } finally {
            faceDetector.close()
        }

        val crop = face?.boundingBox?.let { box ->
            // The box runs roughly brows to chin; the head with hair is about twice as tall, and sits a bit higher.
            Crop(box.exactCenterX(), box.exactCenterY() - box.height() * 0.12f, box.height() * 2f)
        } ?: Crop(bitmap.width / 2f, bitmap.height / 2f, min(bitmap.width, bitmap.height) * 0.8f)

        Analysis(
            source = bitmap,
            cutout = runCatching {
                cutout(bitmap, input, seed = face?.boundingBox?.let { PointF(it.exactCenterX(), it.exactCenterY()) })
            }.getOrNull(),
            crop = crop,
            faceFound = face != null,
            leftEye = face?.getLandmark(FaceLandmark.LEFT_EYE)?.position,
            rightEye = face?.getLandmark(FaceLandmark.RIGHT_EYE)?.position,
        )
    }

    /**
     * The photo with everything but the person made transparent, softly at the edges.
     * With a [seed] (the face centre), only the person connected to it is kept: crowds behind them are dropped.
     */
    private suspend fun cutout(bitmap: Bitmap, input: InputImage, seed: PointF?): Bitmap? {
        val segmenter = Segmentation.getClient(
            SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).build(),
        )
        val mask = try {
            segmenter.process(input).await()
        } finally {
            segmenter.close()
        }
        val w = bitmap.width
        val h = bitmap.height
        if (mask.width != w || mask.height != h) return null

        val buffer = mask.buffer.apply { rewind() }
        val alpha = FloatArray(w * h) {
            // Smoothstep 0.3..0.7: hard enough to drop the background, soft enough not to look cut with scissors.
            val t = ((buffer.getFloat() - 0.3f) / 0.4f).coerceIn(0f, 1f)
            t * t * (3 - 2 * t)
        }

        seed?.let { keepConnected(alpha, w, h, it.x.toInt().coerceIn(0, w - 1), it.y.toInt().coerceIn(0, h - 1)) }

        // The model's mask is low resolution and comes out in stair steps once scaled up; blur them away.
        val r = max(1, max(w, h) / 400)
        boxBlur(alpha, w, h, r)
        boxBlur(alpha, w, h, r)

        // Almost nobody found: better to keep the background than to show an empty circle.
        if (alpha.count { it > 0.5f } < alpha.size / 50) return null

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            pixels[i] = (pixels[i] and 0x00FFFFFF) or ((alpha[i] * 255).toInt().coerceIn(0, 255) shl 24)
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Zeroes every part of [alpha] that is not connected to (sx, sy), keeping a soft margin around the kept region.
     * Leaves [alpha] alone if the seed is not on a person, e.g. when the face box centre falls on a gap.
     */
    private fun keepConnected(alpha: FloatArray, w: Int, h: Int, sx: Int, sy: Int) {
        val start = sy * w + sx
        if (alpha[start] < 0.5f) return
        val kept = FloatArray(alpha.size)
        val queue = IntArray(alpha.size)
        var head = 0
        var tail = 0
        queue[tail++] = start
        kept[start] = 1f
        fun visit(n: Int) {
            if (kept[n] == 0f && alpha[n] >= 0.5f) {
                kept[n] = 1f
                queue[tail++] = n
            }
        }
        while (head < tail) {
            val i = queue[head++]
            val x = i % w
            if (x > 0) visit(i - 1)
            if (x < w - 1) visit(i + 1)
            if (i >= w) visit(i - w)
            if (i + w < alpha.size) visit(i + w)
        }
        // The flood stops at alpha 0.5, which would shave off the soft edge. Grow the kept region a little first.
        val margin = max(2, max(w, h) / 150)
        boxBlur(kept, w, h, margin)
        for (i in alpha.indices) alpha[i] = min(alpha[i], (kept[i] * 2f).coerceAtMost(1f))
    }

    /** In-place box blur of a w×h grid with radius [r], horizontal then vertical; repeat it for a softer blur. */
    private fun boxBlur(v: FloatArray, w: Int, h: Int, r: Int) {
        val tmp = FloatArray(v.size)
        val norm = 1f / (2 * r + 1)
        for (y in 0 until h) {
            val row = y * w
            var sum = 0f
            for (k in -r..r) sum += v[row + k.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                tmp[row + x] = sum * norm
                sum += v[row + (x + r + 1).coerceAtMost(w - 1)] - v[row + (x - r).coerceAtLeast(0)]
            }
        }
        for (x in 0 until w) {
            var sum = 0f
            for (k in -r..r) sum += tmp[k.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                v[y * w + x] = sum * norm
                sum += tmp[(y + r + 1).coerceAtMost(h - 1) * w + x] - tmp[(y - r).coerceAtLeast(0) * w + x]
            }
        }
    }

    /** Renders the framed head: [crop] scaled to [OutputSize], then cut to a soft-edged circle. */
    fun render(analysis: Analysis, crop: Crop, removeBackground: Boolean): Bitmap {
        val n = OutputSize.toFloat()
        val out = Bitmap.createBitmap(OutputSize, OutputSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val image = if (removeBackground) analysis.cutout ?: analysis.source else analysis.source

        canvas.save()
        canvas.scale(n / crop.side, n / crop.side)
        canvas.translate(-(crop.cx - crop.side / 2), -(crop.cy - crop.side / 2))
        canvas.drawBitmap(image, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()

        val r = n / 2
        val circle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                r, r, r,
                intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT),
                floatArrayOf(0f, 0.94f, 1f),
                Shader.TileMode.CLAMP,
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawRect(0f, 0f, n, n, circle)
        return out
    }

    /** Eye positions as fractions of the rendered head, for drawing fake angry eyebrows later. */
    fun eyesIn(analysis: Analysis, crop: Crop): Pair<Offset, Offset>? {
        val l = analysis.leftEye ?: return null
        val r = analysis.rightEye ?: return null
        fun norm(p: PointF) = Offset((p.x - (crop.cx - crop.side / 2)) / crop.side, (p.y - (crop.cy - crop.side / 2)) / crop.side)
        // ML Kit's "left eye" is the person's left, which is on the right of the picture. Store picture order.
        return listOf(norm(l), norm(r)).sortedBy { it.x }.let { it[0] to it[1] }
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}
