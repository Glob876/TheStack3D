import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Texture.TextureFilter
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.viewport.FitViewport
import kotlin.math.min

enum class DialogueAnimState {
    ENTRANCE, TYPING, EXIT, COMPLETED
}

data class DialogueToken(
    val text: String,
    val isShaking: Boolean,
    val isWhitespace: Boolean
)

data class DialogueReplica(
    val emotionKey: String,
    val rawText: String
) {
    val tokens: List<DialogueToken> = tokenize(rawText)
    val totalCleanLength: Int = tokens.sumOf { it.text.length }

    companion object {
        private fun tokenize(raw: String): List<DialogueToken> {
            val list = mutableListOf<DialogueToken>()
            var shaking = false
            val currentWord = StringBuilder()

            for (c in raw) {
                if (c == '_') {
                    if (currentWord.isNotEmpty()) {
                        list.add(DialogueToken(currentWord.toString(), shaking, false))
                        currentWord.clear()
                    }
                    shaking = !shaking
                } else if (c == ' ') {
                    if (currentWord.isNotEmpty()) {
                        list.add(DialogueToken(currentWord.toString(), shaking, false))
                        currentWord.clear()
                    }
                    list.add(DialogueToken(" ", shaking, true))
                } else if (c == '\n') {
                    if (currentWord.isNotEmpty()) {
                        list.add(DialogueToken(currentWord.toString(), shaking, false))
                        currentWord.clear()
                    }
                    list.add(DialogueToken("\n", shaking, true))
                } else {
                    currentWord.append(c)
                }
            }

            if (currentWord.isNotEmpty()) {
                list.add(DialogueToken(currentWord.toString(), shaking, false))
            }
            return list
        }
    }
}

class DialogueSystem {
    private val textures = mutableMapOf<String, Texture>()
    private val glyphLayout = GlyphLayout()

    private var replicas = listOf<DialogueReplica>()
    var currentReplicaIndex = 0
        private set

    var activeDialogueId = 1
        private set

    var animState = DialogueAnimState.ENTRANCE
        private set
    private var animTimer = 0f
    private val animDuration = 0.5f

    private var swayTimer = 0f
    private var typeTimer = 0f
    private val charSpeed = 28f

    val isCompleted: Boolean
        get() = animState == DialogueAnimState.COMPLETED

    val shouldShowPlatform: Boolean
        get() = activeDialogueId >= 2 || ((animState == DialogueAnimState.TYPING || animState == DialogueAnimState.EXIT) && currentReplicaIndex >= 1)

    // 1. Первый диалог (Обучение слоям)
    fun initDefaultGlobDialogue() {
        activeDialogueId = 1
        replicas = listOf(
            DialogueReplica("glob1", "Welcome to Stack 3D game!!!1!1"),
            DialogueReplica("glob2", "There are layer here. See?"),
            DialogueReplica("glob1", "Just make a puff pastry pie and everything will be awesome (I guess)"),
            DialogueReplica("glob3", "Anyway, go have fun—I've got important things to do.")
        )
        loadTextures()
        reset()
    }

    // 2. Второй диалог (Обучение кнопке C для коллекции)
    fun initSecondGlobDialogue() {
        activeDialogueId = 2
        replicas = listOf(
            DialogueReplica("glob3", "I see... You've been playing for quite a while now."),
            DialogueReplica("glob2", "Just don't forget that there's a magic button,"),
            DialogueReplica("glob1", "...which will show you your entire library of towers!"),
            DialogueReplica("glob", "This button is C.")
        )
        loadTextures()
        reset()
    }

    // 3. Третий диалог (Реакция на встречу с секретной мини-игрой)
    fun initPostMiniGameGlobDialogue() {
        activeDialogueId = 3
        replicas = listOf(
            DialogueReplica("glob3", "You just... _disappeared_ for a minute!"),
            DialogueReplica("glob5", "Your task is simply to stack the cubes. Ok?!"),
            DialogueReplica("glob", "All right, all right..."),
            DialogueReplica("glob1", "Just keep playing!")
        )
        loadTextures()
        reset()
    }

    private fun loadTextures() {
        val keys = listOf("glob", "glob1", "glob2", "glob3", "glob4", "glob5")
        for (k in keys) {
            val file = sequenceOf(
                Gdx.files.local("$k.png"),
                Gdx.files.internal("$k.png"),
                Gdx.files.internal("src/main/resources/$k.png")
            ).firstOrNull { it.exists() }

            if (file != null) {
                val tex = Texture(file)
                tex.setFilter(TextureFilter.Nearest, TextureFilter.Nearest)
                textures[k] = tex
            } else {
                textures[k] = createPlaceholderTexture(k)
            }
        }
    }

