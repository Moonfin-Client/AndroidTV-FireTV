package org.jellyfin.androidtv.ui.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.random.Random

/**
 * A custom view that renders falling cherry blossom petals and flowers for spring.
 * Petals fall slower than snowflakes with gentle swaying motion.
 */
class PetalfallView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

	private data class Petal(
		var x: Float,
		var y: Float,
		val size: Float,
		val speed: Float,
		val drift: Float,
		val driftSpeed: Float,
		var driftPhase: Float,
		val rotation: Float,
		var currentRotation: Float,
		val rotationSpeed: Float,
		val alpha: Int,
		val emoji: String
	)

	private enum class BeeState {
		WAITING, FLYING, FADING, DONE
	}

	private data class Bee(
		var x: Float,
		var y: Float,
		val targetX: Float,
		val speed: Float,
		val size: Float,
		var state: BeeState,
		var alpha: Int,
		var waitTimer: Int,
		var buzzPhase: Float,
		val buzzSpeed: Float,
		val buzzAmplitude: Float,
		val fromLeft: Boolean
	)

	private val petals = mutableListOf<Petal>()
	private val bees = mutableListOf<Bee>()
	
	private val paint = Paint().apply {
		isAntiAlias = false  // Disable for performance
		textAlign = Paint.Align.CENTER
	}
	
	private val beePaint = Paint().apply {
		isAntiAlias = true
		textAlign = Paint.Align.CENTER
	}

	private var animator: ValueAnimator? = null
	private var isFalling = false
	private var beeSpawnTimer = 0
	private val beeSpawnInterval = 400  // Spawn bees every ~20 seconds at 20fps

	private val maxPetals = 20  // Reduced from 45
	private val minSize = 18f
	private val maxSize = 26f
	private val minSpeed = 0.8f
	private val maxSpeed = 1.8f
	private val minAlpha = 200
	private val maxAlpha = 255

	private val beeCount = 3
	private val beeSize = 35f
	private val beeSpeed = 3f

	private val springEmojis = listOf("🌸", "🌼", "🌸", "🌸", "🌼", "🌸")

	init {
		isClickable = false
		isFocusable = false
		setLayerType(LAYER_TYPE_HARDWARE, null)  // Hardware acceleration
	}

	/**
	 * Start the petal fall animation.
	 */
	fun startFalling() {
		if (isFalling) return
		isFalling = true
		
		initPetals()
		startAnimation()
	}

	/**
	 * Stop the petal fall animation.
	 */
	fun stopFalling() {
		if (!isFalling) return
		isFalling = false
		
		animator?.cancel()
		animator = null
		petals.clear()
		bees.clear()
		beeSpawnTimer = 0
		invalidate()
	}

	private fun initPetals() {
		petals.clear()
		
		if (width <= 0 || height <= 0) {
			return
		}

		repeat(maxPetals) {
			petals.add(createPetal(randomY = true))
		}
	}

	private fun createPetal(randomY: Boolean = false): Petal {
		val size = Random.nextFloat() * (maxSize - minSize) + minSize
		return Petal(
			x = Random.nextFloat() * width,
			y = if (randomY) Random.nextFloat() * height else -size * 2,
			size = size,
			speed = Random.nextFloat() * (maxSpeed - minSpeed) + minSpeed,
			drift = Random.nextFloat() * 50f + 20f,  // Wider drift for floating effect
			driftSpeed = Random.nextFloat() * 0.02f + 0.008f,  // Slower drift
			driftPhase = Random.nextFloat() * Math.PI.toFloat() * 2,
			rotation = Random.nextFloat() * 360f,
			currentRotation = Random.nextFloat() * 360f,
			rotationSpeed = Random.nextFloat() * 1.5f + 0.5f,  // Gentle rotation
			alpha = Random.nextInt(minAlpha, maxAlpha),
			emoji = springEmojis[Random.nextInt(springEmojis.size)]
		)
	}

	private fun startAnimation() {
		animator?.cancel()
		
		animator = ValueAnimator.ofFloat(0f, 1f).apply {
			duration = 50L  // ~20fps - slower is fine for gentle petals
			repeatCount = ValueAnimator.INFINITE
			interpolator = LinearInterpolator()
			addUpdateListener {
				updatePetals()
				updateBees()
				invalidate()
			}
			start()
		}
	}

	private fun updatePetals() {
		if (width <= 0 || height <= 0) return

		petals.forEachIndexed { index, petal ->
			petal.y += petal.speed
			
			petal.driftPhase += petal.driftSpeed
			petal.x += kotlin.math.sin(petal.driftPhase) * petal.drift * 0.015f
			
			petal.currentRotation += petal.rotationSpeed
			if (petal.currentRotation > 360f) petal.currentRotation -= 360f
			
			if (petal.y > height + petal.size) {
				val newPetal = createPetal(randomY = false)
				petals[index] = newPetal
			}
			
			if (petal.x < -petal.size) {
				petal.x = width + petal.size
			} else if (petal.x > width + petal.size) {
				petal.x = -petal.size
			}
		}
	}

	private fun updateBees() {
		if (width <= 0 || height <= 0) return

		beeSpawnTimer++
		if (beeSpawnTimer >= beeSpawnInterval && bees.none { it.state != BeeState.DONE }) {
			beeSpawnTimer = 0
			spawnBees()
		}

		val iterator = bees.iterator()
		while (iterator.hasNext()) {
			val bee = iterator.next()
			
			when (bee.state) {
				BeeState.WAITING -> {
					bee.waitTimer--
					if (bee.waitTimer <= 0) {
						bee.state = BeeState.FLYING
					}
				}
				BeeState.FLYING -> {
					if (bee.fromLeft) {
						bee.x += bee.speed
					} else {
						bee.x -= bee.speed
					}
					
					bee.buzzPhase += bee.buzzSpeed
					bee.y += kotlin.math.sin(bee.buzzPhase) * bee.buzzAmplitude
					
					val reachedEnd = if (bee.fromLeft) bee.x > width + bee.size else bee.x < -bee.size
					if (reachedEnd) {
						bee.state = BeeState.FADING
					}
				}
				BeeState.FADING -> {
					bee.alpha -= 15  // Quick fade
					if (bee.alpha <= 0) {
						bee.state = BeeState.DONE
					}
				}
				BeeState.DONE -> {
					iterator.remove()
				}
			}
		}
	}

	private fun spawnBees() {
		if (width <= 0 || height <= 0) return
		
		val usableHeight = height * 0.6f  // Use middle 60% of screen
		val topMargin = height * 0.2f
		val zoneHeight = usableHeight / beeCount
		
		repeat(beeCount) { i ->
			val fromLeft = Random.nextBoolean()
			val startX = if (fromLeft) -beeSize else width + beeSize
			val baseY = topMargin + (zoneHeight * i) + (zoneHeight * 0.2f)
			val y = baseY + Random.nextFloat() * (zoneHeight * 0.6f)
			
			bees.add(
				Bee(
					x = startX,
					y = y,
					targetX = if (fromLeft) width + beeSize else -beeSize,
					speed = beeSpeed + Random.nextFloat() * 1.5f,
					size = beeSize,
					state = BeeState.WAITING,
					alpha = 255,
					waitTimer = i * 40 + Random.nextInt(20, 60),  // Staggered: 0-3s, 2-5s, 4-7s
					buzzPhase = Random.nextFloat() * Math.PI.toFloat() * 2,
					buzzSpeed = 0.5f + Random.nextFloat() * 0.3f,  // Fast vibration
					buzzAmplitude = 1.5f + Random.nextFloat() * 1f,  // Small vertical buzz
					fromLeft = fromLeft
				)
			)
		}
	}

	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
		super.onSizeChanged(w, h, oldw, oldh)
		
		if (isFalling && petals.isEmpty()) {
			initPetals()
		}
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		
		if (!isFalling) return

		petals.forEach { petal ->
			paint.alpha = petal.alpha
			paint.textSize = petal.size
			
			canvas.save()
			canvas.translate(petal.x, petal.y)
			canvas.rotate(petal.currentRotation)
			canvas.drawText(petal.emoji, 0f, 0f, paint)
			canvas.restore()
		}

		bees.forEach { bee ->
			if (bee.state != BeeState.DONE && bee.state != BeeState.WAITING) {
				beePaint.alpha = bee.alpha
				beePaint.textSize = bee.size
				
				canvas.save()
				canvas.translate(bee.x, bee.y)
				if (bee.fromLeft) {
					canvas.scale(-1f, 1f)
				}
				canvas.drawText("🐝", 0f, 0f, beePaint)
				canvas.restore()
			}
		}
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		stopFalling()
	}

	override fun onVisibilityChanged(changedView: View, visibility: Int) {
		super.onVisibilityChanged(changedView, visibility)
		
		if (visibility == VISIBLE && isFalling) {
			if (animator?.isRunning != true) {
				startAnimation()
			}
		} else {
			animator?.cancel()
		}
	}
}
