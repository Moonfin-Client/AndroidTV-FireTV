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
 * A custom view that renders falling autumn leaves for fall season.
 * Leaves fall slowly with gentle swaying and rotation.
 */
class LeaffallView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

	private data class Leaf(
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

	private enum class PumpkinState {
		WAITING, RISING, BOUNCING, SETTLING, FADING, DONE
	}

	private data class Pumpkin(
		var x: Float,
		var y: Float,
		var velocity: Float,
		val size: Float,
		var state: PumpkinState,
		var alpha: Int,
		var bounceCount: Int = 0,
		val groundY: Float,
		var waitTimer: Int = 0
	)

	private val leaves = mutableListOf<Leaf>()
	private val pumpkins = mutableListOf<Pumpkin>()
	
	private val paint = Paint().apply {
		isAntiAlias = false  // Disable for performance
		textAlign = Paint.Align.CENTER
	}
	
	private val pumpkinPaint = Paint().apply {
		isAntiAlias = true
		textAlign = Paint.Align.CENTER
	}

	private var animator: ValueAnimator? = null
	private var isFalling = false
	private var pumpkinSpawnTimer = 0
	private val pumpkinSpawnInterval = 400  // Spawn pumpkins every ~20 seconds at 20fps

	private val maxLeaves = 18  // Reduced from 40
	private val minSize = 22f
	private val maxSize = 30f
	private val minSpeed = 0.6f
	private val maxSpeed = 1.4f
	private val minAlpha = 220
	private val maxAlpha = 255

	private val pumpkinCount = 4
	private val pumpkinSize = 55f
	private val gravity = 0.4f
	private val bounceDamping = 0.25f
	private val popUpVelocity = -8f

	private val fallEmojis = listOf("🍁", "🍂", "🍁", "🍂", "🍁")

	init {
		isClickable = false
		isFocusable = false
		setLayerType(LAYER_TYPE_HARDWARE, null)  // Hardware acceleration
	}

	/**
	 * Start the leaf fall animation.
	 */
	fun startFalling() {
		if (isFalling) return
		isFalling = true
		
		initLeaves()
		startAnimation()
	}

	/**
	 * Stop the leaf fall animation.
	 */
	fun stopFalling() {
		if (!isFalling) return
		isFalling = false
		
		animator?.cancel()
		animator = null
		leaves.clear()
		pumpkins.clear()
		pumpkinSpawnTimer = 0
		invalidate()
	}

	private fun initLeaves() {
		leaves.clear()
		
		if (width <= 0 || height <= 0) {
			return
		}

		repeat(maxLeaves) {
			leaves.add(createLeaf(randomY = true))
		}
	}

	private fun createLeaf(randomY: Boolean = false): Leaf {
		val size = Random.nextFloat() * (maxSize - minSize) + minSize
		return Leaf(
			x = Random.nextFloat() * width,
			y = if (randomY) Random.nextFloat() * height else -size * 2,
			size = size,
			speed = Random.nextFloat() * (maxSpeed - minSpeed) + minSpeed,
			drift = Random.nextFloat() * 60f + 30f,  // Wide drift for floating leaf effect
			driftSpeed = Random.nextFloat() * 0.015f + 0.005f,  // Very slow drift
			driftPhase = Random.nextFloat() * Math.PI.toFloat() * 2,
			rotation = Random.nextFloat() * 360f,
			currentRotation = Random.nextFloat() * 360f,
			rotationSpeed = Random.nextFloat() * 2f + 0.3f,  // Gentle tumbling
			alpha = Random.nextInt(minAlpha, maxAlpha),
			emoji = fallEmojis[Random.nextInt(fallEmojis.size)]
		)
	}

	private fun startAnimation() {
		animator?.cancel()
		
		animator = ValueAnimator.ofFloat(0f, 1f).apply {
			duration = 50L  // ~20fps - slower is fine for gentle leaves
			repeatCount = ValueAnimator.INFINITE
			interpolator = LinearInterpolator()
			addUpdateListener {
				updateLeaves()
				updatePumpkins()
				invalidate()
			}
			start()
		}
	}

	private fun updateLeaves() {
		if (width <= 0 || height <= 0) return

		leaves.forEachIndexed { index, leaf ->
			leaf.y += leaf.speed
			
			leaf.driftPhase += leaf.driftSpeed
			leaf.x += kotlin.math.sin(leaf.driftPhase) * leaf.drift * 0.012f
			
			leaf.currentRotation += leaf.rotationSpeed
			if (leaf.currentRotation > 360f) leaf.currentRotation -= 360f
			
			if (leaf.y > height + leaf.size) {
				val newLeaf = createLeaf(randomY = false)
				leaves[index] = newLeaf
			}
			
			if (leaf.x < -leaf.size) {
				leaf.x = width + leaf.size
			} else if (leaf.x > width + leaf.size) {
				leaf.x = -leaf.size
			}
		}
	}

	private fun updatePumpkins() {
		if (width <= 0 || height <= 0) return

		pumpkinSpawnTimer++
		if (pumpkinSpawnTimer >= pumpkinSpawnInterval && pumpkins.none { it.state != PumpkinState.DONE }) {
			pumpkinSpawnTimer = 0
			spawnPumpkins()
		}

		val iterator = pumpkins.iterator()
		while (iterator.hasNext()) {
			val pumpkin = iterator.next()
			
			when (pumpkin.state) {
				PumpkinState.WAITING -> {
					pumpkin.waitTimer--
					if (pumpkin.waitTimer <= 0) {
						pumpkin.state = PumpkinState.RISING
						pumpkin.velocity = popUpVelocity
					}
				}
				PumpkinState.RISING -> {
					pumpkin.velocity += gravity
					pumpkin.y += pumpkin.velocity
					
					if (pumpkin.velocity >= 0 && pumpkin.y >= pumpkin.groundY) {
						pumpkin.y = pumpkin.groundY
						pumpkin.velocity = popUpVelocity * bounceDamping
						pumpkin.state = PumpkinState.BOUNCING
					}
				}
				PumpkinState.BOUNCING -> {
					pumpkin.velocity += gravity
					pumpkin.y += pumpkin.velocity
					
					if (pumpkin.y >= pumpkin.groundY) {
						pumpkin.y = pumpkin.groundY
						pumpkin.bounceCount++
						
						if (pumpkin.bounceCount >= 1) {
							pumpkin.state = PumpkinState.SETTLING
							pumpkin.velocity = 0f
						} else {
							pumpkin.velocity = popUpVelocity * bounceDamping * 0.5f
						}
					}
				}
				PumpkinState.SETTLING -> {
					pumpkin.state = PumpkinState.FADING
				}
				PumpkinState.FADING -> {
					pumpkin.alpha -= 3
					if (pumpkin.alpha <= 0) {
						pumpkin.state = PumpkinState.DONE
					}
				}
				PumpkinState.DONE -> {
					iterator.remove()
				}
			}
		}
	}

	private fun spawnPumpkins() {
		if (width <= 0 || height <= 0) return
		
		val groundY = height - pumpkinSize / 2 - 20f
		val spacing = width / (pumpkinCount + 1)
		
		repeat(pumpkinCount) { i ->
			val x = spacing * (i + 1) + Random.nextFloat() * 40 - 20
			pumpkins.add(
				Pumpkin(
					x = x.toFloat(),
					y = height + pumpkinSize,
					velocity = 0f,
					size = pumpkinSize,
					state = PumpkinState.WAITING,
					alpha = 255,
					groundY = groundY,
					waitTimer = i * 30 + Random.nextInt(0, 40)  // Staggered start times
				)
			)
		}
	}

	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
		super.onSizeChanged(w, h, oldw, oldh)
		
		if (isFalling && leaves.isEmpty()) {
			initLeaves()
		}
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		
		if (!isFalling) return

		leaves.forEach { leaf ->
			paint.alpha = leaf.alpha
			paint.textSize = leaf.size
			
			canvas.save()
			canvas.translate(leaf.x, leaf.y)
			canvas.rotate(leaf.currentRotation)
			canvas.drawText(leaf.emoji, 0f, 0f, paint)
			canvas.restore()
		}
		
		pumpkins.forEach { pumpkin ->
			if (pumpkin.state != PumpkinState.DONE && pumpkin.state != PumpkinState.WAITING) {
				pumpkinPaint.alpha = pumpkin.alpha
				pumpkinPaint.textSize = pumpkin.size
				canvas.drawText("🎃", pumpkin.x, pumpkin.y, pumpkinPaint)
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
