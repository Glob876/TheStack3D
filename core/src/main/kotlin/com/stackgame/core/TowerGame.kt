package com.stackgame.core

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Preferences
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.Align

/** Android tower edition with local records, language settings and Glob's guide. */
class TowerGame : ApplicationAdapter() {
    private enum class Screen { LANGUAGE, SETTINGS, GUIDE, MENU, PLAYING, FAILING, PAUSED, OVER, RECORDS }
    private data class Block(val x: Float, val y: Float, val z: Float, val w: Float, val d: Float, val instance: ModelInstance)
    private data class Offcut(val instance: ModelInstance, var velocity: Float = 0f, var age: Float = 0f)

    private lateinit var model: Model
    private lateinit var models: ModelBatch
    private lateinit var batch: SpriteBatch
    private lateinit var shapes: ShapeRenderer
    private lateinit var font: BitmapFont
    private lateinit var prefs: Preferences
    private lateinit var explosionTexture: Texture
    private lateinit var explosionSound: Sound
    private lateinit var explosionFrames: List<TextureRegion>
    private val portraits = mutableMapOf<String, Texture>()
    private lateinit var camera: PerspectiveCamera
    private lateinit var world: FitViewport
    private val ui = FitViewport(480f, 800f)
    private val lighting = Environment()
    private val layout = GlyphLayout()
    private val touch = Vector2()
    private val explosionPosition = Vector3()
    private val projectedExplosion = Vector3()
    private val explosionUi = Vector2()
    private val blocks = mutableListOf<Block>()
    private val offcuts = mutableListOf<Offcut>()
    private var screen = Screen.MENU
    private var score = 0
    private var best = 0
    private val records = mutableListOf<Int>()
    private var movingX = -4f
    private var movingZ = -1.5f
    private var axisX = true
    private var direction = 1f
    private var cameraY = 2f
    private var perfectTimer = 0f
    private var combo = 0
    private var runRecorded = false
    private var flashIntensity = 0f
    private var shakeTimer = 0f
    private var shakeStrength = 0f
    private var failTimer = 0f
    private var failedBlock: Block? = null
    private var explosionTime = -1f
    private var pausedScreen = Screen.PLAYING
    private var language = GameLanguage.RUSSIAN
    private var selectedLanguage = GameLanguage.RUSSIAN
    private var vibrationEnabled = true
    private var guidePage = 0
    private var guideCharacters = 0f
    private var guideSway = 0f
    private var guideReturn = Screen.MENU

    override fun create() {
        prefs = Gdx.app.getPreferences("StackGameAndroidRecords")
        language = GameLanguage.fromCode(prefs.getString("language", "ru"))
        selectedLanguage = language
        vibrationEnabled = prefs.getBoolean("vibration", true)
        best = prefs.getInteger("best", 0)
        repeat(10) { i -> prefs.getInteger("record$i", -1).takeIf { it >= 0 }?.let(records::add) }
        models = ModelBatch()
        batch = SpriteBatch()
        shapes = ShapeRenderer()
        font = BitmapFont(Gdx.files.internal("fonts/stack-sans.fnt")).apply {
            region.texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
            setUseIntegerPositions(true)
        }
        listOf("glob1", "glob2", "glob3").forEach { name ->
            portraits[name] = Texture(Gdx.files.internal("guide/$name.png")).apply {
                setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
            }
        }
        // Original desktop GIF, packed into a single small texture for Android.
        explosionTexture = Texture(Gdx.files.internal("effects/explosion.png"))
        explosionFrames = List(17) { i -> TextureRegion(explosionTexture, i % 5 * 142, i / 5 * 200, 142, 200) }
        explosionSound = Gdx.audio.newSound(Gdx.files.internal("effects/explosion.wav"))
        model = ModelBuilder().createBox(1f, 1f, 1f,
            Material(ColorAttribute.createDiffuse(Color.WHITE)),
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong())
        lighting.set(ColorAttribute.createAmbientLight(0.65f, 0.65f, 0.68f, 1f))
        lighting.add(DirectionalLight().set(0.45f, 0.45f, 0.45f, -1f, -2.5f, -1.5f))
        camera = PerspectiveCamera(35f, 480f, 800f).apply { near = 0.1f; far = 300f }
        world = FitViewport(480f, 800f, camera)
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        reset()
        screen = if (prefs.getBoolean("languageChosen", false)) Screen.MENU else Screen.LANGUAGE
    }

