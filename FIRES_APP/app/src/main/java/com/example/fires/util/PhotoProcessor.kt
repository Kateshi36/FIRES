package com.example.fires.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import com.example.fires.data.model.IncidentPhoto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max

/**
 * Turns a photo the person picked into the small JPEG we store (D3):
 * check size and real type, fix the rotation, shrink, compress to about 200 KB, Base64.
 * The result is an [IncidentPhoto], ready for IncidentRepository.savePhoto(incidentId, photo),
 * which writes it to the incident_photos collection.
 * Never throws: problems come back as [Result.Failure] with a message for the screen.
 */
object PhotoProcessor {

    sealed interface Result {
        data class Success(val photo: IncidentPhoto) : Result
        data class Failure(val message: String) : Result
    }

    suspend fun process(context: Context, uri: Uri): Result = withContext(Dispatchers.Default) {
        try {
            run(context.applicationContext, uri)
        } catch (e: CancellationException) {
            throw e
        } catch (_: OutOfMemoryError) {
            Result.Failure(PhotoRules.MSG_FAILED)
        } catch (_: Exception) {
            Result.Failure(PhotoRules.MSG_FAILED)
        }
    }

    /** For the thumbnail on the form. Null if the text is empty or not an image. */
    fun decodePreview(base64: String): Bitmap? = try {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Exception) {
        null
    }

    private fun run(context: Context, uri: Uri): Result {
        // 1. Read the file, refusing anything over the size limit.
        val bytes = readLimited(context, uri) ?: return Result.Failure(PhotoRules.MSG_TOO_LARGE)
        PhotoRules.validateSourceSize(bytes.size.toLong())?.let { return Result.Failure(it) }

        // 2. Find out what the file really is by reading its header (not its name).
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        PhotoRules.validateType(bounds.outMimeType)?.let { return Result.Failure(it) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return Result.Failure(PhotoRules.MSG_NOT_IMAGE)

        // 3. Decode at a reduced size so a 12 MP photo never fills the phone's memory.
        var sample = 1
        val longest = max(bounds.outWidth, bounds.outHeight)
        while (longest / (sample * 2) >= PhotoRules.MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: return Result.Failure(PhotoRules.MSG_NOT_IMAGE)

        // 4. Phone cameras store photos sideways plus a rotation note. Apply it, then
        //    put transparent PNGs on white (JPEG has no transparency, it would turn black).
        val base = flattenOnWhite(rotateUpright(decoded, readOrientation(bytes)))

        // 5. Shrink and compress until it fits.
        for (attempt in PhotoRules.ATTEMPTS) {
            val (w, h) = PhotoRules.scaledSize(base.width, base.height, attempt.maxSide)
            val scaled = if (w == base.width && h == base.height) base
            else Bitmap.createScaledBitmap(base, w, h, true)

            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, attempt.quality, out)
            if (scaled !== base) scaled.recycle()

            val jpeg = out.toByteArray()
            if (jpeg.size <= PhotoRules.TARGET_MAX_BYTES) {
                return Result.Success(
                    IncidentPhoto(
                        base64 = Base64.encodeToString(jpeg, Base64.NO_WRAP),
                        mimeType = "image/jpeg",
                        sizeBytes = jpeg.size.toLong()
                    )
                )
            }
        }
        return Result.Failure(PhotoRules.MSG_FAILED)
    }

    /** The file's bytes, or null if it is bigger than the limit (read at most limit + a little). */
    private fun readLimited(context: Context, uri: Uri): ByteArray? {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open photo")
        input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > PhotoRules.MAX_SOURCE_BYTES) return null
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }
    }

    private fun readOrientation(bytes: ByteArray): Int = try {
        ExifInterface(ByteArrayInputStream(bytes))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (_: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun rotateUpright(source: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            else -> return source
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun flattenOnWhite(source: Bitmap): Bitmap {
        if (!source.hasAlpha()) return source
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(result).apply {
            drawColor(Color.WHITE)
            drawBitmap(source, 0f, 0f, null)
        }
        return result
    }
}
