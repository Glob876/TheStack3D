import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.viewport.FitViewport
import kotlin.math.max
import kotlin.math.min

class MainMenuGui {
    private val glyphLayout = GlyphLayout()
    private val titleMatrix = Matrix4()

    private var titleBeatTimer = 0f
    private var titleSwayTimer = 0f

    var isStartAnimating = false
        private set
    var startAnimTimer = 0f
        private set
    val startAnimDuration = 0.7f

    // Буфер чит-кодов (876, 1100101, 2200202, 3300303)
    private var cheatBuffer = ""
    private var cheatTimer = 0f

    fun reset() {
        isStartAnimating = false
        startAnimTimer = 0f
        cheatBuffer = ""
        cheatTimer = 0f
    }

    fun triggerStartAnimation() {
        isStartAnimating = true
        startAnimTimer = 0f
    }

    fun updateTimers(delta: Float) {
        titleBeatTimer += delta * 2.1f
        titleSwayTimer += delta * 1.6f

        if (cheatTimer > 0f) {
            cheatTimer -= delta
            if (cheatTimer <= 0f) cheatBuffer = ""
        }

        if (isStartAnimating) {
            startAnimTimer += delta
            if (startAnimTimer >= startAnimDuration) {
                isStartAnimating = false
            }
        }
    }

