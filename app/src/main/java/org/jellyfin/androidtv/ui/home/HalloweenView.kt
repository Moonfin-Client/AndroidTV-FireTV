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
 * A custom view that renders Halloween effects with ghosts, pumpkins,
 * spiders, and raining candy.
 */
class HalloweenView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

	private enum class GhostState {
		WAITING, FLOATING, FADING, DONE
	}

	private data class Ghost(
		var x: Float,
		var y: Float,
		val baseY: Float,
		val speed: Float,
		val size: Float,
		var state: GhostState,
		var alpha: Int,
		var waitTimer: Int,
		var floatPhase: Float,
		val floatSpeed: Float,
		val floatAmplitude: Float,
		val fromLeft: Boolean
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

	private enum class SpiderState {
		WAITING, APPEARING, VISIBLE, DISAPPEARING, DONE
	}

	private data class Spider(
		val x: Float,
		val y: Float,
		val size: Float,
		var state: SpiderState,
		var alpha: Int,
		var waitTimer: Int,
		var visibleTimer: Int
	)

	private data class Candy(
		var x: Float,
		var y: Float,
		val size: Float,
		val speed: Float,
		val drift: Float,
		var driftPhase: Float,
		val alpha: Int,
		val emoji: String
	)

	private val ghosts = mutableListOf<Ghost>()
	private val pumpkins = mutableListOf<Pumpkin>()
	private val spiders = mutableListOf<Spider>()
	private val candies = mutableListOf<Candy>()

	private val ghostPaint = Paint().apply {
		isAntiAlias = true
		textAlign = Paint.Align.CENTER
	}

	private val pumpkinPaint = Paint().apply {
		isAntiAlias = true
		textAlign = Paint.Align.CENTER
	}

	private val spiderPaint = Paint().apply {
		isAntiAlias = true
		textAlign = Paint.Align.CENTER
	}

	private val candyPaint = Paint().apply {
		isAntiAlias = false
		textAlign = Paint.Align.CENTER
	}

	private var animator: ValueAnimator? = null
	private var isActive = false
	private var ghostSpawnTimer = 0
	private var pumpkinSpawnTimer = 0
	private var spiderSpawnTimer = 0
	private val ghostSpawnInterval = 400  // ~20 seconds at 20fps
	private val pumpkinSpawnInterval = 500  // ~25 seconds at 20fps
	private val spiderSpawnInterval = 150  // ~7.5 seconds at 20fps

	private val ghostCount = 3
	private val ghostSize = 50f

	private val pumpkinCount = 3
	private val pumpkinSize = 50f
	private val gravity = 0.4f
	private val bounceDamping = 0.25f
	private val popUpVelocity = -8f

	private val maxSpiders = 2
	private val spiderSize = 40f

	private val maxCandies = 12
	private val candyEmojis = listOf("🍬", "🍭", "🍫", "🎃")

	init {
		isClickable = false
		isFocusable = false
		setLayerType(LAYER_TYPE_HARDWARE, null)
	}

	fun startEffect() {
		if (isActive) return
		isActive = true
		initCandies()
		startAnimation()
	}

	fun stopEffect() {
		if (!isActive) return
		isActive = false

		animator?.cancel()
		animator = null
		ghosts.clear()
		pumpkins.clear()
		spiders.clear()
		candies.clear()
		ghostSpawnTimer = 0
		pumpkinSpawnTimer = 0
		spiderSpawnTimer = 0
		invalidate()
	}

	private fun initCandies() {
		candies.clear()
		if (width <= 0 || height <= 0) return

		repeat(maxCandies) {
			candies.add(createCandy(randomY = true))
		}
	}

	private fun createCandy(randomY: Boolean = false): Candy {
		val size = Random.nextFloat() * 8f + 16f
		return Candy(
			x = Random.nextFloat() * width,
			y = if (randomY) Random.nextFloat() * height else -size * 2,
			size = size,
			speed = Random.nextFloat() * 1.2f + 0.8f,
			drift = Random.nextFloat() * 20f + 10f,
			driftPhase = Random.nextFloat() * Math.PI.toFloat() * 2,
			alpha = Random.nextInt(180, 255),
			emoji = candyEmojis[Random.nextInt(candyEmojis.size)]
		)
	}

	private fun startAnimation() {
		animator?.cancel()

		animator = ValueAnimator.ofFloat(0f, 1f).apply {
			duration = 50L  // ~20fps
			repeatCount = ValueAnimator.INFINITE
			interpolator = LinearInterpolator()
			addUpdateListener {
				updateGhosts()
				updatePumpkins()
				updateSpiders()
				updateCandies()
				invalidate()
			}
			start()
		}
	}

	private fun updateGhosts() {
		if (width <= 0 || height <= 0) return

		ghostSpawnTimer++
		if (ghostSpawnTimer >= ghostSpawnInterval && ghosts.none { it.state != GhostState.DONE }) {
			ghostSpawnTimer = 0
			spawnGhosts()
		}

		val iterator = ghosts.iterator()
		while (iterator.hasNext()) {
			val ghost = iterator.next()

			when (ghost.state) {
				GhostState.WAITING -> {
					ghost.waitTimer--
					if (ghost.waitTimer <= 0) {
						ghost.state = GhostState.FLOATING
					}
				}
				GhostState.FLOATING -> {
					if (ghost.fromLeft) {
						ghost.x += ghost.speed
					} else {
						ghost.x -= ghost.speed
					}

					ghost.floatPhase += ghost.floatSpeed
					ghost.y = ghost.baseY + kotlin.math.sin(ghost.floatPhase) * ghost.floatAmplitude

					val reachedEnd = if (ghost.fromLeft) ghost.x > width + ghost.size else ghost.x < -ghost.size
					if (reachedEnd) {
						ghost.state = GhostState.FADING
					}
				}
				GhostState.FADING -> {
					ghost.alpha -= 10
					if (ghost.alpha <= 0) {
						ghost.state = GhostState.DONE
					}
				}
				GhostState.DONE -> {
					iterator.remove()
				}
			}
		}
	}

	private fun spawnGhosts() {
		if (width <= 0 || height <= 0) return

		val usableHeight = height * 0.5f
		val topMargin = height * 0.15f
		val zoneHeight = usableHeight / ghostCount

		repeat(ghostCount) { i ->
			val fromLeft = Random.nextBoolean()
			val startX = if (fromLeft) -ghostSize else width + ghostSize
			val baseY = topMargin + (zoneHeight * i) + Random.nextFloat() * (zoneHeight * 0.6f)

			ghosts.add(
				Ghost(
					x = startX,
					y = baseY,
					baseY = baseY,
					speed = 2f + Random.nextFloat() * 1f,
					size = ghostSize + Random.nextFloat() * 10f,
					state = GhostState.WAITING,
					alpha = 200,
					waitTimer = i * 60 + Random.nextInt(20, 80),
					floatPhase = Random.nextFloat() * Math.PI.toFloat() * 2,
					floatSpeed = 0.06f + Random.nextFloat() * 0.03f,
					floatAmplitude = 15f + Random.nextFloat() * 10f,
					fromLeft = fromLeft
				)
			)
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
					waitTimer = i * 30 + Random.nextInt(0, 40)
				)
			)
		}
	}

	private fun updateSpiders() {
		if (width <= 0 || height <= 0) return

		spiderSpawnTimer++
		val activeSpiders = spiders.count { it.state != SpiderState.DONE }
		if (spiderSpawnTimer >= spiderSpawnInterval && activeSpiders < maxSpiders) {
			spiderSpawnTimer = 0
			spawnSpider()
		}

		val iterator = spiders.iterator()
		while (iterator.hasNext()) {
			val spider = iterator.next()

			when (spider.state) {
				SpiderState.WAITING -> {
					spider.waitTimer--
					if (spider.waitTimer <= 0) {
						spider.state = SpiderState.APPEARING
					}
				}
				SpiderState.APPEARING -> {
					spider.alpha += 8
					if (spider.alpha >= 255) {
						spider.alpha = 255
						spider.state = SpiderState.VISIBLE
					}
				}
				SpiderState.VISIBLE -> {
					spider.visibleTimer--
					if (spider.visibleTimer <= 0) {
						spider.state = SpiderState.DISAPPEARING
					}
				}
				SpiderState.DISAPPEARING -> {
					spider.alpha -= 5
					if (spider.alpha <= 0) {
						spider.state = SpiderState.DONE
					}
				}
				SpiderState.DONE -> {
					iterator.remove()
				}
			}
		}
	}

	private fun spawnSpider() {
		if (width <= 0 || height <= 0) return

		val x = Random.nextFloat() * (width - spiderSize * 2) + spiderSize
		val y = Random.nextFloat() * (height * 0.25f) + spiderSize

		spiders.add(
			Spider(
				x = x,
				y = y,
				size = spiderSize + Random.nextFloat() * 15f,
				state = SpiderState.WAITING,
				alpha = 0,
				waitTimer = Random.nextInt(10, 30),
				visibleTimer = Random.nextInt(60, 120)  // 3-6 seconds visible
			)
		)
	}

	private fun updateCandies() {
		if (width <= 0 || height <= 0) return

		candies.forEachIndexed { index, candy ->
			candy.y += candy.speed
			candy.driftPhase += 0.02f
			candy.x += kotlin.math.sin(candy.driftPhase) * candy.drift * 0.015f

			if (candy.y > height + candy.size) {
				val newCandy = createCandy(randomY = false)
				candies[index] = newCandy
			}

			if (candy.x < -candy.size) {
				candy.x = width + candy.size
			} else if (candy.x > width + candy.size) {
				candy.x = -candy.size
			}
		}
	}

	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
		super.onSizeChanged(w, h, oldw, oldh)

		if (isActive && candies.isEmpty()) {
			initCandies()
		}
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)

		if (!isActive) return

		candies.forEach { candy ->
			candyPaint.alpha = candy.alpha
			candyPaint.textSize = candy.size
			canvas.drawText(candy.emoji, candy.x, candy.y, candyPaint)
		}

		spiders.forEach { spider ->
			if (spider.state != SpiderState.DONE && spider.state != SpiderState.WAITING) {
				spiderPaint.alpha = spider.alpha.coerceIn(0, 255)
				spiderPaint.textSize = spider.size
				canvas.drawText("🕷️", spider.x, spider.y, spiderPaint)
			}
		}

		ghosts.forEach { ghost ->
			if (ghost.state != GhostState.DONE && ghost.state != GhostState.WAITING) {
				ghostPaint.alpha = ghost.alpha.coerceIn(0, 255)
				ghostPaint.textSize = ghost.size
				canvas.drawText("👻", ghost.x, ghost.y, ghostPaint)
			}
		}

		pumpkins.forEach { pumpkin ->
			if (pumpkin.state != PumpkinState.DONE && pumpkin.state != PumpkinState.WAITING) {
				pumpkinPaint.alpha = pumpkin.alpha.coerceIn(0, 255)
				pumpkinPaint.textSize = pumpkin.size
				canvas.drawText("🎃", pumpkin.x, pumpkin.y, pumpkinPaint)
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
