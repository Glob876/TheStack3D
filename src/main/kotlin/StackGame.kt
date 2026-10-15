import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Texture.TextureFilter
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g2d.Animation
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array as GdxArray
import com.badlogic.gdx.utils.viewport.FitViewport
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class GameMusicManager {
    private val musicMap = mutableMapOf<String, Music>()
    private var currentTrackKey: String? = null
    private var activeMusic: Music? = null
    private var fadingOutMusic: Music? = null

    private var activeVol = 0f
    private var fadeOutVol = 0f
    private val crossfadeSpeed = 2.2f

    fun init() {
        listOf("layerbit", "layers", "glob", "enemies").forEach { name ->
            loadMusicFile(name)?.let { musicMap[name] = it }
        }
    }

    private fun loadMusicFile(name: String): Music? {
        val file = sequenceOf(
            Gdx.files.local("$name.ogg"),
            Gdx.files.internal("$name.ogg"),
            Gdx.files.internal("src/main/resources/$name.ogg"),
            Gdx.files.local("$name.wav"),
            Gdx.files.internal("$name.wav"),
            Gdx.files.internal("src/main/resources/$name.wav"),
            Gdx.files.local("$name.mp3"),
            Gdx.files.internal("$name.mp3"),
            Gdx.files.internal("src/main/resources/$name.mp3")
        ).firstOrNull { it.exists() }

        return if (file != null) {
            try {
                Gdx.audio.newMusic(file).apply { isLooping = true }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } else null
    }

    fun play(trackKey: String) {
        if (currentTrackKey == trackKey) return

        if (activeMusic != null && activeMusic != musicMap[trackKey]) {
            fadingOutMusic?.stop()
            fadingOutMusic = activeMusic
            fadeOutVol = activeVol
        }

        currentTrackKey = trackKey
        activeMusic = musicMap[trackKey]
        activeVol = 0f

        activeMusic?.let {
            it.volume = 0f
            if (!it.isPlaying) it.play()
        }
    }

    fun stopAll(immediate: Boolean = true) {
        if (immediate) {
            musicMap.values.forEach {
                if (it.isPlaying) it.stop()
            }
            activeMusic = null
            fadingOutMusic = null
            currentTrackKey = null
            activeVol = 0f
            fadeOutVol = 0f
        } else {
            if (currentTrackKey == null) return
            fadingOutMusic = activeMusic
            fadeOutVol = activeVol
            activeMusic = null
            currentTrackKey = null
        }
    }

    fun update(delta: Float, masterVolume: Float) {
        fadingOutMusic?.let { music ->
            fadeOutVol = max(0f, fadeOutVol - delta * crossfadeSpeed)
            music.volume = fadeOutVol * masterVolume
            if (fadeOutVol <= 0f) {
                music.stop()
                fadingOutMusic = null
            }
        }

        activeMusic?.let { music ->
            activeVol = min(1f, activeVol + delta * crossfadeSpeed)
            music.volume = activeVol * masterVolume
            if (!music.isPlaying && masterVolume > 0f) {
                music.play()
            }
        }
    }

    fun dispose() {
        musicMap.values.forEach { it.dispose() }
        musicMap.clear()
    }
}

class StackGame : ApplicationAdapter() {
    private lateinit var camera: PerspectiveCamera
    private lateinit var gameViewport: FitViewport
    private lateinit var uiViewport: FitViewport

    private lateinit var modelBatch: ModelBatch
    private lateinit var spriteBatch: SpriteBatch
    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var font: BitmapFont
    private lateinit var titleFont: BitmapFont
    private val glyphLayout = GlyphLayout()
    private val uiTransformMatrix = Matrix4()

    private lateinit var environment: Environment
    private lateinit var boxModel: Model
    private lateinit var pewSound: Sound
    private lateinit var explosionSound: Sound

    private var rickrollMusic: Music? = null
    private var rickrollSound: Sound? = null
    private val musicManager = GameMusicManager()

    private lateinit var movingInstance: ModelInstance
    private lateinit var failedInstance: ModelInstance

    private val mainMenuGui = MainMenuGui()
    private lateinit var collectionGui: TowerCollectionGui
    private lateinit var dialogueSystem: DialogueSystem
    private lateinit var settingsGui: SettingsGui
    private lateinit var stockExchangeGui: StockExchangeGui

    private val secretMiniGame = SecretMiniGame()
    private val mazeMiniGame = MazeMiniGame()

    private var previousGameState = GameState.START
    private var wasFullscreenBeforeMiniGame = false
    private var miniGameTriggeredThisMatch = false

    private var explosionAnim: Animation<TextureRegion>? = null
    private var rickrollAnim: Animation<TextureRegion>? = null
    private var isRickrollActive = false

    private var explosionTime = 0f
    private var explosionTriggered = false

    private val blocks = mutableListOf<BlockData>()
    private val fallingBlocks = mutableListOf<FallingBlock>()

    private var gameState = GameState.START

    private var currentX = 0f
    private var currentZ = 0f
    private var currentWidth = 3f
    private var currentDepth = 3f
    private val blockHeight = 0.5f

    private var moveAxisX = true
    private var moveSpeed = 4f
    private var moveDirection = 1
    private var moveLimit = 4f

    private var score = 0
    private var highScore = 0
    private var comboCount = 0
    private var comboText = ""
    private var comboTextTimer = 0f
    private var flashIntensity = 0f

    private var failedX = 0f
    private var failedY = 0f
    private var failedZ = 0f
    private var failedWidth = 0f
    private var failedDepth = 0f
    private var failedColor = Color.WHITE
    private var failTimer = 0f

    private var cameraTargetY = 2f
    private var cameraSwayTimer = 0f
    private var shakeTimer = 0f
    private var shakeIntensity = 0f
    private var failCamZoom = 0f
    private var gameOverFadeTimer = 0f

    private val perfectThreshold = 0.15f

    override fun create() {
        modelBatch = ModelBatch()
        spriteBatch = SpriteBatch()
        shapeRenderer = ShapeRenderer()

        font = BitmapFont().apply {
            data.setScale(2f)
            region.texture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest)
        }
        titleFont = BitmapFont().apply {
            data.setScale(3.5f)
            region.texture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest)
        }

        val modelBuilder = ModelBuilder()
        val material = Material(ColorAttribute.createDiffuse(Color.WHITE))
        val attributes = (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
        boxModel = modelBuilder.createBox(1f, 1f, 1f, material, attributes)

        movingInstance = ModelInstance(boxModel)
        failedInstance = ModelInstance(boxModel)

        pewSound = createSynthPewSound()
        explosionSound = loadExplosionSound()
        loadRickrollAudio()
        musicManager.init()

        explosionAnim = sequenceOf(
            Gdx.files.local("explode.gif"),
            Gdx.files.internal("explode.gif"),
            Gdx.files.internal("src/main/resources/explode.gif")
        ).firstOrNull { it.exists() }?.let { loadGifAnimation(it) }

        rickrollAnim = sequenceOf(
            Gdx.files.local("rickroll.gif"),
            Gdx.files.internal("rickroll.gif"),
            Gdx.files.internal("src/main/resources/rickroll.gif"),
            Gdx.files.local("rickroll.png"),
            Gdx.files.internal("rickroll.png")
        ).firstOrNull { it.exists() }?.let { loadGifAnimation(it) }

        environment = Environment().apply {
            set(ColorAttribute(ColorAttribute.AmbientLight, 0.65f, 0.65f, 0.68f, 1f))
            add(DirectionalLight().set(0.45f, 0.45f, 0.45f, -1f, -2.5f, -1.5f))
        }

        camera = PerspectiveCamera(35f, 800f, 600f).apply {
            position.set(3.5f, 2.5f, 3.5f)
            near = 0.1f
            far = 300f
            update()
        }

        gameViewport = FitViewport(800f, 600f, camera)
        uiViewport = FitViewport(800f, 600f)

        collectionGui = TowerCollectionGui()
        settingsGui = SettingsGui()
        stockExchangeGui = StockExchangeGui()

        highScore = collectionGui.loadHighScore()
        collectionGui.loadCollection()

        dialogueSystem = DialogueSystem()

        if (collectionGui.isFirstLaunch()) {
            dialogueSystem.initDefaultGlobDialogue()
            initStartScreen(GameState.DIALOGUE)
        } else if (!collectionGui.isSecondDialogueDone() && highScore > 0) {
            dialogueSystem.initSecondGlobDialogue()
            initStartScreen(GameState.DIALOGUE)
        } else {
            initStartScreen(GameState.START)
        }
    }

    private fun triggerSecretMiniGame() {
        previousGameState = gameState
        wasFullscreenBeforeMiniGame = Gdx.graphics.isFullscreen

        gameState = GameState.SECRET_MINIGAME
        try {
            if (!Gdx.graphics.isFullscreen) {
                Gdx.graphics.setFullscreenMode(Gdx.graphics.displayMode)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        Gdx.input.isCursorCatched = true
        collectionGui.markMiniGameEncountered()
        secretMiniGame.init(boxModel)
    }

    private fun finishSecretMiniGame() {
        Gdx.input.isCursorCatched = false
        if (!wasFullscreenBeforeMiniGame) {
            Gdx.graphics.setWindowedMode(800, 600)
        }

        if (previousGameState == GameState.START) {
            if (collectionGui.isMiniGameEncountered() && !collectionGui.isPostMiniGameDialogueDone()) {
                dialogueSystem.initPostMiniGameGlobDialogue()
                initStartScreen(GameState.DIALOGUE)
                return
            }
        }

        gameState = previousGameState
    }

    private fun triggerMazeMiniGame() {
        previousGameState = gameState
        wasFullscreenBeforeMiniGame = Gdx.graphics.isFullscreen

        gameState = GameState.MAZE_MINIGAME
        Gdx.input.isCursorCatched = true
        mazeMiniGame.init()
    }

    private fun finishMazeMiniGame() {
        Gdx.input.isCursorCatched = false
        gameState = GameState.START
        initStartScreen(GameState.START)
    }

    private fun playRickrollAudio() {
        val vol = settingsGui.sfxVolume
        if (rickrollMusic != null) {
            rickrollMusic?.apply {
                volume = vol
                isLooping = false
                play()
            }
        } else if (rickrollSound != null) {
            rickrollSound?.play(1.0f * vol)
        }
    }

    private fun stopRickrollAudio() {
        rickrollMusic?.stop()
        rickrollSound?.stop()
    }

    private fun triggerDirectRickroll() {
        gameState = GameState.FAIL_ANIMATION
        isRickrollActive = true
        failTimer = 2.0f
        explosionTime = 0f
        explosionTriggered = true
        failedX = 0f
        failedY = 0f
        failedZ = 0f
        failedWidth = 3f
        failedDepth = 3f
        musicManager.stopAll(immediate = true)
        playRickrollAudio()
    }

    private fun createBlockData(x: Float, y: Float, z: Float, w: Float, h: Float, d: Float, color: Color): BlockData {
        val inst = ModelInstance(boxModel)
        inst.materials.first().set(ColorAttribute.createDiffuse(color))
        inst.transform.setToTranslation(x + w / 2f, y + h / 2f, z + d / 2f).scale(w, h, d)
        return BlockData(x, y, z, w, h, d, color, inst)
    }

    private fun createFallingBlock(x: Float, y: Float, z: Float, w: Float, h: Float, d: Float, color: Color, vx: Float = 0f, vz: Float = 0f): FallingBlock {
        val inst = ModelInstance(boxModel)
        inst.materials.first().set(ColorAttribute.createDiffuse(color))
        inst.transform.setToTranslation(x + w / 2f, y + h / 2f, z + d / 2f).scale(w, h, d)
        val rx = (Math.random().toFloat() * 140f + 60f) * if (Math.random() > 0.5) 1f else -1f
        val rz = (Math.random().toFloat() * 140f + 60f) * if (Math.random() > 0.5) 1f else -1f
        return FallingBlock(x, y, z, w, h, d, color, velocityX = vx, velocityZ = vz, rotSpeedX = rx, rotSpeedZ = rz, instance = inst)
    }

    private fun initStartScreen(targetState: GameState = GameState.START) {
        stopRickrollAudio()
        gameState = targetState
        blocks.clear()
        fallingBlocks.clear()
        score = 0
        comboCount = 0
        comboTextTimer = 0f
        flashIntensity = 0f
        shakeTimer = 0f
        failTimer = 0f
        failCamZoom = 0f
        gameOverFadeTimer = 0f
        explosionTriggered = false
        isRickrollActive = false
        miniGameTriggeredThisMatch = false
        currentWidth = 3f
        currentDepth = 3f
        mainMenuGui.reset()

        val baseColor = Color(0.72f, 0.75f, 0.80f, 1f)
        blocks.add(createBlockData(-1.5f, 0f, -1.5f, currentWidth, blockHeight, currentDepth, baseColor))

        moveAxisX = false
        currentX = -1.5f
        currentZ = -1.5f
        cameraTargetY = 2f
    }

    private fun spawnNextBlock() {
        val last = blocks.last()
        moveAxisX = !moveAxisX
        currentX = if (moveAxisX) -moveLimit else last.x
        currentZ = if (!moveAxisX) -moveLimit else last.z
        moveDirection = 1
        cameraTargetY = last.y + 1f
    }

    override fun resize(width: Int, height: Int) {
        gameViewport.update(width, height, true)
        uiViewport.update(width, height, true)
    }

    override fun render() {
        val delta = Gdx.graphics.deltaTime
        handleInput()
        updateLogic(delta)

        if (gameState == GameState.MAZE_MINIGAME) {
            mazeMiniGame.updateAndRender(
                delta, modelBatch, spriteBatch, shapeRenderer, font, titleFont, uiViewport,
                onFinish = { finishMazeMiniGame() }
            )
            return
        }

        if (gameState == GameState.SECRET_MINIGAME) {
            secretMiniGame.updateAndRender(
                delta, modelBatch, spriteBatch, shapeRenderer, font, uiViewport,
                onFinish = { finishSecretMiniGame() }
            )
            return
        }

        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        val baseR = 0.14f
        val baseG = 0.15f
        val baseB = 0.20f
        val bgR = min(1f, baseR + flashIntensity * 0.5f)
        val bgG = min(1f, baseG + flashIntensity * 0.5f)
        val bgB = min(1f, baseB + flashIntensity * 0.5f)

        gameViewport.apply()
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST)
        Gdx.gl.glScissor(gameViewport.screenX, gameViewport.screenY, gameViewport.screenWidth, gameViewport.screenHeight)
        Gdx.gl.glClearColor(bgR, bgG, bgB, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST)

        if (gameState == GameState.TOWER_INSPECT) {
            collectionGui.update3DInspectCamera(delta)
        } else {
            var camDist = 5f + failCamZoom
            if (mainMenuGui.isStartAnimating) {
                val alpha = min(1f, mainMenuGui.startAnimTimer / mainMenuGui.startAnimDuration)
                val progress = Interpolation.swingOut.apply(alpha)
                camDist = MathUtils.lerp(3.5f, 5f, progress)
            } else if (gameState == GameState.START || gameState == GameState.COLLECTION || gameState == GameState.DIALOGUE || gameState == GameState.SETTINGS || gameState == GameState.STOCK_EXCHANGE) {
                camDist = 3.5f
            }

            val tiltAngle = if (gameState == GameState.FAIL_ANIMATION || gameState == GameState.GAME_OVER) {
                0f
            } else {
                cameraSwayTimer += delta * 1.5f
                MathUtils.sin(cameraSwayTimer) * 2.5f
            }

            var camX = camDist
            var camZ = camDist
            var camY = camera.position.y + ((cameraTargetY + 3.8f) - camera.position.y) * 0.05f

            if (shakeTimer > 0f && settingsGui.isCameraShakeEnabled) {
                shakeTimer -= delta
                val curShake = shakeIntensity * (shakeTimer / 0.25f)
                camX += (MathUtils.random() - 0.5f) * curShake
                camY += (MathUtils.random() - 0.5f) * curShake
                camZ += (MathUtils.random() - 0.5f) * curShake
            }

            camera.position.set(camX, camY, camZ)
            camera.up.set(0f, 1f, 0f)
            camera.lookAt(0f, camera.position.y - 4.2f, 0f)
            camera.up.rotate(camera.direction, tiltAngle)
            camera.update()
        }

        val activeCam = if (gameState == GameState.TOWER_INSPECT) collectionGui.inspectCamera else camera
        modelBatch.begin(activeCam)

        if (gameState == GameState.TOWER_INSPECT) {
            val entry = collectionGui.savedTowers.getOrNull(collectionGui.selectedIndex)
            if (entry != null) {
                for (b in entry.data.blocks) {
                    collectionGui.drawBlockInstance(modelBatch, boxModel, environment, b)
                }
            }
        } else if (gameState == GameState.DIALOGUE) {
            if (dialogueSystem.shouldShowPlatform && blocks.isNotEmpty()) {
                val b = blocks[0]
                val inst = b.instance ?: createBlockData(b.x, b.y, b.z, b.width, b.height, b.depth, b.color).instance!!
                modelBatch.render(inst, environment)
            }
        } else {
            for (b in blocks) {
                val inst = b.instance ?: createBlockData(b.x, b.y, b.z, b.width, b.height, b.depth, b.color).instance!!
                modelBatch.render(inst, environment)
            }

            for (fb in fallingBlocks) {
                val inst = fb.instance ?: createFallingBlock(fb.x, fb.y, fb.z, fb.width, fb.height, fb.depth, fb.color).instance!!
                inst.transform.setToTranslation(fb.x + fb.width / 2f, fb.y + fb.height / 2f, fb.z + fb.depth / 2f)
                if (fb.rotationX != 0f || fb.rotationZ != 0f) {
                    inst.transform.rotate(1f, 0f, 0f, fb.rotationX)
                    inst.transform.rotate(0f, 0f, 1f, fb.rotationZ)
                }
                inst.transform.scale(fb.width, fb.height, fb.depth)
                modelBatch.render(inst, environment)
            }

            if (gameState == GameState.PLAYING) {
                val topY = blocks.last().y + blockHeight
                val currentColor = Color().fromHsv((score * 20 % 360).toFloat(), 0.38f, 0.92f)

                movingInstance.materials.first().set(ColorAttribute.createDiffuse(currentColor))
                movingInstance.transform.setToTranslation(currentX + currentWidth / 2f, topY + blockHeight / 2f, currentZ + currentDepth / 2f)
                    .scale(currentWidth, blockHeight, currentDepth)
                modelBatch.render(movingInstance, environment)
            } else if (gameState == GameState.FAIL_ANIMATION && failTimer < 2.0f) {
                failedInstance.materials.first().set(ColorAttribute.createDiffuse(failedColor))
                failedInstance.transform.setToTranslation(failedX + failedWidth / 2f, failedY + blockHeight / 2f, failedZ + failedDepth / 2f)
                    .scale(failedWidth, blockHeight, failedDepth)
                modelBatch.render(failedInstance, environment)
            }
        }

        modelBatch.end()

        // 2D UI РЕНДЕРИНГ: гарантируем отключение теста глубины и куллинга
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)

        when (gameState) {
            GameState.START -> {
                mainMenuGui.render(delta, spriteBatch, shapeRenderer, font, titleFont, uiViewport)
            }
            GameState.STOCK_EXCHANGE -> stockExchangeGui.renderMarketUI(delta, spriteBatch, shapeRenderer, font, titleFont, uiViewport)
            GameState.SETTINGS -> settingsGui.renderSettingsUI(delta, spriteBatch, shapeRenderer, font, titleFont, uiViewport)
            GameState.DIALOGUE -> dialogueSystem.render(spriteBatch, shapeRenderer, font, titleFont, uiViewport)
            GameState.COLLECTION -> collectionGui.renderCollectionUI(delta, spriteBatch, shapeRenderer, font, titleFont, uiViewport)
            GameState.TOWER_INSPECT -> collectionGui.renderInspectOverlay(spriteBatch, font, uiViewport)
            else -> {
                if (mainMenuGui.isStartAnimating) {
                    mainMenuGui.render(delta, spriteBatch, shapeRenderer, font, titleFont, uiViewport)
                }

                uiViewport.apply()
                spriteBatch.projectionMatrix = uiViewport.camera.combined
                spriteBatch.begin()

                if (gameState == GameState.FAIL_ANIMATION && failTimer >= 2.0f) {
                    explosionTime += delta

                    val pos3d = Vector3(failedX + failedWidth / 2f, failedY + blockHeight / 2f, failedZ + failedDepth / 2f)
                    camera.project(pos3d)
                    val uiX = (pos3d.x - gameViewport.screenX) * (800f / gameViewport.screenWidth)
                    val uiY = (pos3d.y - gameViewport.screenY) * (600f / gameViewport.screenHeight)

                    if (isRickrollActive) {
                        val inDuration = 1.0f
                        val exitStartTime = 3.8f
                        val exitDuration = 1.4f

                        val startSize = max(35f, max(failedWidth, failedDepth) * 55f)
                        val targetW = 720f
                        val targetH = 405f
                        val targetX = 400f
                        val targetY = 275f

                        val curW: Float
                        val curH: Float
                        val curX: Float
                        val curY: Float
                        val rot: Float

                        if (explosionTime < inDuration) {
                            val p = Interpolation.pow3Out.apply(min(1f, explosionTime / inDuration))
                            curW = MathUtils.lerp(startSize, targetW, p)
                            curH = MathUtils.lerp(startSize / (16f / 9f), targetH, p)
                            curX = MathUtils.lerp(uiX, targetX, p)
                            curY = MathUtils.lerp(uiY, targetY, p)
                            rot = (1f - p) * 720f
                        } else if (explosionTime < exitStartTime) {
                            curW = targetW
                            curH = targetH
                            curX = targetX
                            curY = targetY
                            rot = 0f
                        } else {
                            val p = Interpolation.pow2In.apply(min(1f, (explosionTime - exitStartTime) / exitDuration))
                            curW = targetW * (1f - p * 0.2f)
                            curH = targetH * (1f - p * 0.2f)
                            curX = targetX
                            curY = targetY - p * 850f
                            rot = -p * 720f
                        }

                        val rAnim = rickrollAnim
                        if (rAnim != null) {
                            val frame = rAnim.getKeyFrame(explosionTime, true)
                            spriteBatch.draw(
                                frame,
                                curX - curW / 2f, curY - curH / 2f,
                                curW / 2f, curH / 2f,
                                curW, curH,
                                1f, 1f,
                                rot
                            )
                        } else {
                            font.color = Color.YELLOW
                            font.draw(spriteBatch, "[ RICKROLL VIDEO 16:9 ]", curX - 100f, curY)
                        }

                        val textCenterX = 400f
                        val textCenterY = 535f
                        val textRot: Float
                        val textScale: Float
                        val textAlpha: Float

                        if (explosionTime < inDuration) {
                            val p = Interpolation.pow3Out.apply(min(1f, explosionTime / inDuration))
                            textRot = -(1f - p) * 720f
                            textScale = p
                            textAlpha = min(1f, explosionTime * 2f)
                        } else if (explosionTime < exitStartTime) {
                            textRot = 0f
                            textScale = 1f
                            textAlpha = 1f
                        } else {
                            val p = Interpolation.pow2In.apply(min(1f, (explosionTime - exitStartTime) / 1.1f))
                            textRot = p * 540f
                            textScale = max(0f, 1f - p)
                            textAlpha = max(0f, 1f - p)
                        }

                        if (textAlpha > 0f && textScale > 0f) {
                            titleFont.data.setScale(3.5f * textScale)
                            glyphLayout.setText(titleFont, "YOU ARE RICKROLLED")
                            val tw = glyphLayout.width
                            val th = glyphLayout.height

                            uiTransformMatrix.idt()
                                .translate(textCenterX, textCenterY, 0f)
                                .rotate(0f, 0f, 1f, textRot)
                                .translate(-textCenterX, -textCenterY, 0f)

                            spriteBatch.transformMatrix = uiTransformMatrix
                            titleFont.color = Color(1f, 0.85f, 0.2f, textAlpha)
                            titleFont.draw(spriteBatch, "YOU ARE RICKROLLED", textCenterX - tw / 2f, textCenterY + th / 2f)

                            uiTransformMatrix.idt()
                            spriteBatch.transformMatrix = uiTransformMatrix
                            titleFont.data.setScale(3.5f)
                        }
                    } else {
                        val anim = explosionAnim
                        if (anim != null) {
                            val frame = anim.getKeyFrame(explosionTime, false)
                            if (!anim.isAnimationFinished(explosionTime)) {
                                val fw = frame.regionWidth.toFloat()
                                val fh = frame.regionHeight.toFloat()
                                val aspect = fw / fh

                                val maxBlockDim = max(failedWidth, failedDepth)
                                val targetSize = max(35f, maxBlockDim * 55f)

                                val drawW: Float
                                val drawH: Float
                                if (aspect >= 1f) {
                                    drawW = targetSize
                                    drawH = targetSize / aspect
                                } else {
                                    drawH = targetSize
                                    drawW = targetSize * aspect
                                }

                                spriteBatch.draw(frame, uiX - drawW / 2f, uiY - drawH / 2f, drawW, drawH)
                            }
                        } else {
                            if (explosionTime < 0.6f) {
                                val alpha = 1f - (explosionTime / 0.6f)
                                font.color = Color(1f, 0.4f, 0.2f, alpha)
                                font.draw(spriteBatch, "* BOOM *", uiX - 60f, uiY + 10f)
                            }
                        }
                    }
                }

                if (gameState == GameState.PLAYING) {
                    font.color = Color.WHITE
                    font.draw(spriteBatch, "SCORE: $score", 30f, 570f)

                    if (comboTextTimer > 0f) {
                        val alpha = min(1f, comboTextTimer)
                        font.color = Color(1f, 0.85f, 0.4f, alpha)
                        font.draw(spriteBatch, comboText, 400f - 110f, 300f + 120f)
                    }
                } else if (gameState == GameState.GAME_OVER) {
                    gameOverFadeTimer += delta
                    val fadeAlpha = min(1f, gameOverFadeTimer / 1.0f)

                    titleFont.color = Color(1f, 0.45f, 0.45f, fadeAlpha)
                    titleFont.draw(spriteBatch, "GAME OVER", 400f - 150f, 300f + 120f)

                    font.color = Color.WHITE
                    font.draw(spriteBatch, "SCORE: $score", 400f - 70f, 300f + 30f)

                    font.color = Color(0.5f, 0.9f, 0.6f, fadeAlpha)
                    font.draw(spriteBatch, "BEST: $highScore", 400f - 60f, 300f - 20f)

                    font.color = Color(0.75f, 0.75f, 0.75f, fadeAlpha)
                    font.draw(spriteBatch, "PRESS SPACE TO RESTART", 400f - 190f, 300f - 90f)
                }

                spriteBatch.end()
            }
        }
    }

    private fun updateLogic(delta: Float) {
        if (comboTextTimer > 0f) comboTextTimer -= delta

        if (flashIntensity > 0f) {
            flashIntensity = max(0f, flashIntensity - 3.5f * delta)
        }

        stockExchangeGui.updateMarketPrices(delta)

        val masterVol = settingsGui.sfxVolume

        when (gameState) {
            GameState.START, GameState.COLLECTION, GameState.SETTINGS, GameState.STOCK_EXCHANGE, GameState.TOWER_INSPECT -> {
                musicManager.play("layerbit")
            }
            GameState.PLAYING -> {
                musicManager.play("layers")
            }
            GameState.FAIL_ANIMATION, GameState.GAME_OVER, GameState.MAZE_MINIGAME -> {
                musicManager.stopAll(immediate = true)
            }
            GameState.DIALOGUE -> {
                musicManager.play("glob")
            }
            GameState.SECRET_MINIGAME -> {
                musicManager.play("enemies")
            }
        }

        musicManager.update(delta, masterVol)

        if (gameState == GameState.DIALOGUE) {
            if (dialogueSystem.isCompleted) {
                when (dialogueSystem.activeDialogueId) {
                    1 -> collectionGui.markFirstLaunchDone()
                    2 -> collectionGui.markSecondDialogueDone()
                    3 -> collectionGui.markPostMiniGameDialogueDone()
                }
                initStartScreen(GameState.START)
            }
            return
        }

        if (gameState == GameState.FAIL_ANIMATION) {
            failTimer += delta

            failCamZoom += (2.2f - failCamZoom) * 0.08f

            if (failTimer >= 2.0f && !explosionTriggered) {
                explosionTriggered = true
                explosionTime = 0f
                if (isRickrollActive) {
                    playRickrollAudio()
                } else {
                    explosionSound.play(1.0f * settingsGui.sfxVolume)
                }
                if (settingsGui.isCameraShakeEnabled) {
                    shakeIntensity = 0.5f
                    shakeTimer = 0.3f
                }
            }

            val maxWait = if (isRickrollActive) 7.5f else 6.0f
            if (failTimer >= maxWait) {
                triggerGameOver()
            }
            return
        }

        val iterator = fallingBlocks.iterator()
        while (iterator.hasNext()) {
            val fb = iterator.next()
            fb.velocityY -= 22f * delta
            fb.x += fb.velocityX * delta
            fb.y += fb.velocityY * delta
            fb.z += fb.velocityZ * delta
            fb.rotationX += fb.rotSpeedX * delta
            fb.rotationZ += fb.rotSpeedZ * delta

            for (block in blocks) {
                val overlapX = (fb.x < block.x + block.width) && (fb.x + fb.width > block.x)
                val overlapZ = (fb.z < block.z + block.depth) && (fb.z + fb.depth > block.z)
                val blockTop = block.y + block.height

                if (overlapX && overlapZ && fb.y < blockTop && fb.y + fb.height > block.y && fb.velocityY < 0f) {
                    fb.y = blockTop
                    fb.velocityY = -fb.velocityY * 0.3f
                    fb.velocityX *= 1.4f
                    fb.velocityZ *= 1.4f
                }
            }

            if (fb.y < -15f) {
                iterator.remove()
            }
        }

        if (gameState != GameState.PLAYING) return

        if (moveAxisX) {
            currentX += moveSpeed * moveDirection * delta
            if (currentX > moveLimit || currentX < -moveLimit) moveDirection *= -1
        } else {
            currentZ += moveSpeed * moveDirection * delta
            if (currentZ > moveLimit || currentZ < -moveLimit) moveDirection *= -1
        }
    }

    private fun handleInput() {
        if (gameState == GameState.MAZE_MINIGAME) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
                finishMazeMiniGame()
            }
            return
        }

        if (gameState == GameState.SECRET_MINIGAME) {
            return
        }

        if (gameState == GameState.STOCK_EXCHANGE) {
            stockExchangeGui.handleInput(
                onClose = { gameState = GameState.START }
            )
            return
        }

        if (gameState == GameState.SETTINGS) {
            settingsGui.handleInput(
                onCloseSettings = { gameState = GameState.START },
                onResetProgress = {
                    collectionGui.loadCollection()
                    highScore = collectionGui.loadHighScore()
                    stockExchangeGui.loadMarketData()
                    dialogueSystem.reset()
                }
            )
            return
        }

        if (gameState == GameState.DIALOGUE) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.justTouched()) {
                dialogueSystem.handleInput()
            }
            return
        }

        if (gameState == GameState.TOWER_INSPECT) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
                gameState = GameState.COLLECTION
                return
            }
        } else if (gameState == GameState.COLLECTION) {
            collectionGui.handleCollectionInput(
                onSelectTower = { gameState = GameState.TOWER_INSPECT },
                onCloseCollection = { gameState = GameState.START }
            )
            return
        } else {
            if (Gdx.input.isKeyJustPressed(Input.Keys.F11)) {
                if (Gdx.graphics.isFullscreen) {
                    Gdx.graphics.setWindowedMode(800, 600)
                } else {
                    Gdx.graphics.setFullscreenMode(Gdx.graphics.displayMode)
                }
            }

            if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
                if (Gdx.graphics.isFullscreen) {
                    Gdx.graphics.setWindowedMode(800, 600)
                }
            }
        }

        when (gameState) {
            GameState.START -> {
                mainMenuGui.handleInput(
                    onStartGame = {
                        gameState = GameState.PLAYING
                        spawnNextBlock()
                    },
                    onOpenCollection = {
                        collectionGui.loadCollection()
                        collectionGui.openMenuAnimation()
                        gameState = GameState.COLLECTION
                    },
                    onOpenExchange = {
                        stockExchangeGui.loadMarketData()
                        gameState = GameState.STOCK_EXCHANGE
                    },
                    onOpenSettings = {
                        settingsGui.loadSettings()
                        gameState = GameState.SETTINGS
                    },
                    onResetDialogue = {
                        collectionGui.resetFirstLaunch()
                        dialogueSystem.initDefaultGlobDialogue()
                        initStartScreen(GameState.DIALOGUE)
                    },
                    onTriggerSecretMiniGame = {
                        triggerSecretMiniGame()
                    },
                    onTriggerRickroll = {
                        triggerDirectRickroll()
                    },
                    onTriggerMazeMiniGame = {
                        triggerMazeMiniGame()
                    }
                )
            }
            GameState.PLAYING -> {
                if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.justTouched()) {
                    placeBlock()
                }
            }
            GameState.GAME_OVER -> {
                if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.isKeyJustPressed(Input.Keys.R)) {
                    if (collectionGui.isMiniGameEncountered() && !collectionGui.isPostMiniGameDialogueDone()) {
                        dialogueSystem.initPostMiniGameGlobDialogue()
                        initStartScreen(GameState.DIALOGUE)
                    } else if (!collectionGui.isSecondDialogueDone() && highScore > 0) {
                        dialogueSystem.initSecondGlobDialogue()
                        initStartScreen(GameState.DIALOGUE)
                    } else {
                        initStartScreen(GameState.START)
                    }
                }
            }
            else -> {}
        }
    }

    private fun placeBlock() {
        val prev = blocks.last()
        val topY = prev.y + blockHeight
        val currentColor = Color().fromHsv((score * 20 % 360).toFloat(), 0.38f, 0.92f)

        if (score >= 50 && !miniGameTriggeredThisMatch && Math.random() < 0.005) {
            miniGameTriggeredThisMatch = true
            triggerSecretMiniGame()
            return
        }

        if (moveAxisX) {
            val diff = currentX - prev.x

            if (abs(diff) < perfectThreshold) {
                currentX = prev.x
                comboCount++
                comboText = "PERFECT! x$comboCount"
                comboTextTimer = 1.2f

                flashIntensity = min(1.0f, 0.25f + comboCount * 0.15f)
                pewSound.play(0.9f * settingsGui.sfxVolume, 1.2f + comboCount * 0.1f, 0f)

                if (settingsGui.isCameraShakeEnabled) {
                    shakeIntensity = min(0.6f, 0.08f + comboCount * 0.05f)
                    shakeTimer = 0.25f
                }
            } else {
                comboCount = 0
                val overlapStart = max(prev.x, currentX)
                val overlapEnd = min(prev.x + prev.width, currentX + currentWidth)
                val newWidth = overlapEnd - overlapStart

                if (newWidth <= 0) {
                    startFailAnimation(topY, currentColor)
                    return
                }

                pewSound.play(0.7f * settingsGui.sfxVolume, 0.95f + (score % 10) * 0.04f, 0f)

                if (currentX < prev.x) {
                    fallingBlocks.add(createFallingBlock(
                        currentX, topY, currentZ, prev.x - currentX, blockHeight, currentDepth, currentColor,
                        vx = -2.5f
                    ))
                } else {
                    fallingBlocks.add(createFallingBlock(
                        overlapEnd, topY, currentZ, (currentX + currentWidth) - overlapEnd, blockHeight, currentDepth, currentColor,
                        vx = 2.5f
                    ))
                }

                currentWidth = newWidth
                currentX = overlapStart
            }
        } else {
            val diff = currentZ - prev.z

            if (abs(diff) < perfectThreshold) {
                currentZ = prev.z
                comboCount++
                comboText = "PERFECT! x$comboCount"
                comboTextTimer = 1.2f

                flashIntensity = min(1.0f, 0.25f + comboCount * 0.15f)
                pewSound.play(0.9f * settingsGui.sfxVolume, 1.2f + comboCount * 0.1f, 0f)

                if (settingsGui.isCameraShakeEnabled) {
                    shakeIntensity = min(0.6f, 0.08f + comboCount * 0.05f)
                    shakeTimer = 0.25f
                }
            } else {
                comboCount = 0
                val overlapStart = max(prev.z, currentZ)
                val overlapEnd = min(prev.z + prev.depth, currentZ + currentDepth)
                val newDepth = overlapEnd - overlapStart

                if (newDepth <= 0) {
                    startFailAnimation(topY, currentColor)
                    return
                }

                pewSound.play(0.7f * settingsGui.sfxVolume, 0.95f + (score % 10) * 0.04f, 0f)

                if (currentZ < prev.z) {
                    fallingBlocks.add(createFallingBlock(
                        currentX, topY, currentZ, currentWidth, blockHeight, prev.z - currentZ, currentColor,
                        vz = -2.5f
                    ))
                } else {
                    fallingBlocks.add(createFallingBlock(
                        currentX, topY, overlapEnd, currentWidth, blockHeight, (currentZ + currentDepth) - overlapEnd, currentColor,
                        vz = 2.5f
                    ))
                }

                currentDepth = newDepth
                currentZ = overlapStart
            }
        }

        blocks.add(createBlockData(currentX, topY, currentZ, currentWidth, blockHeight, currentDepth, currentColor))
        score++
        spawnNextBlock()
    }

    private fun startFailAnimation(topY: Float, color: Color) {
        gameState = GameState.FAIL_ANIMATION
        musicManager.stopAll(immediate = true)

        failedX = currentX
        failedY = topY
        failedZ = currentZ
        failedWidth = currentWidth
        failedDepth = currentDepth
        failedColor = color
        failTimer = 0f
        failCamZoom = 0f
        gameOverFadeTimer = 0f
        explosionTriggered = false

        val triggerRickroll = !collectionGui.isRickrollSeen() && (Math.random() < 0.20)
        if (triggerRickroll) {
            isRickrollActive = true
            collectionGui.markRickrollSeen()
        } else {
            isRickrollActive = false
        }

        if (score > highScore) {
            highScore = score
            collectionGui.saveHighScore(highScore)
        }

        stockExchangeGui.addAccumulatedScore(score)
        collectionGui.saveTowerToCollection(blocks, score, modelBatch, boxModel, environment)
    }

    private fun triggerGameOver() {
        stopRickrollAudio()
        gameState = GameState.GAME_OVER
        gameOverFadeTimer = 0f
    }

    private fun loadExplosionSound(): Sound {
        val file = sequenceOf(
            Gdx.files.local("explode.wav"),
            Gdx.files.internal("explode.wav"),
            Gdx.files.internal("src/main/resources/explode.wav"),
            Gdx.files.local("explode.ogg"),
            Gdx.files.internal("explode.ogg"),
            Gdx.files.internal("src/main/resources/explode.ogg")
        ).firstOrNull { it.exists() }

        return if (file != null) {
            try {
                Gdx.audio.newSound(file)
            } catch (e: Exception) {
                createSynthExplosionSound()
            }
        } else {
            createSynthExplosionSound()
        }
    }

    private fun loadRickrollAudio() {
        val file = sequenceOf(
            Gdx.files.local("rickroll.wav"),
            Gdx.files.internal("rickroll.wav"),
            Gdx.files.internal("src/main/resources/rickroll.wav"),
            Gdx.files.local("rickroll.ogg"),
            Gdx.files.internal("rickroll.ogg"),
            Gdx.files.internal("src/main/resources/rickroll.ogg"),
            Gdx.files.local("rickroll.mp3"),
            Gdx.files.internal("rickroll.mp3"),
            Gdx.files.internal("src/main/resources/rickroll.mp3")
        ).firstOrNull { it.exists() }

        if (file != null) {
            try {
                rickrollMusic = Gdx.audio.newMusic(file)
            } catch (e: Exception) {
                try {
                    rickrollSound = Gdx.audio.newSound(file)
                } catch (e2: Exception) {
                    rickrollSound = createSynthRickrollSound()
                }
            }
        } else {
            rickrollSound = createSynthRickrollSound()
        }
    }

    private fun loadGifAnimation(file: FileHandle): Animation<TextureRegion>? {
        if (!file.exists()) return null
        return try {
            val stream = file.read()
            val readers = ImageIO.getImageReadersByFormatName("gif")
            if (!readers.hasNext()) {
                val tex = Texture(file)
                tex.setFilter(TextureFilter.Nearest, TextureFilter.Nearest)
                return Animation<TextureRegion>(1f, GdxArray(arrayOf(TextureRegion(tex))))
            }
            val reader = readers.next() as ImageReader
            val imageStream = ImageIO.createImageInputStream(stream)
            reader.setInput(imageStream)

            val count = reader.getNumImages(true)
            if (count <= 0) return null

            var canvasWidth = 0
            var canvasHeight = 0
            val streamMeta = reader.streamMetadata
            if (streamMeta != null) {
                val root = streamMeta.getAsTree("javax_imageio_gif_stream_1.0")
                if (root != null) {
                    for (j in 0 until root.childNodes.length) {
                        val node = root.childNodes.item(j)
                        if (node.nodeName.equals("LogicalScreenDescriptor", ignoreCase = true)) {
                            val wAttr = node.attributes.getNamedItem("logicalScreenWidth")?.nodeValue
                            val hAttr = node.attributes.getNamedItem("logicalScreenHeight")?.nodeValue
                            if (wAttr != null && hAttr != null) {
                                canvasWidth = wAttr.toIntOrNull() ?: 0
                                canvasHeight = hAttr.toIntOrNull() ?: 0
                            }
                        }
                    }
                }
            }

            val firstImg = reader.read(0)
            if (canvasWidth <= 0 || canvasHeight <= 0) {
                canvasWidth = firstImg.width
                canvasHeight = firstImg.height
            }

            val masterImage = BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB)
            val masterGraphics = masterImage.createGraphics()
            masterGraphics.background = java.awt.Color(0, 0, 0, 0)

            val regions = GdxArray<TextureRegion>(count)
            var avgFrameDuration = 0.06f

            for (i in 0 until count) {
                val rawFrame = reader.read(i)
                var offsetX = 0
                var offsetY = 0
                var disposal = "none"
                var delayUnits = 6

                val meta = reader.getImageMetadata(i)
                if (meta != null) {
                    val tree = meta.getAsTree("javax_imageio_gif_image_1.0")
                    for (j in 0 until tree.childNodes.length) {
                        val node = tree.childNodes.item(j)
                        if (node.nodeName.equals("ImageDescriptor", ignoreCase = true)) {
                            offsetX = node.attributes.getNamedItem("imageLeftPosition")?.nodeValue?.toIntOrNull() ?: 0
                            offsetY = node.attributes.getNamedItem("imageTopPosition")?.nodeValue?.toIntOrNull() ?: 0
                        } else if (node.nodeName.equals("GraphicControlExtension", ignoreCase = true)) {
                            disposal = node.attributes.getNamedItem("disposalMethod")?.nodeValue ?: "none"
                            val delayStr = node.attributes.getNamedItem("delayTime")?.nodeValue
                            if (delayStr != null) {
                                val d = delayStr.toIntOrNull() ?: 6
                                delayUnits = if (d <= 1) 6 else d
                            }
                        }
                    }
                }

                var prevBackup: BufferedImage? = null
                if (disposal.equals("restoreToPrevious", ignoreCase = true)) {
                    prevBackup = BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB)
                    val g = prevBackup.createGraphics()
                    g.drawImage(masterImage, 0, 0, null)
                    g.dispose()
                }

                masterGraphics.drawImage(rawFrame, offsetX, offsetY, null)

                val pixmap = Pixmap(canvasWidth, canvasHeight, Pixmap.Format.RGBA8888)
                for (x in 0 until canvasWidth) {
                    for (y in 0 until canvasHeight) {
                        val argb = masterImage.getRGB(x, y)
                        val a = (argb ushr 24) and 0xFF
                        val r = (argb ushr 16) and 0xFF
                        val g = (argb ushr 8) and 0xFF
                        val b = argb and 0xFF
                        pixmap.drawPixel(x, y, (r shl 24) or (g shl 16) or (b shl 8) or a)
                    }
                }
                val tex = Texture(pixmap)
                tex.setFilter(TextureFilter.Nearest, TextureFilter.Nearest)
                pixmap.dispose()
                regions.add(TextureRegion(tex))

                if (disposal.equals("restoreToBackgroundColor", ignoreCase = true)) {
                    masterGraphics.clearRect(offsetX, offsetY, rawFrame.width, rawFrame.height)
                } else if (disposal.equals("restoreToPrevious", ignoreCase = true) && prevBackup != null) {
                    masterGraphics.clearRect(0, 0, canvasWidth, canvasHeight)
                    masterGraphics.drawImage(prevBackup, 0, 0, null)
                }

                avgFrameDuration = (delayUnits * 10f) / 1000f
            }

            masterGraphics.dispose()
            reader.dispose()
            imageStream.close()
            stream.close()

            val anim = Animation<TextureRegion>(if (avgFrameDuration <= 0.01f) 0.06f else avgFrameDuration, regions)
            anim.playMode = Animation.PlayMode.LOOP
            anim
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun createSynthPewSound(): Sound {
        val sampleRate = 44100
        val durationMs = 120
        val numSamples = sampleRate * durationMs / 1000
        val pcm = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            val t = i.toFloat() / sampleRate
            val freq = 350f + progress * 550f
            val phase = 2.0 * Math.PI * freq * t
            val envelope = (1f - progress) * (1f - progress)
            pcm[i] = (Math.sin(phase) * 32767 * envelope * 0.7f).toInt().toShort()
        }

        return pcmToSound(pcm, sampleRate)
    }

    private fun createSynthExplosionSound(): Sound {
        val sampleRate = 44100
        val durationMs = 350
        val numSamples = sampleRate * durationMs / 1000
        val pcm = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            val t = i.toFloat() / sampleRate
            val noise = (Math.random() * 2.0 - 1.0)
            val freq = 120f * (1f - progress)
            val wave = Math.sin(2.0 * Math.PI * freq * t)
            val envelope = (1f - progress) * (1f - progress)
            pcm[i] = ((noise * 0.6 + wave * 0.4) * 32767 * envelope * 0.8f).toInt().toShort()
        }

        return pcmToSound(pcm, sampleRate)
    }

    private fun createSynthRickrollSound(): Sound {
        val sampleRate = 44100
        val durationMs = 2800
        val numSamples = sampleRate * durationMs / 1000
        val pcm = ShortArray(numSamples)

        val notes = floatArrayOf(293.66f, 329.63f, 392f, 329.63f, 493.88f, 493.88f, 440f, 392f, 440f)
        val noteDur = (numSamples / notes.size)

        for (i in 0 until numSamples) {
            val noteIdx = min(notes.size - 1, i / noteDur)
            val freq = notes[noteIdx]
            val t = i.toFloat() / sampleRate
            val localT = (i % noteDur).toFloat() / noteDur
            val env = 1f - localT * 0.5f
            val wave = Math.sin(2.0 * Math.PI * freq * t)
            pcm[i] = (wave * 32767 * env * 0.65f).toInt().toShort()
        }

        return pcmToSound(pcm, sampleRate)
    }

    private fun pcmToSound(pcm: ShortArray, sampleRate: Int): Sound {
        val numSamples = pcm.size
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
        wav[20] = 1; wav[21] = 0; wav[22] = 1; wav[23] = 0
        wav[24] = (sampleRate and 0xFF).toByte()
        wav[25] = ((sampleRate shr 8) and 0xFF).toByte()
        wav[26] = ((sampleRate shr 16) and 0xFF).toByte()
        wav[27] = ((sampleRate shr 24) and 0xFF).toByte()
        val byteRate = sampleRate * 2
        wav[28] = (byteRate and 0xFF).toByte()
        wav[29] = ((byteRate shr 8) and 0xFF).toByte()
        wav[30] = ((byteRate shr 16) and 0xFF).toByte()
        wav[31] = ((byteRate shr 24) and 0xFF).toByte()
        wav[32] = 2; wav[33] = 0; wav[34] = 16; wav[35] = 0

        wav[36] = 'd'.code.toByte(); wav[37] = 'a'.code.toByte(); wav[38] = 't'.code.toByte(); wav[39] = 'a'.code.toByte()
        wav[40] = (totalDataLen and 0xFF).toByte()
        wav[41] = ((totalDataLen shr 8) and 0xFF).toByte()
        wav[42] = ((totalDataLen shr 16) and 0xFF).toByte()
        wav[43] = ((totalDataLen shr 24) and 0xFF).toByte()

        System.arraycopy(pcmBytes, 0, wav, 44, totalDataLen)

        val tempFile = Gdx.files.local("sound_temp_${System.currentTimeMillis()}.wav")
        tempFile.writeBytes(wav, false)
        val snd = Gdx.audio.newSound(tempFile)
        tempFile.delete()
        return snd
    }

    override fun dispose() {
        modelBatch.dispose()
        spriteBatch.dispose()
        shapeRenderer.dispose()
        font.dispose()
        titleFont.dispose()
        boxModel.dispose()
        pewSound.dispose()
        explosionSound.dispose()
        rickrollMusic?.dispose()
        rickrollSound?.dispose()
        musicManager.dispose()
        collectionGui.dispose()
        dialogueSystem.dispose()
        secretMiniGame.dispose()
        mazeMiniGame.dispose()
    }
}