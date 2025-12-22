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
 * A custom view that renders summer effects with bouncing beach balls
 * and pulsing suns.
 */
class SummerView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

	private enum class BeachBallState {
		WAITING, BOUNCING, FADING, DONE
	}

	private data class BeachBall(
		var x: Float,
		var y: Float,
		val baseY: Float,
		val targetX: Float,
		val speed: Float,
		val size: Float,
		var state: BeachBallState,
		var alpha: Int,
		var waitTimer: Int,
		var bouncePhase: Float,
		val bounceSpeed: Float,
		val bounceAmplitude: Float,
		val fromLeft: Boolean,
		val emoji: String
	)

	private enum class SunState {
		WAITING, PULSING_IN, PULSING_OUT, FADING, DONE
	}

	private data class Sun(
		val x: Float,
		val y: Float,
		val size: Float,
		var state: SunState,
		var alpha: Int,
		var waitTimer: Int,
		var scale: Float,
		var pulseCount: Int
	)

	private enum class BeachUmbrellaState {
		WAITING, RISING, SETTLING, FADING, DONE
	}

	private data class BeachUmbrella(
		var x: Float,
		var y: Float,
		var velocity: Float,
		val size: Float,
		var state: BeachUmbrellaState,
		var alpha: Int,
		val groundY: Float,
		var waitTimer: Int = 0
	)

	private val beachBalls = mutableListOf<BeachBall>()
	private val suns = mutableListOf<Sun>()
	private val beachUmbrellas = mutableListOf<BeachUmbrella>()

	private val beachBallPaint = Paint().apply {
		isAntiAlias = true
		textAlign = Paint.Align.CENTER
	}

	private val sunPaint = Paint().apply {
		isAntiAlias = true
		textAlign = Paint.Align.CENTER
	}

	private val umbrellaPaint = Paint().apply {
		isAntiAlias = true
		textAlign = Paint.Align.CENTER
	}

	private var animator: ValueAnimator? = null
	private var isActive = false
	private var beachBallSpawnTimer = 0
	private var sunSpawnTimer = 0
	private var umbrellaSpawnTimer = 0
	private val beachBallSpawnInterval = 350  // ~17 seconds at 20fps
	private val sunSpawnInterval = 120  // ~6 seconds at 20fps
	private val umbrellaSpawnInterval = 450  // ~22 seconds at 20fps

	private val beachBallCount = 2
	private val beachBallSize = 45f
	private val beachBallSpeed = 2.5f
	private val beachBallEmojis = listOf("🏐")

	private val maxSuns = 2
	private val sunSize = 50f

	private val umbrellaCount = 3
	private val umbrellaSize = 50f
	private val umbrellaRiseSpeed = -4f

	init {
		isClickable = false
		isFocusable = false
		setLayerType(LAYER_TYPE_HARDWARE, null)
	}

	fun startEffect() {
		if (isActive) return
		isActive = true
		startAnimation()
	}

	fun stopEffect() {
		if (!isActive) return
		isActive = false

		animator?.cancel()
		animator = null
		beachBalls.clear()
		suns.clear()
		beachUmbrellas.clear()
		beachBallSpawnTimer = 0
		sunSpawnTimer = 0
		umbrellaSpawnTimer = 0
		invalidate()
	}

	private fun startAnimation() {
		animator?.cancel()

		animator = ValueAnimator.ofFloat(0f, 1f).apply {
			duration = 50L  // ~20fps
			repeatCount = ValueAnimator.INFINITE
			interpolator = LinearInterpolator()
			addUpdateListener {
				updateBeachBalls()
				updateSuns()
				updateBeachUmbrellas()
				invalidate()
			}
			start()
		}
	}

	private fun updateBeachBalls() {
		if (width <= 0 || height <= 0) return

		beachBallSpawnTimer++
		if (beachBallSpawnTimer >= beachBallSpawnInterval && beachBalls.none { it.state != BeachBallState.DONE }) {
			beachBallSpawnTimer = 0
			spawnBeachBalls()
		}

		val iterator = beachBalls.iterator()
		while (iterator.hasNext()) {
			val ball = iterator.next()

			when (ball.state) {
				BeachBallState.WAITING -> {
					ball.waitTimer--
					if (ball.waitTimer <= 0) {
						ball.state = BeachBallState.BOUNCING
					}
				}
				BeachBallState.BOUNCING -> {
					if (ball.fromLeft) {
						ball.x += ball.speed
					} else {
						ball.x -= ball.speed
					}

					ball.bouncePhase += ball.bounceSpeed
					ball.y = ball.baseY + kotlin.math.sin(ball.bouncePhase) * ball.bounceAmplitude * 0.3f

					val reachedEnd = if (ball.fromLeft) ball.x > width + ball.size else ball.x < -ball.size
					if (reachedEnd) {
						ball.state = BeachBallState.FADING
					}
				}
				BeachBallState.FADING -> {
					ball.alpha -= 12
					if (ball.alpha <= 0) {
						ball.state = BeachBallState.DONE
					}
				}
				BeachBallState.DONE -> {
					iterator.remove()
				}
			}
		}
	}

	private fun spawnBeachBalls() {
		if (width <= 0 || height <= 0) return

		val usableHeight = height * 0.5f
		val topMargin = height * 0.3f
		val zoneHeight = usableHeight / beachBallCount

		repeat(beachBallCount) { i ->
			val fromLeft = Random.nextBoolean()
			val startX = if (fromLeft) -beachBallSize else width + beachBallSize
			val baseY = topMargin + (zoneHeight * i) + (zoneHeight * 0.3f) + Random.nextFloat() * (zoneHeight * 0.4f)

			beachBalls.add(
				BeachBall(
					x = startX,
					y = baseY,
					baseY = baseY,
					targetX = if (fromLeft) width + beachBallSize else -beachBallSize,
					speed = beachBallSpeed + Random.nextFloat() * 1f,
					size = beachBallSize + Random.nextFloat() * 10f,
					state = BeachBallState.WAITING,
					alpha = 255,
					waitTimer = i * 50 + Random.nextInt(20, 70),
					bouncePhase = Random.nextFloat() * Math.PI.toFloat(),
					bounceSpeed = 0.08f + Random.nextFloat() * 0.04f,  // Slower than bee buzz
					bounceAmplitude = 40f + Random.nextFloat() * 20f,  // Bigger bounce
					fromLeft = fromLeft,
					emoji = beachBallEmojis[Random.nextInt(beachBallEmojis.size)]
				)
			)
		}
	}

	private fun updateSuns() {
		if (width <= 0 || height <= 0) return

		sunSpawnTimer++
		val activeSuns = suns.count { it.state != SunState.DONE }
		if (sunSpawnTimer >= sunSpawnInterval && activeSuns < maxSuns) {
			sunSpawnTimer = 0
			spawnSun()
		}

		val iterator = suns.iterator()
		while (iterator.hasNext()) {
			val sun = iterator.next()

			when (sun.state) {
				SunState.WAITING -> {
					sun.waitTimer--
					if (sun.waitTimer <= 0) {
						sun.state = SunState.PULSING_IN
					}
				}
				SunState.PULSING_IN -> {
					sun.scale += 0.02f
					sun.alpha = (sun.scale * 255).toInt().coerceIn(0, 255)
					if (sun.scale >= 1.2f) {
						sun.state = SunState.PULSING_OUT
					}
				}
				SunState.PULSING_OUT -> {
					sun.scale -= 0.015f
					if (sun.scale <= 0.8f) {
						sun.pulseCount++
						if (sun.pulseCount >= 2) {
							sun.state = SunState.FADING
						} else {
							sun.state = SunState.PULSING_IN
						}
					}
				}
				SunState.FADING -> {
					sun.alpha -= 5
					sun.scale -= 0.01f
					if (sun.alpha <= 0) {
						sun.state = SunState.DONE
					}
				}
				SunState.DONE -> {
					iterator.remove()
				}
			}
		}
	}

	private fun spawnSun() {
		if (width <= 0 || height <= 0) return

		val x = Random.nextFloat() * (width - sunSize * 2) + sunSize
		val y = Random.nextFloat() * (height * 0.4f) + sunSize

		suns.add(
			Sun(
				x = x,
				y = y,
				size = sunSize + Random.nextFloat() * 15f,
				state = SunState.WAITING,
				alpha = 0,
				waitTimer = Random.nextInt(10, 40),
				scale = 0.3f,
				pulseCount = 0
			)
		)
	}

	private fun updateBeachUmbrellas() {
		if (width <= 0 || height <= 0) return

		umbrellaSpawnTimer++
		if (umbrellaSpawnTimer >= umbrellaSpawnInterval && beachUmbrellas.none { it.state != BeachUmbrellaState.DONE }) {
			umbrellaSpawnTimer = 0
			spawnBeachUmbrellas()
		}

		val iterator = beachUmbrellas.iterator()
		while (iterator.hasNext()) {
			val umbrella = iterator.next()

			when (umbrella.state) {
				BeachUmbrellaState.WAITING -> {
					umbrella.waitTimer--
					if (umbrella.waitTimer <= 0) {
						umbrella.state = BeachUmbrellaState.RISING
						umbrella.velocity = umbrellaRiseSpeed
					}
				}
				BeachUmbrellaState.RISING -> {
					umbrella.velocity += 0.2f  // Slow deceleration
					umbrella.y += umbrella.velocity

					if (umbrella.velocity >= 0 && umbrella.y >= umbrella.groundY) {
						umbrella.y = umbrella.groundY
						umbrella.state = BeachUmbrellaState.SETTLING
					}
				}
				BeachUmbrellaState.SETTLING -> {
					umbrella.state = BeachUmbrellaState.FADING
				}
				BeachUmbrellaState.FADING -> {
					umbrella.alpha -= 3
					if (umbrella.alpha <= 0) {
						umbrella.state = BeachUmbrellaState.DONE
					}
				}
				BeachUmbrellaState.DONE -> {
					iterator.remove()
				}
			}
		}
	}

	private fun spawnBeachUmbrellas() {
		if (width <= 0 || height <= 0) return

		val groundY = height - umbrellaSize / 2 - 20f
		val spacing = width / (umbrellaCount + 1)

		repeat(umbrellaCount) { i ->
			val x = spacing * (i + 1) + Random.nextFloat() * 40 - 20
			beachUmbrellas.add(
				BeachUmbrella(
					x = x.toFloat(),
					y = height + umbrellaSize,
					velocity = 0f,
					size = umbrellaSize,
					state = BeachUmbrellaState.WAITING,
					alpha = 255,
					groundY = groundY,
					waitTimer = i * 25 + Random.nextInt(0, 30)
				)
			)
		}
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)

		if (!isActive) return

		suns.forEach { sun ->
			if (sun.state != SunState.DONE && sun.state != SunState.WAITING) {
				sunPaint.alpha = sun.alpha.coerceIn(0, 255)
				sunPaint.textSize = sun.size * sun.scale

				canvas.save()
				canvas.translate(sun.x, sun.y)
				canvas.drawText("☀️", 0f, 0f, sunPaint)
				canvas.restore()
			}
		}

		beachBalls.forEach { ball ->
			if (ball.state != BeachBallState.DONE && ball.state != BeachBallState.WAITING) {
				beachBallPaint.alpha = ball.alpha.coerceIn(0, 255)
				beachBallPaint.textSize = ball.size
				canvas.drawText(ball.emoji, ball.x, ball.y, beachBallPaint)
			}
		}

		beachUmbrellas.forEach { umbrella ->
			if (umbrella.state != BeachUmbrellaState.DONE && umbrella.state != BeachUmbrellaState.WAITING) {
				umbrellaPaint.alpha = umbrella.alpha.coerceIn(0, 255)
				umbrellaPaint.textSize = umbrella.size
				canvas.drawText("🏖️", umbrella.x, umbrella.y, umbrellaPaint)
			}
		}
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		stopEffect()
	}

	override fun onVisibilityChanged(changedView: View, visibility: Int) {
		super.onVisibilityChanged(changedView, visibility)

		if (visibility == VISIBLE && isActive) {
			if (animator?.isRunning != true) {
				startAnimation()
			}
		} else {
			animator?.cancel()
		}
	}
}