    private fun block(x: Float, y: Float, z: Float, w: Float, d: Float, color: Color): Block {
        val instance = ModelInstance(model)
        instance.materials.first().set(ColorAttribute.createDiffuse(color))
        instance.transform.setToTranslation(x + w / 2f, y + 0.25f, z + d / 2f).scale(w, 0.5f, d)
        return Block(x, y, z, w, d, instance)
    }

    private fun reset() {
        score = 0
        combo = 0
        perfectTimer = 0f
        runRecorded = false
        flashIntensity = 0f
        shakeTimer = 0f
        failTimer = 0f
        failedBlock = null
        explosionTime = -1f
        explosionSound.stop()
        blocks.clear()
        offcuts.clear()
        blocks.add(block(-1.5f, 0f, -1.5f, 3f, 3f, Color(0.72f, 0.75f, 0.80f, 1f)))
        axisX = false
        cameraY = 2f
        spawn()
    }

    private fun spawn() {
        axisX = !axisX
        val top = blocks.last()
        movingX = if (axisX) -4f else top.x
        movingZ = if (axisX) top.z else -4f
        direction = 1f
    }

    private fun place() {
        val top = blocks.last()
        val color = Color().fromHsv((score * 20 % 360).toFloat(), 0.38f, 0.92f)
        val p = TowerRules.place(if (axisX) top.x else top.z, if (axisX) top.w else top.d,
            if (axisX) movingX else movingZ, if (axisX) top.w else top.d)
        if (p == null) {
            failedBlock = block(movingX, top.y + 0.5f, movingZ, top.w, top.d, color)
            failTimer = 0f
            recordRun()
            screen = Screen.FAILING
            return
        }
        if (p.perfect) {
            combo++
            perfectTimer = 1.1f
            flashIntensity = (0.25f + combo * 0.15f).coerceAtMost(1f)
            shakeStrength = (0.08f + combo * 0.05f).coerceAtMost(0.6f)
            shakeTimer = 0.25f
            vibrate((30 + combo * 4).coerceAtMost(55))
        } else {
            vibrate(16)
            combo = 0
            val movingStart = if (axisX) movingX else movingZ
            val movingSize = if (axisX) top.w else top.d
            val cutStart = if (movingStart < p.start) movingStart else p.start + p.size
            val cutSize = movingSize - p.size
            if (cutSize > 0f) offcuts.add(Offcut(if (axisX)
                block(cutStart, top.y + 0.5f, movingZ, cutSize, top.d, color).instance
                else block(movingX, top.y + 0.5f, cutStart, top.w, cutSize, color).instance))
        }
        blocks.add(block(if (axisX) p.start else top.x, top.y + 0.5f,
            if (axisX) top.z else p.start, if (axisX) p.size else top.w, if (axisX) top.d else p.size, color))
        score++
        if (score > best) {
            best = score
            prefs.putInteger("best", best).flush()
        }
        spawn()
    }

    private fun recordRun() {
        if (runRecorded) return
        runRecorded = true
        records.add(score)
        records.sortDescending()
        while (records.size > 10) records.removeAt(records.lastIndex)
        records.forEachIndexed { i, value -> prefs.putInteger("record$i", value) }
        prefs.putInteger("best", best).flush()
    }

    override fun resize(width: Int, height: Int) {
        world.update(width, height, false)
        ui.update(width, height, true)
    }