    private fun createPlaceholderTexture(label: String): Texture {
        val pixmap = Pixmap(160, 160, Pixmap.Format.RGBA8888)
        pixmap.setColor(Color(0.25f, 0.65f, 0.85f, 1f))
        pixmap.fillCircle(80, 80, 75)
        pixmap.setColor(Color.WHITE)
        pixmap.fillCircle(55, 60, 12)
        pixmap.fillCircle(105, 60, 12)
        pixmap.setColor(Color.BLACK)
        pixmap.fillCircle(55, 60, 5)
        pixmap.fillCircle(105, 60, 5)

        when (label) {
            "glob5" -> {
                pixmap.setColor(Color(0.8f, 0.2f, 0.2f, 1f))
                pixmap.fillCircle(80, 115, 20)
            }
            "glob3" -> {
                pixmap.setColor(Color.BLACK)
                pixmap.fillRectangle(50, 110, 45, 8)
            }
            else -> {
                pixmap.setColor(Color.BLACK)
                pixmap.fillRectangle(50, 110, 60, 10)
            }
        }

        val tex = Texture(pixmap)
        pixmap.dispose()
        return tex
    }

    fun reset() {
        currentReplicaIndex = 0
        animState = DialogueAnimState.ENTRANCE
        animTimer = 0f
        typeTimer = 0f
        swayTimer = 0f
    }

    fun update(delta: Float): Boolean {
        swayTimer += delta * 2.2f

        when (animState) {
            DialogueAnimState.ENTRANCE -> {
                animTimer += delta
                if (animTimer >= animDuration) {
                    animState = DialogueAnimState.TYPING
                    animTimer = 0f
                }
            }
            DialogueAnimState.TYPING -> {
                typeTimer += delta * charSpeed
            }
            DialogueAnimState.EXIT -> {
                animTimer += delta
                if (animTimer >= animDuration) {
                    animState = DialogueAnimState.COMPLETED
                    return true
                }
            }
            DialogueAnimState.COMPLETED -> return true
        }
        return false
    }

    fun handleInput(): Boolean {
        if (animState == DialogueAnimState.COMPLETED) return true

        if (animState == DialogueAnimState.ENTRANCE) {
            animState = DialogueAnimState.TYPING
            animTimer = 0f
            return false
        }

        if (animState == DialogueAnimState.EXIT) {
            return false
        }

        val currentReplica = replicas.getOrNull(currentReplicaIndex) ?: return true
        val fullLen = currentReplica.totalCleanLength

        if (typeTimer < fullLen) {
            typeTimer = fullLen.toFloat()
        } else {
            currentReplicaIndex++
            typeTimer = 0f
            if (currentReplicaIndex >= replicas.size) {
                animState = DialogueAnimState.EXIT
                animTimer = 0f
            }
        }
        return false
    }

