import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Texture.TextureFilter
import com.badlogic.gdx.graphics.Texture.TextureWrap
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.viewport.FitViewport
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class EnemyState {
    STALKING, // Идет на игрока
    FROZEN,   // Замер на 0.5 сек от яркого света
    FLEEING   // Убегает обратно в темноту
}

data class DarkEnemy2D(
    var x: Float,
    var y: Float,
    val spawnX: Float,
    val spawnY: Float,
    var state: EnemyState = EnemyState.STALKING,
    var freezeTimer: Float = 0f,
    val speed: Float = (Math.random().toFloat() * 40f + 110f),
    val fleeSpeed: Float = (Math.random().toFloat() * 80f + 260f) // Убегает намного быстрее!
)

class SecretMiniGame {
    private lateinit var worldCamera: OrthographicCamera
    private var lightFbo: FrameBuffer? = null

    private val enemies = mutableListOf<DarkEnemy2D>()
    private var enemySpawnTimer = 0f

    var playerX = 0f
    var playerY = 0f
    private var cameraX = 0f
    private var cameraY = 0f
    private val playerSpeed = 230f
    private var aimAngleDeg = 0f

    private val bodyDist = 34f
    private val tipDist = 48f
    private val proximityDist = 140f

    var gameTimer = 0f
    val gameDuration = 60f

    private var glitchIntensity = 0f

    var jumpscareTimer = 0f
    val jumpscareDuration = 1.8f

    private var screamSound: Sound? = null
    private val faceTextures = mutableListOf<Texture>()
    private var grassTexture: Texture? = null
    private var flashlightTexture: Texture? = null

    fun init(boxModel: Model? = null) {
        enemies.clear()
        playerX = 0f
        playerY = 0f
        cameraX = 0f
        cameraY = 0f
        gameTimer = 0f
        jumpscareTimer = 0f
        enemySpawnTimer = 0f
        glitchIntensity = 0f

        worldCamera = OrthographicCamera(800f, 600f)

        if (lightFbo == null) {
            lightFbo = FrameBuffer(Pixmap.Format.RGBA8888, 800, 600, false)
        }

        if (faceTextures.isEmpty()) {
            loadFaceTextures()
        }

        if (grassTexture == null) {
            loadGrassTexture()
        }

        if (flashlightTexture == null) {
            loadFlashlightTexture()
        }

        if (screamSound == null) {
            screamSound = createScreamSound()
        }
    }

    private fun loadFlashlightTexture() {
        val file = sequenceOf(
            Gdx.files.local("flashlight.png"),
            Gdx.files.internal("flashlight.png"),
            Gdx.files.internal("src/main/resources/flashlight.png")
        ).firstOrNull { it.exists() }

        if (file != null) {
            try {
                flashlightTexture = Texture(file).apply {
                    setFilter(TextureFilter.Nearest, TextureFilter.Nearest)
                }
            } catch (e: Exception) {
                flashlightTexture = createFallbackFlashlightTexture()
            }
        } else {
            flashlightTexture = createFallbackFlashlightTexture()
        }
    }

    private fun createFallbackFlashlightTexture(): Texture {
        val pixmap = Pixmap(16, 36, Pixmap.Format.RGBA8888)
        pixmap.setColor(Color(0.25f, 0.25f, 0.30f, 1f))
        pixmap.fillRectangle(4, 10, 8, 26)
        pixmap.setColor(Color(1f, 0.95f, 0.6f, 1f))
        pixmap.fillRectangle(2, 0, 12, 10)
        val tex = Texture(pixmap)
        pixmap.dispose()
        return tex
    }

    private fun loadGrassTexture() {
        val file = sequenceOf(
            Gdx.files.local("grass.png"),
            Gdx.files.internal("grass.png"),
            Gdx.files.internal("src/main/resources/grass.png")
        ).firstOrNull { it.exists() }

        if (file != null) {
            try {
                grassTexture = Texture(file).apply {
                    setFilter(TextureFilter.Linear, TextureFilter.Linear)
                    setWrap(TextureWrap.Repeat, TextureWrap.Repeat)
                }
            } catch (e: Exception) {
                grassTexture = createFallbackGrassTexture()
            }
        } else {
            grassTexture = createFallbackGrassTexture()
        }
    }

