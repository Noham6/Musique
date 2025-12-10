package com.example.musique

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.audiofx.Visualizer
import android.util.AttributeSet
import android.view.View
import kotlin.math.log10
import kotlin.math.sqrt

class AudioVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barCount = 110
    private val barHeights = FloatArray(barCount) { 0f }
    private val targetHeights = FloatArray(barCount) { 0f }

    private var visualizer: Visualizer? = null
    private var isAnimating = false
    private var isRandomMode = false

    init {
        paint.style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val barWidth = width.toFloat() / barCount
        val maxHeight = height * 0.7f
        val spacing = barWidth * 0.2f

        for (i in 0 until barCount) {
            // Interpolation pour une animation fluide
            barHeights[i] += (targetHeights[i] - barHeights[i]) * 0.3f

            val left = i * barWidth + spacing
            val right = (i + 1) * barWidth - spacing
            val top = height - barHeights[i]
            val bottom = height.toFloat()

            // Gradient principal
            val gradient = LinearGradient(
                0f, top, 0f, bottom,
                intArrayOf(
                    Color.parseColor("#000000"),
                    Color.parseColor("#7f00ff"),
                    Color.parseColor("#9400D3")
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            paint.shader = gradient
            canvas.drawRect(left, top, right, bottom, paint)

            // Effet miroir
            val mirrorGradient = LinearGradient(
                0f, bottom, 0f, bottom + barHeights[i] * 0.3f,
                intArrayOf(
                    Color.argb(100, 0, 255, 0),
                    Color.TRANSPARENT
                ),
                null,
                Shader.TileMode.CLAMP
            )
            paint.shader = mirrorGradient
            canvas.drawRect(left, bottom, right, bottom + barHeights[i] * 0.3f, paint)
        }

        if (isAnimating) {
            invalidate()
        }
    }

    /**
     * Démarre l'animation simple (mode aléatoire - pour compatibilité)
     */
    fun startAnimation() {
        stopVisualization() // Arrêter toute visualisation en cours
        isRandomMode = true
        isAnimating = true
        updateBarsRandom()
        invalidate()
    }

    /**
     * Arrête l'animation
     */
    fun stopAnimation() {
        stopVisualization()
    }

    /**
     * Met à jour les barres de manière aléatoire
     */
    private fun updateBarsRandom() {
        if (!isAnimating || !isRandomMode) return

        val maxHeight = height * 0.7f
        for (i in targetHeights.indices) {
            targetHeights[i] = (Math.random() * maxHeight).toFloat()
        }

        postDelayed({ updateBarsRandom() }, 100)
    }

    /**
     * Démarre la visualisation audio avec analyse réelle
     * @param audioSessionId L'ID de session audio du MediaPlayer
     */
    fun startVisualization(audioSessionId: Int) {
        stopVisualization()
        isRandomMode = false

        try {
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]

                // Listener pour les données FFT (Fast Fourier Transform)
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        // Non utilisé dans cette implémentation
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        fft?.let { updateVisualizerFFT(it) }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)

                enabled = true
            }

            isAnimating = true
            invalidate()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Arrête la visualisation audio
     */
    fun stopVisualization() {
        isAnimating = false
        isRandomMode = false

        visualizer?.apply {
            enabled = false
            release()
        }
        visualizer = null

        // Réinitialise les barres
        for (i in targetHeights.indices) {
            targetHeights[i] = 0f
        }
        invalidate()
    }

    /**
     * Met à jour les hauteurs des barres en fonction des données FFT
     */
    private fun updateVisualizerFFT(fft: ByteArray) {
        val maxHeight = height * 0.7f

        // Calcule le nombre d'échantillons par barre
        val samplesPerBar = (fft.size / 2) / barCount

        for (i in 0 until barCount) {
            var sum = 0f
            val startIndex = i * samplesPerBar
            val endIndex = minOf(startIndex + samplesPerBar, fft.size / 2)

            // Calcule la magnitude moyenne pour cette barre
            for (j in startIndex until endIndex) {
                val real = fft[j * 2].toInt()
                val imaginary = fft[j * 2 + 1].toInt()
                val magnitude = sqrt((real * real + imaginary * imaginary).toFloat())
                sum += magnitude
            }

            val average = sum / samplesPerBar


            val db = if (average > 0) 20 * log10(average / 128f) else -100f
            val normalizedDb = ((db + 60) / 60).coerceIn(0f, 1f)

            targetHeights[i] = normalizedDb * normalizedDb * maxHeight
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopVisualization()
    }
}