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
 * A custom view that renders a snowfall effect overlay.
 * Snowflakes fall from the top of the screen with varying sizes, speeds, and horizontal drift.
 * Snowmen periodically drop, bounce, settle, and fade out at the bottom.
 * 
 * Inspired by Home Assistant's seasonal surprise feature.
 */
class SnowfallView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

	private data class Snowflake(
		var x: Float,
		var y: Float,
		val size: Float,
		val speed: Float,
		val drift: Float,
		val driftSpeed: Float,
		var driftPhase: Float,
		val alpha: Int
	)

	private enum class SnowmanState {
		WAITING, RISING, BOUNCING, SETTLING, FADING, DONE
	}

	private data class Snowman(
		var x: Float,
		var y: Float,
		var velocity: Float,
		val size: Float,
		var state: SnowmanState,
		var alpha: Int,
		var bounceCount: Int = 0,
		val groundY: Float,
		var waitTimer: Int = 0
	)

	private val snowflakes = mutableListOf<Snowflake>()
	private val snowmen = mutableListOf<Snowman>()
	
	private val paint = Paint().apply {
		isAntiAlias = false  // Disable for performance
		color = 0xFFFFFFFF.toInt()
		style = Paint.Style.FILL
	}
	
	private val snowmanPaint = Paint().apply {
		isAntiAlias = true
		textAlign = Paint.Align.CENTER
	}

	private var animator: ValueAnimator? = null
	private var isSnowing = false
	private var snowmanSpawnTimer = 0
	private val snowmanSpawnInterval = 300  // Spawn snowmen every ~10 seconds at 30fps

	private val maxSnowflakes = 25  // Reduced from 60
	private val minSize = 4f
	private val maxSize = 10f
	private val minSpeed = 1.5f
	private val maxSpeed = 3.5f
	private val minAlpha = 120
	private val maxAlpha = 200
	
	private val snowmanCount = 4
	private val snowmanSize = 60f
	private val gravity = 0.4f  // Reduced gravity for gentler motion
	private val bounceDamping = 0.25f  // Much less bouncy
	private val popUpVelocity = -8f  // Initial upward velocity when popping up

	init {
		isClickable = false
		isFocusable = false
		setLayerType(LAYER_TYPE_HARDWARE, null)  // Hardware acceleration
	}

	/**
	 * Start the snowfall animation.
	 */
	fun startSnowing() {
		if (isSnowing) return
		isSnowing = true
		
		initSnowflakes()
		startAnimation()
	}

	/**
	 * Stop the snowfall animation.
	 */
	fun stopSnowing() {
		if (!isSnowing) return
		isSnowing = false
		
		animator?.cancel()
		animator = null
		snowflakes.clear()
		snowmen.clear()
		snowmanSpawnTimer = 0
		invalidate()
	}

	private fun initSnowflakes() {
		snowflakes.clear()
		
		if (width <= 0 || height <= 0) {
			return
		}

		repeat(maxSnowflakes) {
			snowflakes.add(createSnowflake(randomY = true))
		}
	}

	private fun createSnowflake(randomY: Boolean = false): Snowflake {
		val size = Random.nextFloat() * (maxSize - minSize) + minSize
		return Snowflake(
			x = Random.nextFloat() * width,
			y = if (randomY) Random.nextFloat() * height else -size * 2,
			size = size,
			speed = Random.nextFloat() * (maxSpeed - minSpeed) + minSpeed,
			drift = Random.nextFloat() * 30f + 10f,  // Drift amplitude 10-40 pixels
			driftSpeed = Random.nextFloat() * 0.03f + 0.01f,
			driftPhase = Random.nextFloat() * Math.PI.toFloat() * 2,
			alpha = Random.nextInt(minAlpha, maxAlpha)
		)
	}

	private fun startAnimation() {
		animator?.cancel()
		
		animator = ValueAnimator.ofFloat(0f, 1f).apply {
			duration = 33L  // ~30fps for better performance
			repeatCount = ValueAnimator.INFINITE
			interpolator = LinearInterpolator()
			addUpdateListener {
				updateSnowflakes()
				updateSnowmen()
				invalidate()
			}
			start()
		}
	}

	private fun updateSnowflakes() {
		if (width <= 0 || height <= 0) return

		snowflakes.forEachIndexed { index, flake ->
			flake.y += flake.speed
			
			flake.driftPhase += flake.driftSpeed
			flake.x += kotlin.math.sin(flake.driftPhase) * flake.drift * 0.02f
			
			if (flake.y > height + flake.size) {
				val newFlake = createSnowflake(randomY = false)
				snowflakes[index] = newFlake
			}
			
			if (flake.x < -flake.size) {
				flake.x = width + flake.size
			} else if (flake.x > width + flake.size) {
				flake.x = -flake.size
			}
		}
	}

	private fun updateSnowmen() {
		if (width <= 0 || height <= 0) return

		snowmanSpawnTimer++
		if (snowmanSpawnTimer >= snowmanSpawnInterval && snowmen.none { it.state != SnowmanState.DONE }) {
			snowmanSpawnTimer = 0
			spawnSnowmen()
		}

		val iterator = snowmen.iterator()
		while (iterator.hasNext()) {
			val snowman = iterator.next()
			
			when (snowman.state) {
				SnowmanState.WAITING -> {
					snowman.waitTimer--
					if (snowman.waitTimer <= 0) {
						snowman.state = SnowmanState.RISING
						snowman.velocity = popUpVelocity
					}
				}
				SnowmanState.RISING -> {
					snowman.velocity += gravity
					snowman.y += snowman.velocity
					
					if (snowman.velocity >= 0 && snowman.y >= snowman.groundY) {
						snowman.y = snowman.groundY
						snowman.velocity = popUpVelocity * bounceDamping  // Small bounce
						snowman.state = SnowmanState.BOUNCING
					}
				}
				SnowmanState.BOUNCING -> {
					snowman.velocity += gravity
					snowman.y += snowman.velocity
					
					if (snowman.y >= snowman.groundY) {
						snowman.y = snowman.groundY
						snowman.bounceCount++
						
						if (snowman.bounceCount >= 1) {
							snowman.state = SnowmanState.SETTLING
							snowman.velocity = 0f
						} else {
							snowman.velocity = popUpVelocity * bounceDamping * 0.5f
						}
					}
				}
				SnowmanState.SETTLING -> {
					snowman.state = SnowmanState.FADING
				}
				SnowmanState.FADING -> {
					snowman.alpha -= 3  // Fade out gradually
					if (snowman.alpha <= 0) {
						snowman.state = SnowmanState.DONE
					}
				}
				SnowmanState.DONE -> {
					iterator.remove()
				}
			}
		}
	}

	private fun spawnSnowmen() {
		if (width <= 0 || height <= 0) return
		
		val groundY = height - snowmanSize / 2 - 20f  // Slightly above bottom
		val spacing = width / (snowmanCount + 1)
		
		repeat(snowmanCount) { i ->
			val x = spacing * (i + 1) + Random.nextFloat() * 40 - 20  // Add some randomness
			val staggerDelay = Random.nextInt(20, 80)  // Random delay 0.6-2.6 seconds at 30fps
			snowmen.add(
				Snowman(
					x = x.toFloat(),
					y = height + snowmanSize,  // Start below screen
					velocity = 0f,
					size = snowmanSize,
					state = SnowmanState.WAITING,
					alpha = 255,
					groundY = groundY,
					waitTimer = staggerDelay * i + Random.nextInt(0, 30)  // Staggered start times
				)
			)
		}
	}

	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
		super.onSizeChanged(w, h, oldw, oldh)
		
		if (isSnowing && snowflakes.isEmpty()) {
			initSnowflakes()
		}
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		
		if (!isSnowing) return

		snowflakes.forEach { flake ->
			paint.alpha = flake.alpha
			canvas.drawCircle(flake.x, flake.y, flake.size / 2, paint)
		}
		
		snowmen.forEach { snowman ->
			if (snowman.state != SnowmanState.DONE) {
				snowmanPaint.alpha = snowman.alpha
				snowmanPaint.textSize = snowman.size
				canvas.drawText("⛄", snowman.x, snowman.y, snowmanPaint)
			}
		}
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		stopSnowing()
	}

	override fun onVisibilityChanged(changedView: View, visibility: Int) {
		super.onVisibilityChanged(changedView, visibility)
		
		if (visibility == VISIBLE && isSnowing) {
			if (animator?.isRunning != true) {
				startAnimation()
			}
		} else {
			animator?.cancel()
		}
	}
}
