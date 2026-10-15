import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Preferences
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.viewport.FitViewport
import kotlin.math.max
import kotlin.math.min

class SettingsGui {
    private val prefs: Preferences get() = Gdx.app.getPreferences("StackGamePrefs")
    private val glyphLayout = GlyphLayout()

    private var smallFont: BitmapFont? = null

    var selectedIndex = 0
    private val totalItems = 4 // 0: Volume, 1: Fullscreen, 2: Camera Shake, 3: Reset

    var sfxVolume: Float = 0.8f
        private set
    var isCameraShakeEnabled: Boolean = true
        private set

    private var statusMessage = ""
    private var statusTimer = 0f

    init {
        loadSettings()
    }

    fun loadSettings() {
        sfxVolume = prefs.getFloat("sfxVolume", 0.8f)
        isCameraShakeEnabled = prefs.getBoolean("cameraShake", true)
    }

    fun handleInput(onCloseSettings: () -> Unit, onResetProgress: () -> Unit) {
        val delta = Gdx.graphics.deltaTime
        if (statusTimer > 0f) statusTimer -= delta

        // Навигация стрелками ВВЕРХ / ВНИЗ
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W)) {
            selectedIndex = (selectedIndex - 1 + totalItems) % totalItems
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S)) {
            selectedIndex = (selectedIndex + 1) % totalItems
        }

        // Изменение значений ВЛЕВО / ВПРАВО / ПРОБЕЛ
        val left = Gdx.input.isKeyJustPressed(Input.Keys.LEFT) || Gdx.input.isKeyJustPressed(Input.Keys.A)
        val right = Gdx.input.isKeyJustPressed(Input.Keys.RIGHT) || Gdx.input.isKeyJustPressed(Input.Keys.D)
        val trigger = Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)

        when (selectedIndex) {
            0 -> { // Громкость звуков (0% .. 100%)
                if (left) {
                    sfxVolume = max(0f, sfxVolume - 0.1f)
                    prefs.putFloat("sfxVolume", sfxVolume)
                    prefs.flush()
                } else if (right || trigger) {
                    sfxVolume = if (sfxVolume >= 1f) 0f else min(1f, sfxVolume + 0.1f)
                    prefs.putFloat("sfxVolume", sfxVolume)
                    prefs.flush()
                }
            }
            1 -> { // Полноэкранный режим
                if (left || right || trigger) {
                    if (Gdx.graphics.isFullscreen) {
                        Gdx.graphics.setWindowedMode(800, 600)
                    } else {
                        Gdx.graphics.setFullscreenMode(Gdx.graphics.displayMode)
                    }
                }
            }
            2 -> { // Тряска камеры
                if (left || right || trigger) {
                    isCameraShakeEnabled = !isCameraShakeEnabled
                    prefs.putBoolean("cameraShake", isCameraShakeEnabled)
                    prefs.flush()
                }
            }
            3 -> { // Сброс прогресса
                if (trigger) {
                    prefs.clear()
                    prefs.flush()

                    val dir = Gdx.files.local("towers/")
                    if (dir.exists()) {
                        dir.deleteDirectory()
                    }

                    loadSettings()
                    onResetProgress()

                    statusMessage = "PROGRESS RESETTED!"
                    statusTimer = 2.5f
                }
            }
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.O)) {
            onCloseSettings()
        }
    }

    fun renderSettingsUI(
        delta: Float,
        spriteBatch: SpriteBatch,
        shapeRenderer: ShapeRenderer,
        font: BitmapFont,
        titleFont: BitmapFont,
        uiViewport: FitViewport
    ) {
        uiViewport.apply()

        if (smallFont == null) {
            smallFont = BitmapFont().apply {
                data.setScale(1.2f)
                region.texture.setFilter(com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest, com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest)
            }
        }
        val smFont = smallFont!!

        // Темный фон
        shapeRenderer.projectionMatrix = uiViewport.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = Color(0.08f, 0.10f, 0.14f, 0.96f)
        shapeRenderer.rect(0f, 0f, 800f, 600f)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        spriteBatch.projectionMatrix = uiViewport.camera.combined
        spriteBatch.begin()

        val centerX = 400f

        // Заголовок
        titleFont.color = Color(0.95f, 0.96f, 0.98f, 1f)
        drawCenteredText(spriteBatch, titleFont, "SETTINGS", centerX, 510f)

        // Элементы меню
        val startY = 390f
        val itemSpacing = 50f

        val volPct = (sfxVolume * 100).toInt()
        val fsStr = if (Gdx.graphics.isFullscreen) "ON" else "OFF"
        val shakeStr = if (isCameraShakeEnabled) "ON" else "OFF"

        val items = listOf(
            "SOUND VOLUME      < $volPct% >",
            "FULLSCREEN        < $fsStr >",
            "CAMERA SHAKE      < $shakeStr >",
            "[ RESET ALL PROGRESS ]"
        )

        for (i in items.indices) {
            val y = startY - i * itemSpacing
            val isSelected = (i == selectedIndex)

            if (isSelected) {
                font.color = if (i == 3) Color(1f, 0.35f, 0.35f, 1f) else Color(1f, 0.85f, 0.3f, 1f)
                drawCenteredText(spriteBatch, font, "> ${items[i]} <", centerX, y)
            } else {
                font.color = if (i == 3) Color(0.75f, 0.3f, 0.3f, 0.8f) else Color(0.7f, 0.75f, 0.82f, 0.8f)
                drawCenteredText(spriteBatch, font, "  ${items[i]}  ", centerX, y)
            }
        }

        if (statusTimer > 0f) {
            font.color = Color(1f, 0.3f, 0.3f, min(1f, statusTimer))
            drawCenteredText(spriteBatch, font, statusMessage, centerX, 140f)
        }

        // Подсказки управления
        smFont.color = Color.GRAY
        drawCenteredText(spriteBatch, smFont, "UP/DOWN - NAVIGATE   |   LEFT/RIGHT/SPACE - CHANGE", centerX, 60f)
        smFont.draw(spriteBatch, "ESC / S - BACK TO MENU", 35f, 575f)

        // Авторская подпись
        smFont.color = Color(0.5f, 0.55f, 0.65f, 0.85f)
        smFont.draw(spriteBatch, "by MogDop", 800f - 120f, 25f)

        spriteBatch.end()
    }

    private fun drawCenteredText(batch: SpriteBatch, font: BitmapFont, text: String, xCenter: Float, y: Float) {
        glyphLayout.setText(font, text)
        font.draw(batch, text, xCenter - glyphLayout.width / 2f, y)
    }
}