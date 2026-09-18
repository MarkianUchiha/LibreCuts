package com.tharunbirla.librecuts.customviews

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.tharunbirla.librecuts.models.EditOperation
import com.tharunbirla.librecuts.utils.GifFrameSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ImageOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }
    private var imageOperations: List<EditOperation.AddImageOverlay> = emptyList()
    
    // Caches
    private val bitmapCache = mutableMapOf<String, Bitmap>()
    private val gifCache = mutableMapOf<String, GifFrameSource>()
    private val retrieverCache = mutableMapOf<String, android.media.MediaMetadataRetriever>()
    private val lastFrameCache = mutableMapOf<String, Pair<Long, Bitmap>>()
    
    // Memory pools to prevent GC churn during playback
    private val reusableBitmaps = mutableMapOf<String, Bitmap>()
    private val reusableIntArrays = mutableMapOf<String, IntArray>()
    
    private val pendingFetches = mutableSetOf<String>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var videoWidth = 0
    private var videoHeight = 0
    
    var currentPositionMs: Long = 0L
        set(value) {
            field = value
            invalidate()
        }
        
    var hiddenOperationId: String? = null
        set(value) {
            field = value
            invalidate()
        }

    // Rect y rotación de cada imagen en el último onDraw, en orden de dibujado (la última queda
    // arriba). Se guardan aunque el bitmap aún no esté decodificado: la imagen ya ocupa su lugar.
    private val drawnImageBounds = LinkedHashMap<String, Pair<RectF, Float>>()
    private val hitSlopPx = 16f * resources.displayMetrics.density

    var onImageTapped: ((operationId: String) -> Unit)? = null

    /**
     * Se consulta en cada DOWN. La Activity lo apaga cuando otro modo de edición es dueño del
     * toque (texto, subtítulos, recorte, audio). Es una función y no un Boolean para leer el estado
     * real en ese momento, sin sincronizar un flag desde cada camino que abre o cierra un modo.
     */
    var canSelectByTap: () -> Boolean = { true }

    private val tapDetector = android.view.GestureDetector(context, object : android.view.GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: android.view.MotionEvent): Boolean = true

        override fun onSingleTapUp(e: android.view.MotionEvent): Boolean {
            findImageAt(e.x, e.y)?.let { onImageTapped?.invoke(it) }
            return true
        }
    })

    /** Id de la imagen visible bajo (x, y), considerando su rotación y un margen táctil. */
    fun findImageAt(x: Float, y: Float): String? =
        drawnImageBounds.entries.reversed().firstOrNull { (_, bounds) ->
            val (rect, rotation) = bounds
            // Se lleva el punto al marco sin rotar de la imagen en vez de rotar el rect.
            val point = floatArrayOf(x, y)
            android.graphics.Matrix().apply { setRotate(-rotation, rect.centerX(), rect.centerY()) }.mapPoints(point)
            point[0] >= rect.left - hitSlopPx && point[0] <= rect.right + hitSlopPx &&
                point[1] >= rect.top - hitSlopPx && point[1] <= rect.bottom + hitSlopPx
        }?.key

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (onImageTapped == null) return super.onTouchEvent(event)
        // Solo se reclama el toque si empieza sobre una imagen; si no, sigue a las vistas de abajo.
        if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN &&
            (!canSelectByTap() || findImageAt(event.x, event.y) == null)
        ) {
            return false
        }
        return tapDetector.onTouchEvent(event)
    }

    fun setImageOperations(operations: List<EditOperation.AddImageOverlay>) {
        this.imageOperations = operations
        
        // Cache static images with their chroma key properties considered
        scope.launch(Dispatchers.Default) {
            for (op in operations) {
                val path = op.imageUri.path ?: continue
                val isGif = path.endsWith(".gif", ignoreCase = true)
                val isVideo = path.endsWith(".mp4", ignoreCase = true) ||
                              path.endsWith(".mkv", ignoreCase = true) ||
                              path.endsWith(".mov", ignoreCase = true) ||
                              path.endsWith(".3gp", ignoreCase = true)

                if (!isGif && !isVideo) {
                    val cacheKey = "${op.imageUri}_${op.chromaKeyColor}_${op.chromaKeySimilarity}"
                    if (!bitmapCache.containsKey(cacheKey)) {
                        try {
                            val file = File(path)
                            if (file.exists()) {
                                var bitmap = BitmapFactory.decodeFile(file.absolutePath)
                                if (bitmap != null) {
                                    try {
                                        val exif = android.media.ExifInterface(file.absolutePath)
                                        val orientation = exif.getAttributeInt(
                                            android.media.ExifInterface.TAG_ORIENTATION,
                                            android.media.ExifInterface.ORIENTATION_NORMAL
                                        )
                                        val matrix = android.graphics.Matrix()
                                        var needsRotation = false
                                        when (orientation) {
                                            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> { matrix.postRotate(90f); needsRotation = true }
                                            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> { matrix.postRotate(180f); needsRotation = true }
                                            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> { matrix.postRotate(270f); needsRotation = true }
                                        }
                                        if (needsRotation) {
                                            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                if (bitmap != null && op.chromaKeyColor != null) {
                                    bitmap = applyChromaKey(bitmap, op.chromaKeyColor!!, op.chromaKeySimilarity, cacheKey)
                                }
                                if (bitmap != null) {
                                    withContext(Dispatchers.Main) {
                                        bitmapCache[cacheKey] = bitmap
                                        invalidate()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        for (retriever in retrieverCache.values) {
            try {
                retriever.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        retrieverCache.clear()
        lastFrameCache.clear()
        gifCache.clear()
        bitmapCache.clear()
        reusableBitmaps.values.forEach { it.recycle() }
        reusableBitmaps.clear()
        reusableIntArrays.clear()
        pendingFetches.clear()
    }

    fun setVideoSize(width: Int, height: Int) {
        this.videoWidth = width
        this.videoHeight = height
        invalidate()
    }

    private fun getVideoRect(): RectF {
        val rect = RectF()
        if (width <= 0 || height <= 0 || videoWidth <= 0 || videoHeight <= 0) {
            rect.set(0f, 0f, width.toFloat(), height.toFloat())
            return rect
        }

        val containerRatio = width.toFloat() / height
        val videoRatio = videoWidth.toFloat() / videoHeight

        if (videoRatio > containerRatio) {
            val h = width / videoRatio
            val top = (height - h) / 2f
            rect.set(0f, top, width.toFloat(), top + h)
        } else {
            val w = height * videoRatio
            val left = (width - w) / 2f
            rect.set(left, 0f, left + w, height.toFloat())
        }
        return rect
    }
    
    private fun applyChromaKey(bitmap: Bitmap, colorHex: String, similarity: Float, cacheKey: String): Bitmap {
        try {
            val color = android.graphics.Color.parseColor(colorHex)
            val targetR = android.graphics.Color.red(color)
            val targetG = android.graphics.Color.green(color)
            val targetB = android.graphics.Color.blue(color)
            
            val width = bitmap.width
            val height = bitmap.height
            val pixelCount = width * height
            
            var pixels = reusableIntArrays[cacheKey]
            if (pixels == null || pixels.size != pixelCount) {
                pixels = IntArray(pixelCount)
                reusableIntArrays[cacheKey] = pixels
            }
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            
            val maxDistSq = 255f * 255f * 3f
            val simSq = similarity * similarity * maxDistSq
            val blendSq = 0.1f * 0.1f * maxDistSq
            
            for (i in 0 until pixelCount) {
                val p = pixels[i]
                val a = (p shr 24) and 0xFF
                if (a == 0) continue
                
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                
                val diffR = r - targetR
                val diffG = g - targetG
                val diffB = b - targetB
                val distSq = (diffR * diffR + diffG * diffG + diffB * diffB).toFloat()
                
                if (distSq < simSq) {
                    if (blendSq > 0 && distSq > simSq - blendSq) {
                        val alphaMult = (distSq - (simSq - blendSq)) / blendSq
                        val newA = (a * alphaMult).toInt().coerceIn(0, 255)
                        pixels[i] = (newA shl 24) or (r shl 16) or (g shl 8) or b
                    } else {
                        pixels[i] = 0
                    }
                }
            }
            var keyed = reusableBitmaps["${cacheKey}_keyed"]
            if (keyed == null || keyed.width != width || keyed.height != height) {
                keyed = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                reusableBitmaps["${cacheKey}_keyed"] = keyed
            }
            keyed.setPixels(pixels, 0, width, 0, 0, width, height)
            return keyed
        } catch (e: Exception) {
            return bitmap
        }
    }

    private fun interpolateKeyframes(
        keyframes: List<EditOperation.KeyframePoint>,
        timeMs: Long,
        defaultValue: Float
    ): Float {
        if (keyframes.isEmpty()) return defaultValue
        val sorted = keyframes.sortedBy { it.timeMs }
        if (timeMs <= sorted.first().timeMs) {
            return sorted.first().valueX
        }
        if (timeMs >= sorted.last().timeMs) {
            return sorted.last().valueX
        }
        for (i in 0 until sorted.size - 1) {
            val k1 = sorted[i]
            val k2 = sorted[i + 1]
            if (timeMs >= k1.timeMs && timeMs <= k2.timeMs) {
                val progress = (timeMs - k1.timeMs).toFloat() / (k2.timeMs - k1.timeMs)
                return k1.valueX + progress * (k2.valueX - k1.valueX)
            }
        }
        return defaultValue
    }

    private fun interpolateKeyframePosition(
        keyframes: List<EditOperation.KeyframePoint>,
        timeMs: Long,
        defaultX: Float,
        defaultY: Float
    ): Pair<Float, Float> {
        if (keyframes.isEmpty()) return Pair(defaultX, defaultY)
        val sorted = keyframes.sortedBy { it.timeMs }
        if (timeMs <= sorted.first().timeMs) {
            return Pair(sorted.first().valueX, sorted.first().valueY)
        }
        if (timeMs >= sorted.last().timeMs) {
            return Pair(sorted.last().valueX, sorted.last().valueY)
        }
        for (i in 0 until sorted.size - 1) {
            val k1 = sorted[i]
            val k2 = sorted[i + 1]
            if (timeMs >= k1.timeMs && timeMs <= k2.timeMs) {
                val progress = (timeMs - k1.timeMs).toFloat() / (k2.timeMs - k1.timeMs)
                val x = k1.valueX + progress * (k2.valueX - k1.valueX)
                val y = k1.valueY + progress * (k2.valueY - k1.valueY)
                return Pair(x, y)
            }
        }
        return Pair(defaultX, defaultY)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val videoRect = getVideoRect()
        drawnImageBounds.clear()

        for (op in imageOperations) {
            if (op.id == hiddenOperationId) continue
            val start = op.startTimeMs ?: 0L
            val end = op.endTimeMs ?: Long.MAX_VALUE
            if (currentPositionMs < start || currentPositionMs > end) continue

            val path = op.imageUri.path ?: continue
            val isGif = path.endsWith(".gif", ignoreCase = true)
            val isVideo = path.endsWith(".mp4", ignoreCase = true) ||
                          path.endsWith(".mkv", ignoreCase = true) ||
                          path.endsWith(".mov", ignoreCase = true) ||
                          path.endsWith(".3gp", ignoreCase = true)

            val relativeTimeMs = currentPositionMs - start
            val interpolatedPos = if (op.positionKeyframes.isNotEmpty()) {
                interpolateKeyframePosition(op.positionKeyframes, relativeTimeMs, op.relativeX, op.relativeY)
            } else {
                Pair(op.relativeX, op.relativeY)
            }
            val interpolatedOpacity = if (op.opacityKeyframes.isNotEmpty()) {
                interpolateKeyframes(op.opacityKeyframes, relativeTimeMs, op.opacity)
            } else {
                op.opacity
            }
            val interpolatedOp = op.copy(
                relativeX = interpolatedPos.first,
                relativeY = interpolatedPos.second,
                opacity = interpolatedOpacity
            )
            drawnImageBounds[op.id] = computeDstRect(interpolatedOp, videoRect) to op.rotationAngle

            if (isGif || isVideo) {
                val speed = if (op.speedKeyframes.isNotEmpty()) {
                    interpolateKeyframes(op.speedKeyframes, relativeTimeMs, 1.0f)
                } else {
                    1.0f
                }
                var effectiveTimeMs = (relativeTimeMs * speed).toLong()
                
                if (isGif) {
                    var gif = gifCache[op.imageUri.toString()]
                    if (gif == null) {
                        try {
                            gif = GifFrameSource.decode(path)
                            if (gif != null) gifCache[op.imageUri.toString()] = gif
                        } catch (e: Exception) { }
                    }
                    if (gif != null && gif.durationMs > 0) {
                        effectiveTimeMs = if (op.isLooping) {
                            effectiveTimeMs % gif.durationMs
                        } else {
                            Math.min(effectiveTimeMs, (gif.durationMs - 1).toLong())
                        }
                    }
                } else {
                    var retriever = retrieverCache[op.imageUri.toString()]
                    if (retriever == null) {
                        try {
                            retriever = android.media.MediaMetadataRetriever()
                            retriever.setDataSource(path)
                            retrieverCache[op.imageUri.toString()] = retriever
                        } catch (e: Exception) { }
                    }
                    if (retriever != null) {
                        val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                        val durationMs = durationStr?.toLongOrNull() ?: 1L
                        effectiveTimeMs = if (op.isLooping && durationMs > 0) effectiveTimeMs % durationMs else effectiveTimeMs
                    }
                }
                
                val cacheKey = "${op.id}"
                val cached = lastFrameCache[cacheKey]
                
                // We use a relatively high threshold (100ms) to avoid over-fetching
                // As long as the fetched frame is close to what we need, we show it
                if (cached != null && Math.abs(cached.first - effectiveTimeMs) < 100) {
                    drawBitmapOp(canvas, cached.second, interpolatedOp, videoRect)
                } else {
                    // Need a new frame
                    if (!pendingFetches.contains(op.id)) {
                        pendingFetches.add(op.id)
                        scope.launch(Dispatchers.Default) {
                            var bitmap: Bitmap? = null
                            if (isGif) {
                                val gif = gifCache[op.imageUri.toString()]
                                if (gif != null && gif.width > 0 && gif.height > 0) {
                                    try {
                                        val frame = gif.renderFrame(effectiveTimeMs.toInt(), reusableBitmaps["${op.id}_gif"])
                                        reusableBitmaps["${op.id}_gif"] = frame
                                        bitmap = frame
                                    } catch (e: Exception) { }
                                }
                            } else {
                                val retriever = retrieverCache[op.imageUri.toString()]
                                if (retriever != null) {
                                    try {
                                        // Use OPTION_CLOSEST instead of OPTION_CLOSEST_SYNC to get accurate frames rather than choppy I-frames
                                        bitmap = retriever.getFrameAtTime(effectiveTimeMs * 1000, android.media.MediaMetadataRetriever.OPTION_CLOSEST)
                                    } catch (e: Exception) { }
                                }
                            }
                            
                            if (bitmap != null && op.chromaKeyColor != null) {
                                bitmap = applyChromaKey(bitmap, op.chromaKeyColor!!, op.chromaKeySimilarity, cacheKey)
                            }
                            
                            withContext(Dispatchers.Main) {
                                if (bitmap != null) {
                                    lastFrameCache[cacheKey] = Pair(effectiveTimeMs, bitmap)
                                }
                                pendingFetches.remove(op.id)
                                invalidate() // redraw with new frame
                            }
                        }
                    }
                    // Draw the old frame while fetching the new one to prevent flickering
                    cached?.second?.let { drawBitmapOp(canvas, it, interpolatedOp, videoRect) }
                }
            } else {
                val staticCacheKey = "${op.imageUri}_${op.chromaKeyColor}_${op.chromaKeySimilarity}"
                val bitmap = bitmapCache[staticCacheKey]
                if (bitmap != null) {
                    drawBitmapOp(canvas, bitmap, interpolatedOp, videoRect)
                }
            }
        }
    }
    
    private fun computeDstRect(op: EditOperation.AddImageOverlay, videoRect: RectF): RectF {
        val imgW = op.relativeWidth * videoRect.width()
        val imgH = op.relativeHeight * videoRect.height()
        val centerX = videoRect.left + (op.relativeX * videoRect.width())
        val centerY = videoRect.top + (op.relativeY * videoRect.height())
        return RectF(centerX - imgW / 2f, centerY - imgH / 2f, centerX + imgW / 2f, centerY + imgH / 2f)
    }

    private fun drawBitmapOp(canvas: Canvas, bitmap: Bitmap, op: EditOperation.AddImageOverlay, videoRect: RectF) {
        val dstRect = computeDstRect(op, videoRect)
        val centerX = dstRect.centerX()
        val centerY = dstRect.centerY()

        val oldAlpha = paint.alpha
        paint.alpha = (op.opacity * 255).toInt().coerceIn(0, 255)
        canvas.save()
        canvas.rotate(op.rotationAngle, centerX, centerY)
        
        val relativeClipTimeMs = currentPositionMs - (op.startTimeMs ?: 0L)
        val maskConfig = op.maskConfig.evaluatedAt(relativeClipTimeMs)
        
        if (maskConfig.shape != EditOperation.MaskShape.NONE) {
            val path = android.graphics.Path()
            val cx = dstRect.left + dstRect.width() * maskConfig.relativeX
            val cy = dstRect.top + dstRect.height() * maskConfig.relativeY
            val mw = dstRect.width() * maskConfig.relativeWidth
            val mh = dstRect.height() * maskConfig.relativeHeight
            
            when (maskConfig.shape) {
                EditOperation.MaskShape.RECTANGLE -> path.addRect(cx - mw/2, cy - mh/2, cx + mw/2, cy + mh/2, android.graphics.Path.Direction.CW)
                EditOperation.MaskShape.ELLIPSE -> path.addOval(cx - mw/2, cy - mh/2, cx + mw/2, cy + mh/2, android.graphics.Path.Direction.CW)
                EditOperation.MaskShape.SPLIT -> path.addRect(dstRect.left - dstRect.width(), cy, dstRect.right + dstRect.width(), dstRect.bottom + dstRect.height(), android.graphics.Path.Direction.CW)
                EditOperation.MaskShape.SHUTTER -> path.addRect(dstRect.left - dstRect.width(), cy - mh/2, dstRect.right + dstRect.width(), cy + mh/2, android.graphics.Path.Direction.CW)
                EditOperation.MaskShape.HEART -> createHeartPath(path, cx, cy, mw, mh)
                EditOperation.MaskShape.STAR -> createStarPath(path, cx, cy, mw / 2f, mw / 4f)
                else -> {}
            }
            
            if (maskConfig.rotationAngle != 0f) {
                val matrix = android.graphics.Matrix()
                matrix.postRotate(maskConfig.rotationAngle, cx, cy)
                path.transform(matrix)
            }
            
            if (maskConfig.isInverted) {
                canvas.clipOutPath(path)
            } else {
                canvas.clipPath(path)
            }
        }
        
        if (op.isMirrored) {
            canvas.scale(-1f, 1f, centerX, centerY)
        }
        canvas.drawBitmap(bitmap, null, dstRect, paint)
        canvas.restore()
        paint.alpha = oldAlpha
    }

    private fun createHeartPath(path: android.graphics.Path, cx: Float, cy: Float, width: Float, height: Float) {
        path.reset()
        val topCurveHeight = height * 0.3f
        path.moveTo(cx, cy + height * 0.4f)
        path.cubicTo(
            cx - width * 0.5f, cy + height * 0.1f,
            cx - width * 0.5f, cy - topCurveHeight,
            cx, cy - topCurveHeight * 0.4f
        )
        path.cubicTo(
            cx + width * 0.5f, cy - topCurveHeight,
            cx + width * 0.5f, cy + height * 0.1f,
            cx, cy + height * 0.4f
        )
        path.close()
    }

    private fun createStarPath(path: android.graphics.Path, cx: Float, cy: Float, radiusOuter: Float, radiusInner: Float) {
        path.reset()
        val points = 5
        val angle = Math.PI / points
        for (i in 0 until 2 * points) {
            val r = if (i % 2 == 0) radiusOuter else radiusInner
            val currAngle = i * angle - Math.PI / 2
            val x = (cx + r * Math.cos(currAngle)).toFloat()
            val y = (cy + r * Math.sin(currAngle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }
}