    private fun createFallbackGrassTexture(): Texture {
        val pixmap = Pixmap(128, 128, Pixmap.Format.RGBA8888)
        pixmap.setColor(Color(0.18f, 0.35f, 0.15f, 1f))
        pixmap.fill()
        pixmap.setColor(Color(0.22f, 0.42f, 0.18f, 1f))
        for (i in 0 until 80) {
            val rx = (Math.random() * 128).toInt()
            val ry = (Math.random() * 128).toInt()
            pixmap.drawPixel(rx, ry)
            pixmap.drawPixel(rx + 1, ry)
        }
        val tex = Texture(pixmap).apply {
            setWrap(TextureWrap.Repeat, TextureWrap.Repeat)
        }
        pixmap.dispose()
        return tex
    }

    private fun loadFaceTextures() {
        faceTextures.clear()
        val keys = listOf("face", "face1", "face2", "face3")
        for (k in keys) {
            val file = sequenceOf(
                Gdx.files.local("$k.png"),
                Gdx.files.internal("$k.png"),
                Gdx.files.internal("src/main/resources/$k.png")
            ).firstOrNull { it.exists() }

            if (file != null) {
                val tex = Texture(file)
                tex.setFilter(TextureFilter.Nearest, TextureFilter.Nearest)
                faceTextures.add(tex)
            } else {
                faceTextures.add(createPlaceholderFaceTexture(k))
            }
        }
    }

    private fun createPlaceholderFaceTexture(key: String): Texture {
        val pixmap = Pixmap(200, 200, Pixmap.Format.RGBA8888)
        pixmap.setColor(Color.BLACK)
        pixmap.fill()
        pixmap.setColor(Color.RED)
        val offset = when (key) {
            "face1" -> 15
            "face2" -> -15
            "face3" -> 25
            else -> 0
        }
        pixmap.fillRectangle(30 + offset, 50, 40, 30)
        pixmap.fillRectangle(130 - offset, 50, 40, 30)
        pixmap.setColor(Color.WHITE)
        pixmap.fillRectangle(40, 130 + offset / 2, 120, 35)
        val tex = Texture(pixmap)
        pixmap.dispose()
        return tex
    }

    fun updateAndRender(
        delta: Float,
        modelBatch: Any?,
        spriteBatch: SpriteBatch,
        shapeRenderer: ShapeRenderer,
        font: BitmapFont,
        uiViewport: FitViewport,
        onFinish: () -> Unit
    ) {
        if (gameTimer < gameDuration) {
            gameTimer += delta
            updatePlayerAndEnemies(delta)

            // 1. Отрисовка мира, травы, игрока и кубиков
            renderWorldPass(spriteBatch, shapeRenderer, uiViewport)

            // 2. Наложение реалистичного размытого света фонарика
            renderLightMapPass(shapeRenderer, spriteBatch, uiViewport)

            // 3. Помехи камеры
            renderGlitchOverlay(shapeRenderer, uiViewport)
        } else {
            if (jumpscareTimer == 0f) {
                screamSound?.play(1.0f)
            }
            jumpscareTimer += delta
            renderJumpscare(spriteBatch, shapeRenderer, font, uiViewport)

            if (jumpscareTimer >= jumpscareDuration) {
                onFinish()
            }
        }
    }

