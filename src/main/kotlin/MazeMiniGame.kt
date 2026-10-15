import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.PointLight
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.viewport.FitViewport

enum class MazeGameState {
    PLAYING, WON, TIMEOUT
}

class MazeMiniGame {
    private val mazeParser = MazeParser()
    private val fpEngine = FirstPersonEngine()

    private var worldData: MazeWorldData? = null
    private var beaconInstance: ModelInstance? = null

    private lateinit var environment: Environment
    private val goalPulsingLight = PointLight()

    private val glyphLayout = GlyphLayout()
    var timeRemaining = 120.0f
        private set

    var state = MazeGameState.PLAYING
        private set

    private var winSound: Sound? = null
    private var loseSound: Sound? = null
    private var beaconAnimTimer = 0f

    fun init() {
        if (worldData == null) {
            worldData = mazeParser.loadAndBuildMaze()
        }

        val data = worldData!!
        beaconInstance = ModelInstance(data.goalBeaconModel)
        fpEngine.init(data.spawnPosition)

        beaconInstance?.transform?.setToTranslation(data.goalPosition.x, data.wallHeight * 0.4f, data.goalPosition.z)

        environment = Environment().apply {
            set(ColorAttribute(ColorAttribute.AmbientLight, 0.04f, 0.05f, 0.07f, 1f))
            set(ColorAttribute.createFog(0.01f, 0.01f, 0.02f, 1f))
        }

        goalPulsingLight.set(Color(1f, 0.15f, 0.15f, 1f), data.goalPosition.x, 2.0f, data.goalPosition.z, 14f)
        environment.add(goalPulsingLight)

        fpEngine.setupLighting(environment)

        timeRemaining = 120.0f
        state = MazeGameState.PLAYING
        beaconAnimTimer = 0f
    }

    fun updateAndRender(
        delta: Float,
        modelBatch: ModelBatch,
        spriteBatch: SpriteBatch,
        shapeRenderer: ShapeRenderer,
        font: BitmapFont,
        titleFont: BitmapFont,
        uiViewport: FitViewport,
        onFinish: () -> Unit
    ) {
        val data = worldData ?: return

        beaconAnimTimer += delta * 3.5f
        val pulse = (MathUtils.sin(beaconAnimTimer) + 1f) * 0.5f
        goalPulsingLight.intensity = 10f + pulse * 12f

        if (state == MazeGameState.PLAYING) {
            timeRemaining -= delta
            if (timeRemaining <= 0f) {
                timeRemaining = 0f
                state = MazeGameState.TIMEOUT
            }

            fpEngine.update(delta, data)

            val dx = fpEngine.position.x - data.goalPosition.x
            val dz = fpEngine.position.z - data.goalPosition.z
            val distToGoal = Math.sqrt((dx * dx + dz * dz).toDouble()).toFloat()

            if (distToGoal < data.cellSize * 0.75f) {
                state = MazeGameState.WON
            }
        }

        // 1. Отрисовка 3D сцены
        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        Gdx.gl.glClearColor(0.01f, 0.01f, 0.02f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        modelBatch.begin(fpEngine.camera)
        for (inst in data.instances) {
            modelBatch.render(inst, environment)
        }
        beaconInstance?.let { modelBatch.render(it, environment) }
        modelBatch.end()

        // 2. Отрисовка интерфейса
        uiViewport.apply()
        renderUI(spriteBatch, shapeRenderer, font, titleFont, uiViewport, onFinish)
    }

    private fun renderUI(
        spriteBatch: SpriteBatch,
        shapeRenderer: ShapeRenderer,
        font: BitmapFont,
        titleFont: BitmapFont,
        uiViewport: FitViewport,
        onFinish: () -> Unit
    ) {
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)

        if (state == MazeGameState.PLAYING) {
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            shapeRenderer.projectionMatrix = uiViewport.camera.combined
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            shapeRenderer.color = Color(1f, 1f, 1f, 0.45f)
            shapeRenderer.rect(400f - 2f, 300f - 2f, 4f, 4f)
            shapeRenderer.end()
            Gdx.gl.glDisable(GL20.GL_BLEND)
        }

        spriteBatch.projectionMatrix = uiViewport.camera.combined
        spriteBatch.begin()

        val minutes = (timeRemaining / 60).toInt()
        val seconds = (timeRemaining % 60).toInt()
        val millis = ((timeRemaining % 1f) * 100).toInt()
        val timeFormatted = String.format("%02d:%02d.%02d", minutes, seconds, millis)

        if (state == MazeGameState.PLAYING) {
            font.color = if (timeRemaining <= 20f) Color(1f, 0.3f, 0.3f, 1f) else Color(1f, 0.9f, 0.4f, 1f)
            drawCenteredText(spriteBatch, font, "TIME REMAINING: $timeFormatted", 400f, 570f)

            font.color = Color(0.7f, 0.85f, 1f, 0.8f)
            drawCenteredText(spriteBatch, font, "Find the RED EXIT zone!", 400f, 535f)

            font.color = Color(0.5f, 0.55f, 0.65f, 0.7f)
            font.draw(spriteBatch, "WASD - MOVE   |   MOUSE - LOOK   |   ESC - EXIT", 25f, 35f)
        } else {
            spriteBatch.end()

            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            shapeRenderer.projectionMatrix = uiViewport.camera.combined
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            shapeRenderer.color = Color(0.05f, 0.07f, 0.10f, 0.92f)
            shapeRenderer.rect(120f, 160f, 560f, 280f)
            shapeRenderer.end()
            Gdx.gl.glDisable(GL20.GL_BLEND)

            spriteBatch.begin()
            val centerX = 400f

            if (state == MazeGameState.WON) {
                titleFont.color = Color(0.35f, 0.95f, 0.45f, 1f)
                drawCenteredText(spriteBatch, titleFont, "MAZE ESCAPED!", centerX, 395f)

                font.color = Color.WHITE
                drawCenteredText(spriteBatch, font, "You reached the red beacon in time!", centerX, 335f)

                font.color = Color(1f, 0.85f, 0.3f, 1f)
                drawCenteredText(spriteBatch, font, "Time Left: $timeFormatted", centerX, 290f)
            } else {
                titleFont.color = Color(1f, 0.35f, 0.35f, 1f)
                drawCenteredText(spriteBatch, titleFont, "TIME IS UP!", centerX, 395f)

                font.color = Color(0.85f, 0.85f, 0.9f, 1f)
                drawCenteredText(spriteBatch, font, "You were lost in the dark labyrinth.", centerX, 335f)
            }

            font.color = Color(0.6f, 0.85f, 1f, 1f)
            drawCenteredText(spriteBatch, font, "PRESS SPACE OR ENTER TO RETURN", centerX, 220f)

            if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.justTouched()) {
                onFinish()
            }
        }

        spriteBatch.end()
    }

    private fun drawCenteredText(batch: SpriteBatch, font: BitmapFont, text: String, xCenter: Float, y: Float) {
        glyphLayout.setText(font, text)
        font.draw(batch, text, xCenter - glyphLayout.width / 2f, y)
    }

    fun dispose() {
        worldData?.models?.forEach { it.dispose() }
        worldData?.goalBeaconModel?.dispose()
        worldData?.grassTexture?.dispose()
        worldData?.bricksTexture?.dispose()
        winSound?.dispose()
        loseSound?.dispose()
    }
}