    private fun getPressedDigit(): Char? {
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_0) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_0)) return '0'
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_1)) return '1'
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_2)) return '2'
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_3)) return '3'
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_4) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_4)) return '4'
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_5) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_5)) return '5'
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_6) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_6)) return '6'
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_7) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_7)) return '7'
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_8) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_8)) return '8'
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_9) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_9)) return '9'
        return null
    }

    fun handleInput(
        onStartGame: () -> Unit,
        onOpenCollection: () -> Unit,
        onOpenExchange: () -> Unit,
        onOpenSettings: () -> Unit,
        onResetDialogue: () -> Unit,
        onTriggerSecretMiniGame: () -> Unit,
        onTriggerRickroll: () -> Unit,
        onTriggerMazeMiniGame: () -> Unit
    ) {
        val digit = getPressedDigit()
        if (digit != null) {
            cheatBuffer += digit
            cheatTimer = 2.0f
            if (cheatBuffer.length > 16) {
                cheatBuffer = cheatBuffer.takeLast(16)
            }

            // Чит-код на The Maze 3D
            if (cheatBuffer.endsWith("3300303")) {
                cheatBuffer = ""
                onTriggerMazeMiniGame()
                return
            }

            // Чит-коды на хоррор-мини-игру
            if (cheatBuffer.endsWith("876") || cheatBuffer.endsWith("1100101")) {
                cheatBuffer = ""
                onTriggerSecretMiniGame()
                return
            }

            // Чит-код на РИКРОЛЛ
            if (cheatBuffer.endsWith("2200202")) {
                cheatBuffer = ""
                onTriggerRickroll()
                return
            }
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            triggerStartAnimation()
            onStartGame()
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.C)) {
            onOpenCollection()
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.T)) {
            onOpenExchange()
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.S) || Gdx.input.isKeyJustPressed(Input.Keys.O)) {
            onOpenSettings()
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.FORWARD_DEL) || Gdx.input.isKeyJustPressed(Input.Keys.DEL)) {
            onResetDialogue()
        }
    }

    fun render(
        delta: Float,
        spriteBatch: SpriteBatch,
        shapeRenderer: ShapeRenderer,
        font: BitmapFont,
        titleFont: BitmapFont,
        uiViewport: FitViewport
    ) {
        updateTimers(delta)

        var textYOffset = 0f
        var borderAlpha = 1f
        var borderMargin = 0f

        if (isStartAnimating) {
            val animAlpha = min(1f, startAnimTimer / startAnimDuration)
            val flyProgress = Interpolation.pow3In.apply(animAlpha)
            textYOffset = flyProgress * 700f
            borderAlpha = max(0f, 1f - animAlpha * 1.5f)
            borderMargin = -flyProgress * 50f
        }

        uiViewport.apply()
        drawBlurredBorders(shapeRenderer, uiViewport, borderMargin, borderAlpha)

        spriteBatch.projectionMatrix = uiViewport.camera.combined
        titleMatrix.idt()
        spriteBatch.transformMatrix = titleMatrix
        spriteBatch.begin()

        val beatProg = titleBeatTimer % 1.0f
        val pulse = MathUtils.clamp(1.0f - beatProg * 2.5f, 0f, 1f)
        val pulseScale = 1.0f + (pulse * pulse) * 0.16f

        val floatY = MathUtils.sin(titleSwayTimer * 2.0f) * 6.0f
        val floatX = MathUtils.cos(titleSwayTimer * 1.0f) * 4.0f
        val swayAngle = MathUtils.sin(titleSwayTimer * 1.2f) * 2.2f

        val textCenterX = 400f + floatX
        val textCenterY = 420f + textYOffset + floatY

        titleFont.data.setScale(3.5f * pulseScale)
        glyphLayout.setText(titleFont, "STACK 3D")

        titleMatrix.idt()
            .translate(textCenterX, textCenterY, 0f)
            .rotate(0f, 0f, 1f, swayAngle)
            .translate(-textCenterX, -textCenterY, 0f)

        spriteBatch.transformMatrix = titleMatrix
        titleFont.color = Color(0.95f, 0.96f, 0.98f, borderAlpha)
        titleFont.draw(spriteBatch, "STACK 3D", textCenterX - glyphLayout.width / 2f, textCenterY + glyphLayout.height / 2f)

        titleMatrix.idt()
        spriteBatch.transformMatrix = titleMatrix
        titleFont.data.setScale(3.5f)

        font.color = Color(0.85f, 0.88f, 0.95f, borderAlpha)
        font.draw(spriteBatch, "PRESS SPACE TO START", 400f - 175f, 300f - 80f + textYOffset)

        font.color = Color(0.65f, 0.85f, 1f, borderAlpha)
        font.draw(spriteBatch, "PRESS S FOR SETTINGS", 400f - 150f, 300f - 140f + textYOffset)

        spriteBatch.end()
    }

    private fun drawBlurredBorders(
        shapeRenderer: ShapeRenderer,
        uiViewport: FitViewport,
        margin: Float,
        alpha: Float
    ) {
        if (alpha <= 0f) return

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        shapeRenderer.projectionMatrix = uiViewport.camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)

        val w = 800f
        val h = 600f
        val x0 = margin
        val y0 = margin
        val frameW = w - 2 * margin
        val frameH = h - 2 * margin

        val baseT = 24f
        val baseR = 36f
        val darkSlate = Color(0.15f, 0.17f, 0.23f, 0.85f * alpha)

        shapeRenderer.color = darkSlate
        drawFrameLayer(shapeRenderer, x0, y0, frameW, frameH, baseT, baseR)

        val blurLayers = 8
        val blurStep = 2.5f
        for (i in 1..blurLayers) {
            val layerAlpha = (1f - (i.toFloat() / (blurLayers + 1))) * 0.18f * alpha
            shapeRenderer.color = Color(0.15f, 0.17f, 0.23f, layerAlpha)

            val t = baseT + i * blurStep
            val r = max(4f, baseR - i * blurStep)
            drawFrameLayer(shapeRenderer, x0, y0, frameW, frameH, t, r)
        }

        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    private fun drawFrameLayer(
        shapeRenderer: ShapeRenderer,
        x0: Float,
        y0: Float,
        frameW: Float,
        frameH: Float,
        t: Float,
        r: Float
    ) {
        shapeRenderer.rect(x0, y0, t, frameH)
        shapeRenderer.rect(x0 + frameW - t, y0, t, frameH)
        shapeRenderer.rect(x0 + t, y0 + frameH - t, frameW - 2 * t, t)
        shapeRenderer.rect(x0 + t, y0, frameW - 2 * t, t)

        drawCornerFillet(shapeRenderer, x0 + t, y0 + frameH - t, x0 + t + r, y0 + frameH - t - r, r, 90f, 180f)
        drawCornerFillet(shapeRenderer, x0 + frameW - t, y0 + frameH - t, x0 + frameW - t - r, y0 + frameH - t - r, r, 0f, 90f)
        drawCornerFillet(shapeRenderer, x0 + t, y0 + t, x0 + t + r, y0 + t + r, r, 180f, 270f)
        drawCornerFillet(shapeRenderer, x0 + frameW - t, y0 + t, x0 + frameW - t - r, y0 + t + r, r, 270f, 360f)
    }

    private fun drawCornerFillet(
        shapeRenderer: ShapeRenderer,
        vx: Float,
        vy: Float,
        cx: Float,
        cy: Float,
        r: Float,
        startAngleDeg: Float,
        endAngleDeg: Float
    ) {
        val segments = 24
        val step = (endAngleDeg - startAngleDeg) / segments
        for (i in 0 until segments) {
            val a1 = MathUtils.degreesToRadians * (startAngleDeg + i * step)
            val a2 = MathUtils.degreesToRadians * (startAngleDeg + (i + 1) * step)

            val px1 = cx + r * MathUtils.cos(a1)
            val py1 = cy + r * MathUtils.sin(a1)

            val px2 = cx + r * MathUtils.cos(a2)
            val py2 = cy + r * MathUtils.sin(a2)

            shapeRenderer.triangle(vx, vy, px1, py1, px2, py2)
        }
    }
}