    private fun updatePlayerAndEnemies(delta: Float) {
        // Движение игрока (WASD)
        var moveX = 0f
        var moveY = 0f
        if (Gdx.input.isKeyPressed(Input.Keys.W)) moveY += 1f
        if (Gdx.input.isKeyPressed(Input.Keys.S)) moveY -= 1f
        if (Gdx.input.isKeyPressed(Input.Keys.A)) moveX -= 1f
        if (Gdx.input.isKeyPressed(Input.Keys.D)) moveX += 1f

        if (moveX != 0f || moveY != 0f) {
            val len = Math.sqrt((moveX * moveX + moveY * moveY).toDouble()).toFloat()
            playerX += (moveX / len) * playerSpeed * delta
            playerY += (moveY / len) * playerSpeed * delta
        }

        cameraX += (playerX - cameraX) * 0.08f * (delta * 60f)
        cameraY += (playerY - cameraY) * 0.08f * (delta * 60f)

        var camShakeX = 0f
        var camShakeY = 0f
        if (glitchIntensity > 0f) {
            camShakeX = (MathUtils.random() - 0.5f) * glitchIntensity * 16f
            camShakeY = (MathUtils.random() - 0.5f) * glitchIntensity * 16f
        }

        worldCamera.position.set(cameraX + camShakeX, cameraY + camShakeY, 0f)
        worldCamera.update()

        val mousePos = Vector3(Gdx.input.x.toFloat(), Gdx.input.y.toFloat(), 0f)
        worldCamera.unproject(mousePos)
        aimAngleDeg = MathUtils.atan2(mousePos.y - playerY, mousePos.x - playerX) * MathUtils.radiansToDegrees

        val rad = aimAngleDeg * MathUtils.degreesToRadians
        val tipX = playerX + tipDist * MathUtils.cos(rad)
        val tipY = playerY + tipDist * MathUtils.sin(rad)

        // Спавн фиолетовых кубиков
        enemySpawnTimer += delta
        if (enemySpawnTimer >= 0.7f) {
            enemySpawnTimer = 0f
            val spawnAngle = MathUtils.random(0f, 360f)
            val spawnDist = MathUtils.random(420f, 540f)
            val ex = playerX + spawnDist * MathUtils.cosDeg(spawnAngle)
            val ey = playerY + spawnDist * MathUtils.sinDeg(spawnAngle)
            enemies.add(DarkEnemy2D(ex, ey, ex, ey))
        }

        var maxProximityGlitch = 0f
        var touchedByEnemy = false

        val iterator = enemies.iterator()
        while (iterator.hasNext()) {
            val enemy = iterator.next()

            val dxToPlayer = playerX - enemy.x
            val dyToPlayer = playerY - enemy.y
            val distToPlayer = Math.sqrt((dxToPlayer * dxToPlayer + dyToPlayer * dyToPlayer).toDouble()).toFloat()

            // Проверка попадания света фонарика
            val dxToTip = enemy.x - tipX
            val dyToTip = enemy.y - tipY
            val distToTip = Math.sqrt((dxToTip * dxToTip + dyToTip * dyToTip).toDouble()).toFloat()

            val enemyAngle = MathUtils.atan2(dyToTip, dxToTip) * MathUtils.radiansToDegrees
            val rawDiff = abs(enemyAngle - aimAngleDeg) % 360f
            val angleDiff = if (rawDiff > 180f) 360f - rawDiff else rawDiff
            val inLightBeam = (angleDiff < 30f && distToTip < 450f)

            when (enemy.state) {
                EnemyState.STALKING -> {
                    // Движение к игроку
                    if (distToPlayer > 0.1f) {
                        enemy.x += (dxToPlayer / distToPlayer) * enemy.speed * delta
                        enemy.y += (dyToPlayer / distToPlayer) * enemy.speed * delta
                    }

                    // Коллизия с игроком (скример)
                    if (distToPlayer < 20f) {
                        touchedByEnemy = true
                    }

                    // Помехи на расстоянии
                    if (distToPlayer < proximityDist) {
                        val prox = 1f - (distToPlayer / proximityDist)
                        maxProximityGlitch = max(maxProximityGlitch, prox)
                    }

                    // ПОПАДАНИЕ СВЕТА: Замирает на 0.5 секунды
                    if (inLightBeam) {
                        enemy.state = EnemyState.FROZEN
                        enemy.freezeTimer = 0.5f
                    }
                }

                EnemyState.FROZEN -> {
                    // Замер в луче света на полсекунды
                    enemy.freezeTimer -= delta
                    if (enemy.freezeTimer <= 0f) {
                        // После замирания разворачивается и убегает
                        enemy.state = EnemyState.FLEEING
                    }
                }

                EnemyState.FLEEING -> {
                    // Убегает прочь от игрока в сторону своего появления
                    val dxFromPlayer = enemy.x - playerX
                    val dyFromPlayer = enemy.y - playerY
                    val distFromPlayer = Math.sqrt((dxFromPlayer * dxFromPlayer + dyFromPlayer * dyFromPlayer).toDouble()).toFloat()

                    if (distFromPlayer > 0.1f) {
                        enemy.x += (dxFromPlayer / distFromPlayer) * enemy.fleeSpeed * delta
                        enemy.y += (dyFromPlayer / distFromPlayer) * enemy.fleeSpeed * delta
                    }

                    // Как только убежал далеко за экран в темноту — исчезает
                    if (distFromPlayer > 580f) {
                        iterator.remove()
                    }
                }
            }
        }

        if (touchedByEnemy && gameTimer < gameDuration) {
            gameTimer = gameDuration
            glitchIntensity = 1.0f
        }

        if (maxProximityGlitch > 0f) {
            glitchIntensity += (maxProximityGlitch - glitchIntensity) * 0.18f * (delta * 60f)
        } else {
            glitchIntensity = max(0f, glitchIntensity - delta * 0.45f)
        }
    }