    override fun render() {
        val dt = Gdx.graphics.deltaTime.coerceAtMost(0.05f)
        if (Gdx.input.isKeyJustPressed(Input.Keys.BACK) || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            screen = when (screen) {
                Screen.LANGUAGE -> Screen.LANGUAGE
                Screen.SETTINGS -> { selectedLanguage = language; Screen.MENU }
                Screen.GUIDE -> guideReturn
                Screen.PLAYING, Screen.FAILING -> { pauseRun(); Screen.PAUSED }
                Screen.PAUSED -> pausedScreen
                Screen.MENU -> { Gdx.app.exit(); Screen.MENU }
                else -> Screen.MENU
            }
        }
        if (Gdx.input.justTouched()) {
            touch.set(Gdx.input.x.toFloat(), Gdx.input.y.toFloat())
            ui.unproject(touch)
            if (touch.x in 0f..480f && touch.y in 0f..800f) handleTap(touch.x, touch.y)
        }
        if (screen == Screen.PLAYING || screen == Screen.GUIDE) {
            val speed = if (screen == Screen.GUIDE) 2f else 4f
            if (axisX) {
                movingX += speed * direction * dt
                if (movingX >= 4f || movingX <= -4f) { movingX = movingX.coerceIn(-4f, 4f); direction *= -1f }
            } else {
                movingZ += speed * direction * dt
                if (movingZ >= 4f || movingZ <= -4f) { movingZ = movingZ.coerceIn(-4f, 4f); direction *= -1f }
            }
        }
        if (screen != Screen.PAUSED) {
            if (screen == Screen.GUIDE) {
                guideCharacters += dt * 32f
                guideSway += dt
            }
            cameraY = MathUtils.lerp(cameraY, blocks.last().y + 2f, 1f - kotlin.math.exp(-4f * dt))
            perfectTimer = (perfectTimer - dt).coerceAtLeast(0f)
            flashIntensity = (flashIntensity - 3.5f * dt).coerceAtLeast(0f)
            shakeTimer = (shakeTimer - dt).coerceAtLeast(0f)
            if (explosionTime >= 0f) explosionTime += dt
            if (screen == Screen.FAILING) {
                failTimer += dt
                if (failTimer >= 0.65f && explosionTime < 0f) {
                    failedBlock?.let { failed ->
                        explosionPosition.set(failed.x + failed.w / 2f,
                            failed.y + 0.25f - 3f * 0.65f * 0.65f, failed.z + failed.d / 2f)
                    }
                    explosionTime = 0f
                    flashIntensity = 1f
                    shakeStrength = 0.5f
                    shakeTimer = 0.3f
                    explosionSound.play(0.85f)
                    vibrate(180)
                }
                if (explosionTime >= 1.7f) screen = Screen.OVER
            }
            offcuts.removeAll { cut ->
                cut.age += dt
                cut.velocity -= 12f * dt
                cut.instance.transform.translate(0f, cut.velocity * dt, 0f)
                cut.age > 2f
            }
        }
        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        Gdx.gl.glClearColor(0.08f + flashIntensity * 0.5f,
            0.10f + flashIntensity * 0.5f, 0.16f + flashIntensity * 0.5f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        world.apply()
        camera.position.set(7.5f, cameraY + 5f, 7.5f)
        camera.up.set(0f, 1f, 0f)
        camera.lookAt(0f, cameraY, 0f)
        if (shakeTimer > 0f && screen != Screen.PAUSED) {
            val strength = shakeStrength * (shakeTimer / 0.3f).coerceAtMost(1f)
            camera.position.add(MathUtils.random(-strength, strength),
                MathUtils.random(-strength, strength), MathUtils.random(-strength, strength))
        }
        camera.update()
        models.begin(camera)
        // Lower layers are outside the view once the camera follows a tall tower.
        blocks.takeLast(60).forEach { models.render(it.instance, lighting) }
        offcuts.forEach { models.render(it.instance, lighting) }
        if ((screen == Screen.FAILING || screen == Screen.PAUSED && pausedScreen == Screen.FAILING) && explosionTime < 0f) {
            failedBlock?.let {
                it.instance.transform.setToTranslation(it.x + it.w / 2f,
                    it.y + 0.25f - 3f * failTimer * failTimer, it.z + it.d / 2f).scale(it.w, 0.5f, it.d)
                models.render(it.instance, lighting)
            }
        }
        if (screen == Screen.PLAYING || screen == Screen.PAUSED && pausedScreen == Screen.PLAYING || screen == Screen.GUIDE) {
            val top = blocks.last()
            models.render(block(movingX, top.y + 0.5f, movingZ, top.w, top.d,
                Color().fromHsv((score * 20 % 360).toFloat(), 0.38f, 0.92f)).instance, lighting)
        }
        models.end()
        if (explosionTime >= 0f && explosionTime < 1.7f) {
            projectedExplosion.set(explosionPosition)
            world.project(projectedExplosion)
            explosionUi.set(projectedExplosion.x, Gdx.graphics.height - projectedExplosion.y)
            ui.unproject(explosionUi)
        }
        ui.apply()
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        shapes.projectionMatrix = ui.camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        if (screen != Screen.PLAYING && screen != Screen.FAILING) {
            shapes.color = Color(0.03f, 0.04f, 0.08f, if (screen == Screen.GUIDE) 0.6f else 0.87f)
            shapes.rect(0f, 0f, 480f, 800f)
        }
        if (screen == Screen.PLAYING) panel(382f, 706f, 80f, 62f)
        when (screen) {
            Screen.LANGUAGE, Screen.SETTINGS -> {
                languagePanel(435f, selectedLanguage == GameLanguage.RUSSIAN)
                languagePanel(335f, selectedLanguage == GameLanguage.ENGLISH)
                panel(60f, 65f, 360f, 72f)
                if (screen == Screen.SETTINGS) {
                    panel(60f, 215f, 360f, 64f)
                    panel(60f, 145f, 360f, 54f)
                }
            }
            Screen.GUIDE -> {
                shapes.color = Color(0.35f, 0.86f, 0.77f, 1f)
                shapes.rect(24f, 24f, 432f, 334f)
                shapes.color = Color(0.07f, 0.10f, 0.16f, 1f)
                shapes.rect(26f, 26f, 428f, 330f)
                panel(48f, 48f, 384f, 64f)
            }
            Screen.MENU -> { panel(60f, 265f, 360f, 72f); panel(60f, 165f, 360f, 72f); panel(60f, 65f, 360f, 72f) }
            Screen.OVER -> { panel(60f, 165f, 360f, 72f); panel(60f, 65f, 360f, 72f) }
            Screen.PAUSED -> { panel(60f, 265f, 360f, 72f); panel(60f, 165f, 360f, 72f) }
            Screen.RECORDS -> panel(60f, 65f, 360f, 72f)
            else -> {}
        }
        shapes.end()
        batch.projectionMatrix = ui.camera.combined
        batch.begin()
        if (screen == Screen.FAILING && explosionTime >= 0f && explosionTime < 1.7f) {
            val frame = explosionFrames[(explosionTime / 0.1f).toInt().coerceAtMost(16)]
            val size = (maxOf(failedBlock?.w ?: 1f, failedBlock?.d ?: 1f) * 85f).coerceIn(150f, 300f)
            batch.draw(frame, explosionUi.x - size * 0.71f / 2f, explosionUi.y - size / 2f, size * 0.71f, size)
        }
        when (screen) {
            Screen.LANGUAGE, Screen.SETTINGS -> {
                text(if (screen == Screen.LANGUAGE) "ЯЗЫК / LANGUAGE" else tr(UiText.SETTINGS), 690f, 2.3f)
                text(UiText.LANGUAGE.inLanguage(selectedLanguage), 575f, 1.6f)
                text("РУССКИЙ", 482f, 2f)
                text("ENGLISH", 382f, 2f)
                text(UiText.NEXT.inLanguage(selectedLanguage).takeIf { screen == Screen.LANGUAGE }
                    ?: UiText.SAVE.inLanguage(selectedLanguage), 111f, 2f)
                if (screen == Screen.SETTINGS) {
                    text(tr(UiText.REPLAY_GUIDE), 258f, 1.8f)
                    text(tr(if (vibrationEnabled) UiText.VIBRATION_ON else UiText.VIBRATION_OFF), 182f, 1.6f)
                }
            }
            Screen.GUIDE -> drawGuide()
            Screen.PLAYING -> {
                text("$score", 690f, 4f)
                text("${tr(UiText.BEST)}  $best", 744f, 1.4f)
                text("II", 748f, 2f, 422f)
                text(tr(UiText.TAP), 85f, 1.6f)
                if (perfectTimer > 0f) text("${tr(UiText.PERFECT)} x$combo", 605f, 1.8f, color = Color(0.45f, 1f, 0.80f, 1f))
            }
            Screen.FAILING -> {
                text("$score", 690f, 4f)
                text("${tr(UiText.BEST)}  $best", 744f, 1.4f)
            }
            Screen.MENU -> {
                text(tr(UiText.TITLE), 650f, 2.7f)
                text(tr(UiText.TAGLINE), 580f, 1.3f)
                text("${tr(UiText.BEST)}  $best", 470f, 2f)
                text(tr(UiText.PLAY), 311f, 2f); text(tr(UiText.RECORDS), 211f, 2f)
                text(tr(UiText.SETTINGS), 111f, 2f)
            }
            Screen.OVER -> {
                text(tr(UiText.OVER), 620f, 2.5f)
                text("${tr(UiText.SCORE)}  $score", 515f, 2.2f)
                text("${tr(UiText.BEST)}  $best", 460f, 1.8f)
                text(tr(UiText.AGAIN), 211f, 2f); text(tr(UiText.RECORDS), 111f, 2f)
            }
            Screen.PAUSED -> {
                text(tr(UiText.PAUSED), 590f, 2.8f)
                text("${tr(UiText.SCORE)}  $score", 500f, 2f)
                text(tr(UiText.CONTINUE), 311f, 2f); text(tr(UiText.END), 211f, 2f)
            }
            Screen.RECORDS -> {
                text(tr(UiText.LOCAL_RECORDS), 730f, 2f)
                text("${tr(UiText.BEST)}  $best", 670f, 1.6f)
                if (records.isEmpty()) text(tr(UiText.NO_RECORDS), 510f, 1.5f)
                records.forEachIndexed { i, value -> text("${i + 1}.     $value", 602f - i * 42f, 1.8f) }
                text(tr(UiText.BACK), 111f, 2f)
            }
        }
        batch.end()
    }

    private fun handleTap(x: Float, y: Float) {
        val inButton = x in 60f..420f
        when (screen) {
            Screen.LANGUAGE, Screen.SETTINGS -> if (inButton) {
                when (y) {
                    in 435f..507f -> selectedLanguage = GameLanguage.RUSSIAN
                    in 335f..407f -> selectedLanguage = GameLanguage.ENGLISH
                    in 65f..137f -> {
                        val firstLaunch = screen == Screen.LANGUAGE
                        language = selectedLanguage
                        prefs.putString("language", language.code).putBoolean("languageChosen", true).flush()
                        if (firstLaunch && !prefs.getBoolean("guideCompleted", false)) startGuide(Screen.MENU)
                        else screen = Screen.MENU
                    }
                    in 215f..279f -> if (screen == Screen.SETTINGS) {
                        // A pending language choice is kept until SAVE is pressed.
                        startGuide(Screen.SETTINGS)
                    }
                    in 145f..199f -> if (screen == Screen.SETTINGS) {
                        vibrationEnabled = !vibrationEnabled
                        prefs.putBoolean("vibration", vibrationEnabled).flush()
                        if (vibrationEnabled) vibrate(25)
                    }
                }
            }
            Screen.GUIDE -> {
                if (x in 290f..456f && y in 700f..768f) finishGuide()
                else if (x in 24f..456f && y in 24f..358f) {
                    val length = GlobGuide.pages[guidePage].inLanguage(language).length
                    if (guideCharacters < length) guideCharacters = length.toFloat()
                    else if (guidePage < GlobGuide.pages.lastIndex) { guidePage++; guideCharacters = 0f }
                    else finishGuide()
                }
            }
            Screen.PLAYING -> if (x >= 382f && y in 706f..768f) pauseRun() else place()
            Screen.FAILING -> {}
            Screen.MENU -> if (inButton) {
                if (y in 265f..337f) {
                    if (prefs.getBoolean("guideCompleted", false)) { reset(); screen = Screen.PLAYING }
                    else startGuide(Screen.MENU)
                } else if (y in 165f..237f) screen = Screen.RECORDS
                else if (y in 65f..137f) { selectedLanguage = language; screen = Screen.SETTINGS }
            }
            Screen.OVER -> if (inButton) {
                if (y in 165f..237f) { reset(); screen = Screen.PLAYING }
                else if (y in 65f..137f) screen = Screen.RECORDS
            }
            Screen.PAUSED -> if (inButton) {
                if (y in 265f..337f) screen = pausedScreen
                else if (y in 165f..237f) { recordRun(); explosionSound.stop(); screen = Screen.OVER }
            }
            Screen.RECORDS -> if (inButton && y in 65f..137f) screen = Screen.MENU
        }
    }

    private fun tr(key: UiText) = key.inLanguage(language)

    private fun vibrate(milliseconds: Int) {
        if (vibrationEnabled && Gdx.input.isPeripheralAvailable(Input.Peripheral.Vibrator)) {
            Gdx.input.vibrate(milliseconds)
        }
    }

    private fun startGuide(returnTo: Screen) {
        reset()
        guidePage = 0
        guideCharacters = 0f
        guideSway = 0f
        guideReturn = returnTo
        screen = Screen.GUIDE
    }

    private fun finishGuide() {
        prefs.putBoolean("guideCompleted", true).flush()
        if (guideReturn == Screen.SETTINGS) screen = Screen.SETTINGS
        else { reset(); screen = Screen.PLAYING }
    }

    private fun languagePanel(y: Float, selected: Boolean) {
        if (selected) {
            shapes.color = Color(0.35f, 0.86f, 0.77f, 1f)
            shapes.rect(56f, y - 4f, 368f, 80f)
        }
        panel(60f, y, 360f, 72f)
        if (selected) {
            shapes.color = Color(0.35f, 0.86f, 0.77f, 1f)
            shapes.rect(76f, y + 28f, 16f, 16f)
        }
    }

    private fun drawGuide() {
        text(tr(UiText.GUIDE), 744f, 1.8f, 150f)
        text(tr(UiText.SKIP), 740f, 1.1f, 378f)
        val page = GlobGuide.pages[guidePage]
        val portrait = portraits.getValue(page.portrait)
        batch.draw(portrait, 48f, 382f + MathUtils.sin(guideSway * 2f) * 4f, 144f, 144f)
        text(tr(UiText.GLOB), 328f, 2f, 110f, Color(0.45f, 1f, 0.80f, 1f))
        text("${guidePage + 1} / ${GlobGuide.pages.size}", 324f, 1.3f, 390f)
        val fullText = page.inLanguage(language)
        // Wrap the complete replica first so words stay still while letters appear.
        font.data.setScale(1.65f)
        val wrapped = wrapGuide(fullText, 384f)
        val visible = wrapped.take(guideCharacters.toInt().coerceAtMost(wrapped.length))
        font.color = Color(0.92f, 0.95f, 1f, 1f)
        font.draw(batch, visible, 48f, 278f, 384f, Align.left, false)
        val action = when {
            guideCharacters < fullText.length -> UiText.REVEAL
            guidePage == GlobGuide.pages.lastIndex && guideReturn != Screen.SETTINGS -> UiText.PLAY
            guidePage == GlobGuide.pages.lastIndex -> UiText.BACK
            else -> UiText.NEXT
        }
        text(tr(action), 91f, 1.7f)
    }

    private fun wrapGuide(value: String, width: Float): String {
        val result = StringBuilder()
        var line = ""
        value.split(' ').forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            layout.setText(font, candidate)
            if (layout.width > width && line.isNotEmpty()) {
                result.append(line).append('\n')
                line = word
            } else line = candidate
        }
        return result.append(line).toString()
    }

    private fun panel(x: Float, y: Float, w: Float, h: Float) {
        shapes.color = Color(0.18f, 0.28f, 0.43f, 1f)
        shapes.rect(x, y, w, h)
    }

    private fun text(value: String, y: Float, scale: Float, x: Float = 240f, color: Color = Color.WHITE) {
        font.data.setScale(scale)
        font.color = color
        layout.setText(font, value)
        font.draw(batch, layout, x - layout.width / 2f, y)
    }

    private fun pauseRun() {
        pausedScreen = screen
        screen = Screen.PAUSED
        explosionSound.stop()
    }

    override fun pause() {
        if (screen == Screen.PLAYING || screen == Screen.FAILING) pauseRun()
        prefs.flush()
    }

    override fun dispose() {
        prefs.flush()
        model.dispose()
        models.dispose()
        batch.dispose()
        shapes.dispose()
        font.dispose()
        explosionTexture.dispose()
        explosionSound.dispose()
        portraits.values.forEach(Texture::dispose)
    }
}