    fun render(
        spriteBatch: SpriteBatch,
        shapeRenderer: ShapeRenderer,
        font: BitmapFont,
        titleFont: BitmapFont,
        uiViewport: FitViewport
    ) {
        if (animState == DialogueAnimState.COMPLETED) return

        uiViewport.apply()
        val delta = Gdx.graphics.deltaTime
        update(delta)

        var slideX = 0f
        var slideY = 0f

        when (animState) {
            DialogueAnimState.ENTRANCE -> {
                val p = Interpolation.pow3Out.apply(min(1f, animTimer / animDuration))
                slideX = MathUtils.lerp(-280f, 0f, p)
                slideY = MathUtils.lerp(-200f, 0f, p)
            }
            DialogueAnimState.TYPING -> {
                slideX = 0f
                slideY = 0f
            }
            DialogueAnimState.EXIT -> {
                val p = Interpolation.pow3In.apply(min(1f, animTimer / animDuration))
                slideX = MathUtils.lerp(0f, -280f, p)
                slideY = MathUtils.lerp(0f, -200f, p)
            }
            DialogueAnimState.COMPLETED -> return
        }

        val boxSwayY = MathUtils.sin(swayTimer) * 2.0f
        val globSwayY = MathUtils.sin(swayTimer * 1.3f) * 4.0f
        val globSwayRot = MathUtils.cos(swayTimer * 1.3f) * 2.0f

        val boxX = 30f + slideX
        val baseBoxY = 25f + slideY
        val finalBoxY = baseBoxY + boxSwayY
        val boxW = 740f
        val boxH = 150f

        val currentReplica = replicas.getOrNull(currentReplicaIndex) ?: replicas.last()
        val currentTexture = if (animState == DialogueAnimState.ENTRANCE) {
            textures["glob"] ?: textures["glob1"]
        } else {
            textures[currentReplica.emotionKey] ?: textures["glob"]
        }

        // 1. СПЛОШНАЯ ТЕМНАЯ ПОДЛОЖКА
        shapeRenderer.projectionMatrix = uiViewport.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = Color(0.10f, 0.12f, 0.16f, 0.96f)
        shapeRenderer.rect(boxX, finalBoxY, boxW, boxH)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        // 2. СПРАЙТ СЛЕВА, ИМЯ СПРАВА, ТЕКСТ С АВТОПЕРЕНОСОМ СТРОК
        spriteBatch.projectionMatrix = uiViewport.camera.combined
        spriteBatch.begin()

        // Персонаж Glob СЛЕВА (выровнен по вертикали)
        if (currentTexture != null) {
            val globSize = 150f
            val globX = boxX + 25f
            val globY = finalBoxY + (boxH - globSize) / 2f + globSwayY
            spriteBatch.draw(
                currentTexture,
                globX, globY,
                globSize / 2f, globSize / 2f,
                globSize, globSize,
                1f, 1f,
                globSwayRot,
                0, 0, currentTexture.width, currentTexture.height,
                false, false
            )
        }

        // Имя "Glob" СПРАВА крупным БЕЛЫМ шрифтом
        titleFont.data.setScale(2.8f)
        titleFont.color = Color.WHITE
        glyphLayout.setText(titleFont, "Glob")
        val nameX = boxX + boxW - glyphLayout.width - 40f
        val nameY = finalBoxY + boxH + 12f
        titleFont.draw(spriteBatch, "Glob", nameX, nameY)
        titleFont.data.setScale(3.5f)

        // Текст реплики С АВТОМАТИЧЕСКИМ ПЕРЕНОСОМ СТРОК (WORD WRAPPING)
        if (animState != DialogueAnimState.ENTRANCE) {
            val visibleChars = min(currentReplica.totalCleanLength, typeTimer.toInt())
            val textStartX = boxX + 190f
            val textStartY = finalBoxY + boxH - 35f
            val maxTextWidth = boxW - 220f // 520px ширина текстового поля

            renderWrappedTypewriterText(
                spriteBatch, font, currentReplica,
                textStartX, textStartY, maxTextWidth,
                visibleChars
            )
        }

        spriteBatch.end()
    }

    // Отрисовка текста с автоматическим переносом слов по строкам и поддержкой _дрожания_
    private fun renderWrappedTypewriterText(
        batch: SpriteBatch,
        font: BitmapFont,
        replica: DialogueReplica,
        startX: Float,
        startY: Float,
        maxWidth: Float,
        visibleChars: Int
    ) {
        font.color = Color(0.92f, 0.94f, 0.98f, 1f)

        var remaining = visibleChars
        var curX = startX
        var curY = startY
        val lineHeight = 32f
        val maxX = startX + maxWidth

        for (token in replica.tokens) {
            if (remaining <= 0) break

            if (token.text == "\n") {
                curX = startX
                curY -= lineHeight
                continue
            }

            // Измеряем полную ширину токена/слова
            glyphLayout.setText(font, token.text)
            val tokenWidth = glyphLayout.width

            // Если слово не помещается на текущей строке — ПЕРЕНОСИМ НА СЛЕДУЮЩУЮ СТРОКУ!
            if (!token.isWhitespace && curX + tokenWidth > maxX) {
                curX = startX
                curY -= lineHeight
            }

            // Определяем, сколько символов слова уже напечатано тайпврайтером
            val count = min(token.text.length, remaining)
            val sub = token.text.substring(0, count)
            remaining -= count

            if (token.isShaking) {
                // ДРОЖАЩЕЕ СЛОВО
                for (ch in sub) {
                    val chStr = ch.toString()
                    glyphLayout.setText(font, chStr)
                    val jitterX = (MathUtils.random() - 0.5f) * 3.8f
                    val jitterY = (MathUtils.random() - 0.5f) * 3.8f

                    font.color = Color(1f, 0.85f, 0.4f, 1f)
                    font.draw(batch, chStr, curX + jitterX, curY + jitterY)
                    curX += glyphLayout.width
                }
                font.color = Color(0.92f, 0.94f, 0.98f, 1f)
            } else {
                // ОБЫЧНЫЙ ТЕКСТ
                glyphLayout.setText(font, sub)
                font.draw(batch, sub, curX, curY)
                curX += glyphLayout.width
            }
        }
    }

    fun dispose() {
        for (tex in textures.values) {
            tex.dispose()
        }
        textures.clear()
    }
}