    private fun renderWorldPass(spriteBatch: SpriteBatch, shapeRenderer: ShapeRenderer, uiViewport: FitViewport) {
        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        uiViewport.apply()
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST)
        Gdx.gl.glScissor(uiViewport.screenX, uiViewport.screenY, uiViewport.screenWidth, uiViewport.screenHeight)

        // 1. Бесконечная трава
        if (grassTexture != null) {
            spriteBatch.projectionMatrix = worldCamera.combined
            spriteBatch.begin()

            val left = worldCamera.position.x - 400f
            val bottom = worldCamera.position.y - 300f
            val texW = grassTexture!!.width.toFloat()
            val texH = grassTexture!!.height.toFloat()

            val u = left / texW
            val v = bottom / texH
            val u2 = (left + 800f) / texW
            val v2 = (bottom + 600f) / texH

            spriteBatch.color = Color.WHITE
            spriteBatch.draw(grassTexture, left, bottom, 800f, 600f, u, v, u2, v2)
            spriteBatch.end()
        }

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        shapeRenderer.projectionMatrix = worldCamera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)

        // 2. Игрок (Белый 2D кубик)
        shapeRenderer.color = Color.WHITE
        shapeRenderer.rect(playerX - 12f, playerY - 12f, 24f, 24f)

        // 3. Фиолетовые кубики врагов (со сменой состояний)
        for (e in enemies) {
            when (e.state) {
                EnemyState.STALKING -> {
                    shapeRenderer.color = Color(0.85f, 0.20f, 1.0f, 1.0f)
                    shapeRenderer.rect(e.x - 9f, e.y - 9f, 18f, 18f)
                }
                EnemyState.FROZEN -> {
                    // Замерший кубик дрожит от ослепления
                    val shakeJitterX = (MathUtils.random() - 0.5f) * 4f
                    val shakeJitterY = (MathUtils.random() - 0.5f) * 4f
                    shapeRenderer.color = Color(1.0f, 0.8f, 1.0f, 1.0f) // Яркий засвеченный цвет
                    shapeRenderer.rect(e.x - 9f + shakeJitterX, e.y - 9f + shakeJitterY, 18f, 18f)
                }
                EnemyState.FLEEING -> {
                    // Убегающий кубик
                    shapeRenderer.color = Color(0.70f, 0.15f, 0.85f, 0.9f)
                    shapeRenderer.rect(e.x - 9f, e.y - 9f, 18f, 18f)
                }
            }
        }

        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        // 4. Спрайт фонарика flashlight.png на орбите
        val fTex = flashlightTexture
        if (fTex != null) {
            spriteBatch.projectionMatrix = worldCamera.combined
            spriteBatch.begin()

            val rad = aimAngleDeg * MathUtils.degreesToRadians
            val bodyX = playerX + bodyDist * MathUtils.cos(rad)
            val bodyY = playerY + bodyDist * MathUtils.sin(rad)

            val aspect = fTex.width.toFloat() / fTex.height.toFloat()
            val flashH = 38f
            val flashW = flashH * aspect

            spriteBatch.color = Color.WHITE
            spriteBatch.draw(
                fTex,
                bodyX - flashW / 2f, bodyY - flashH / 2f,
                flashW / 2f, flashH / 2f,
                flashW, flashH,
                1f, 1f,
                aimAngleDeg - 90f,
                0, 0, fTex.width, fTex.height,
                false, false
            )
            spriteBatch.end()
        }

        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST)
    }

    private fun renderLightMapPass(shapeRenderer: ShapeRenderer, spriteBatch: SpriteBatch, uiViewport: FitViewport) {
        val fbo = lightFbo ?: return

        val rad = aimAngleDeg * MathUtils.degreesToRadians
        val tipX = playerX + tipDist * MathUtils.cos(rad)
        val tipY = playerY + tipDist * MathUtils.sin(rad)

        fbo.begin()
        Gdx.gl.glViewport(0, 0, 800, 600)
        Gdx.gl.glClearColor(0.07f, 0.08f, 0.12f, 1.0f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE)

        shapeRenderer.projectionMatrix = worldCamera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)

        // Размытый луч бьет точно из линзы фонарика
        drawSmoothLightCone(shapeRenderer, tipX, tipY, aimAngleDeg, 450f)

        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)
        fbo.end()

        uiViewport.apply()
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST)
        Gdx.gl.glScissor(uiViewport.screenX, uiViewport.screenY, uiViewport.screenWidth, uiViewport.screenHeight)

        spriteBatch.projectionMatrix = uiViewport.camera.combined
        spriteBatch.setBlendFunction(GL20.GL_DST_COLOR, GL20.GL_ZERO)
        spriteBatch.begin()

        val lightTexture = fbo.colorBufferTexture
        lightTexture.setFilter(TextureFilter.Linear, TextureFilter.Linear)
        spriteBatch.draw(lightTexture, 0f, 0f, 800f, 600f, 0, 0, 800, 600, false, true)

        spriteBatch.end()
        spriteBatch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST)
    }

    private fun drawSmoothLightCone(shapeRenderer: ShapeRenderer, px: Float, py: Float, aimAngle: Float, length: Float) {
        val totalHalfSpread = 30f
        val steps = 36
        val angleStep = (totalHalfSpread * 2f) / steps

        for (i in 0 until steps) {
            val offset = -totalHalfSpread + i * angleStep
            val a1 = (aimAngle + offset) * MathUtils.degreesToRadians
            val a2 = (aimAngle + offset + angleStep) * MathUtils.degreesToRadians

            val normOffset = abs(offset + angleStep / 2f) / totalHalfSpread
            val alphaFactor = max(0f, 1f - normOffset * normOffset)

            shapeRenderer.color = Color(1f, 1f, 1f, 0.95f * alphaFactor)

            val x1 = px + length * MathUtils.cos(a1)
            val y1 = py + length * MathUtils.sin(a1)

            val x2 = px + length * MathUtils.cos(a2)
            val y2 = py + length * MathUtils.sin(a2)

            shapeRenderer.triangle(px, py, x1, y1, x2, y2)
        }
    }

    private fun renderGlitchOverlay(shapeRenderer: ShapeRenderer, uiViewport: FitViewport) {
        if (glitchIntensity <= 0.05f) return

        uiViewport.apply()
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        shapeRenderer.projectionMatrix = uiViewport.camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)

        val numSlices = (glitchIntensity * 28).toInt()
        for (i in 0 until numSlices) {
            val sliceY = MathUtils.random(0f, 600f)
            val sliceH = MathUtils.random(2f, 18f * glitchIntensity)
            val offsetX = (MathUtils.random() - 0.5f) * 80f * glitchIntensity

            val r = MathUtils.random(0.5f, 1f)
            val g = MathUtils.random(0f, 0.4f)
            val b = MathUtils.random(0.5f, 1f)

            shapeRenderer.color = Color(r, g, b, MathUtils.random(0.2f, 0.6f) * glitchIntensity)
            shapeRenderer.rect(offsetX, sliceY, 800f, sliceH)
        }

        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    private fun renderJumpscare(
        spriteBatch: SpriteBatch,
        shapeRenderer: ShapeRenderer,
        font: BitmapFont,
        uiViewport: FitViewport
    ) {
        uiViewport.apply()

        val flashRed = if ((jumpscareTimer * 20).toInt() % 2 == 0) 0.85f else 0.05f

        shapeRenderer.projectionMatrix = uiViewport.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = Color(flashRed, 0f, 0f, 1f)
        shapeRenderer.rect(0f, 0f, 800f, 600f)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        spriteBatch.projectionMatrix = uiViewport.camera.combined
        spriteBatch.begin()

        val frameIndex = MathUtils.random(0, 3)
        val currentFaceTex = faceTextures.getOrNull(frameIndex)

        val glitchX = (Math.random() * 30.0 - 15.0).toFloat()
        val glitchY = (Math.random() * 30.0 - 15.0).toFloat()
        val faceScale = 1.0f + (Math.random() * 0.2 - 0.1).toFloat()

        val faceW = 320f * faceScale
        val faceH = 320f * faceScale
        val faceX = 400f - faceW / 2f + glitchX
        val faceY = 300f - faceH / 2f + glitchY

        if (currentFaceTex != null) {
            spriteBatch.draw(currentFaceTex, faceX, faceY, faceW, faceH)
        }

        font.color = Color.WHITE
        font.draw(spriteBatch, "I SEE YOU", 330f + glitchX, 80f + glitchY)

        spriteBatch.end()
    }

    private fun createScreamSound(): Sound {
        val sampleRate = 44100
        val durationMs = 1500
        val numSamples = sampleRate * durationMs / 1000
        val pcm = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            val t = i.toFloat() / sampleRate

            val f1 = 800f + MathUtils.sin(t * 120f) * 600f
            val f2 = 2200f + MathUtils.sin(t * 80f) * 1000f
            val noise = (Math.random() * 2.0 - 1.0).toFloat() * 0.5f

            val s1 = MathUtils.sin(2.0f * MathUtils.PI * f1 * t)
            val s2 = MathUtils.sin(2.0f * MathUtils.PI * f2 * t)
            val envelope = if (progress < 0.05f) progress / 0.05f else (1f - progress)

            val mix = (s1 * 0.3f + s2 * 0.3f + noise * 0.4f) * envelope * 32767f * 0.95f
            pcm[i] = MathUtils.clamp(mix.toInt(), -32767, 32767).toShort()
        }

        val pcmBytes = ByteArray(numSamples * 2)
        for (i in 0 until numSamples) {
            val s = pcm[i].toInt()
            pcmBytes[i * 2] = (s and 0xFF).toByte()
            pcmBytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }

        val totalDataLen = pcmBytes.size
        val totalBytes = totalDataLen + 44
        val wav = ByteArray(totalBytes)

        wav[0] = 'R'.code.toByte(); wav[1] = 'I'.code.toByte(); wav[2] = 'F'.code.toByte(); wav[3] = 'F'.code.toByte()
        val chunkSize = totalBytes - 8
        wav[4] = (chunkSize and 0xFF).toByte()
        wav[5] = ((chunkSize shr 8) and 0xFF).toByte()
        wav[6] = ((chunkSize shr 16) and 0xFF).toByte()
        wav[7] = ((chunkSize shr 24) and 0xFF).toByte()
        wav[8] = 'W'.code.toByte(); wav[9] = 'A'.code.toByte(); wav[10] = 'V'.code.toByte(); wav[11] = 'E'.code.toByte()

        wav[12] = 'f'.code.toByte(); wav[13] = 'm'.code.toByte(); wav[14] = 't'.code.toByte(); wav[15] = ' '.code.toByte()
        wav[16] = 16; wav[17] = 0; wav[18] = 0; wav[19] = 0
        wav[20] = 1; wav[21] = 0
        wav[22] = 1; wav[23] = 0
        wav[24] = (sampleRate and 0xFF).toByte()
        wav[25] = ((sampleRate shr 8) and 0xFF).toByte()
        wav[26] = ((sampleRate shr 16) and 0xFF).toByte()
        wav[27] = ((sampleRate shr 24) and 0xFF).toByte()
        val byteRate = sampleRate * 2
        wav[28] = (byteRate and 0xFF).toByte()
        wav[29] = ((byteRate shr 8) and 0xFF).toByte()
        wav[30] = ((byteRate shr 16) and 0xFF).toByte()
        wav[31] = ((byteRate shr 24) and 0xFF).toByte()
        wav[32] = 2; wav[33] = 0
        wav[34] = 16; wav[35] = 0

        wav[36] = 'd'.code.toByte(); wav[37] = 'a'.code.toByte(); wav[38] = 't'.code.toByte(); wav[39] = 'a'.code.toByte()
        wav[40] = (totalDataLen and 0xFF).toByte()
        wav[41] = ((totalDataLen shr 8) and 0xFF).toByte()
        wav[42] = ((totalDataLen shr 16) and 0xFF).toByte()
        wav[43] = ((totalDataLen shr 24) and 0xFF).toByte()

        System.arraycopy(pcmBytes, 0, wav, 44, totalDataLen)

        val tempFile = Gdx.files.local("sound_scream_${System.currentTimeMillis()}.wav")
        tempFile.writeBytes(wav, false)
        val snd = Gdx.audio.newSound(tempFile)
        tempFile.delete()
        return snd
    }

    fun dispose() {
        screamSound?.dispose()
        for (tex in faceTextures) {
            tex.dispose()
        }
        faceTextures.clear()
        grassTexture?.dispose()
        flashlightTexture?.dispose()
        lightFbo?.dispose()
    }
}