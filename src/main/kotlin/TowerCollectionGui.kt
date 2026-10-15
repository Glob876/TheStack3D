import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Preferences
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.FitViewport
import kotlin.math.max
import kotlin.math.min

class TowerCollectionGui {
    private val prefs: Preferences get() = Gdx.app.getPreferences("StackGamePrefs")
    private val json = Json()
    private val glyphLayout = GlyphLayout()

    private var smallFont: BitmapFont? = null

    val savedTowers = mutableListOf<SavedTowerEntry>()
    var selectedIndex = 0
    private var prevSelectedIndex = 0

    val inspectCamera = PerspectiveCamera(45f, 800f, 600f)
    private var inspectAngle = 45f
    private var inspectCamY = 3f

    private var menuAnimTimer = 0f
    private val menuAnimDuration = 0.45f
    private var isMenuOpening = false

    private var cardAnimTimer = 1f
    private val cardAnimDuration = 0.28f
    private var cardSlideDir = 0

    fun isFirstLaunch(): Boolean = !prefs.getBoolean("firstLaunchDone", false)
    fun markFirstLaunchDone() { prefs.putBoolean("firstLaunchDone", true).flush() }

    fun isSecondDialogueDone(): Boolean = prefs.getBoolean("secondDialogueDone", false)
    fun markSecondDialogueDone() { prefs.putBoolean("secondDialogueDone", true).flush() }

    fun isMiniGameEncountered(): Boolean = prefs.getBoolean("miniGameEncountered", false)
    fun markMiniGameEncountered() { prefs.putBoolean("miniGameEncountered", true).flush() }

    fun isPostMiniGameDialogueDone(): Boolean = prefs.getBoolean("postMiniGameDialogueDone", false)
    fun markPostMiniGameDialogueDone() { prefs.putBoolean("postMiniGameDialogueDone", true).flush() }

    // Отслеживание Рикролла
    fun isRickrollSeen(): Boolean = prefs.getBoolean("rickrollSeen", false)
    fun markRickrollSeen() { prefs.putBoolean("rickrollSeen", true).flush() }

    fun resetFirstLaunch() {
        prefs.putBoolean("firstLaunchDone", false)
        prefs.putBoolean("secondDialogueDone", false)
        prefs.putBoolean("miniGameEncountered", false)
        prefs.putBoolean("postMiniGameDialogueDone", false)
        prefs.putBoolean("rickrollSeen", false)
        prefs.flush()
    }

    fun loadHighScore(): Int = prefs.getInteger("highScore", 0)
    fun saveHighScore(score: Int) { prefs.putInteger("highScore", score).flush() }

    fun openMenuAnimation() {
        isMenuOpening = true
        menuAnimTimer = 0f
    }

    fun loadCollection() {
        for (entry in savedTowers) {
            entry.texture.dispose()
        }
        savedTowers.clear()

        val dir = Gdx.files.local("towers/")
        if (!dir.exists()) dir.mkdirs()

        val jsonFiles = dir.list(".json")
        jsonFiles.sortByDescending { it.name() }

        for (file in jsonFiles) {
            try {
                val data = json.fromJson(SavedTowerData::class.java, file.readString())
                val pngFile = dir.child("tower_${data.id}.png")

                if (pngFile.exists()) {
                    val texture = Texture(pngFile)
                    texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
                    savedTowers.add(SavedTowerEntry(data, texture))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        selectedIndex = 0
        prevSelectedIndex = 0
        cardAnimTimer = 1f
    }

    private fun getMaxWidth(blocks: List<BlockData>): Float {
        var maxW = 3f
        for (b in blocks) {
            if (b.width > maxW) maxW = b.width
        }
        return maxW
    }

    private fun getMaxDepth(blocks: List<BlockData>): Float {
        var maxD = 3f
        for (b in blocks) {
            if (b.depth > maxD) maxD = b.depth
        }
        return maxD
    }

    fun drawBlockInstance(modelBatch: ModelBatch, boxModel: Model, environment: Environment, b: BlockData) {
        if (b.instance == null) {
            val inst = ModelInstance(boxModel)
            inst.materials.first().set(ColorAttribute.createDiffuse(b.color))
            inst.transform.setToTranslation(b.x + b.width / 2f, b.y + b.height / 2f, b.z + b.depth / 2f)
                .scale(b.width, b.height, b.depth)
            b.instance = inst
        }
        modelBatch.render(b.instance!!, environment)
    }

    fun saveTowerToCollection(
        blocks: List<BlockData>,
        score: Int,
        modelBatch: ModelBatch,
        boxModel: Model,
        environment: Environment
    ) {
        val id = System.currentTimeMillis().toString()
        val dir = Gdx.files.local("towers/")
        if (!dir.exists()) dir.mkdirs()

        val jsonFile = dir.child("tower_$id.json")
        val pngFile = dir.child("tower_$id.png")

        val data = SavedTowerData().apply {
            this.id = id
            this.score = score
            this.blocks = ArrayList(blocks)
        }
        jsonFile.writeString(json.toJson(data), false)

        val fboW = 300
        val fboH = 400
        val fbo = FrameBuffer(Pixmap.Format.RGBA8888, fboW, fboH, true)

        val topY = blocks.lastOrNull()?.y ?: 0f
        val towerHeight = topY + 0.5f
        val maxBlockDim = max(getMaxWidth(blocks), getMaxDepth(blocks))
        val centerY = towerHeight / 2f
        val camDist = max(8f, towerHeight * 1.25f + maxBlockDim)

        val previewCam = PerspectiveCamera(40f, fboW.toFloat(), fboH.toFloat()).apply {
            position.set(camDist * 0.7f, centerY + 0.5f, camDist * 0.7f)
            lookAt(0f, centerY, 0f)
            near = 0.1f
            far = 500f
            update()
        }

        fbo.begin()
        Gdx.gl.glViewport(0, 0, fboW, fboH)
        Gdx.gl.glClearColor(0.14f, 0.15f, 0.20f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        modelBatch.begin(previewCam)
        for (b in blocks) {
            drawBlockInstance(modelBatch, boxModel, environment, b)
        }
        modelBatch.end()

        val pixmap = ScreenUtils.getFrameBufferPixmap(0, 0, fboW, fboH)
        fbo.end()
        fbo.dispose()

        val flipped = Pixmap(fboW, fboH, Pixmap.Format.RGBA8888)
        for (x in 0 until fboW) {
            for (y in 0 until fboH) {
                flipped.drawPixel(x, y, pixmap.getPixel(x, fboH - 1 - y))
            }
        }
        pixmap.dispose()
        PixmapIO.writePNG(pngFile, flipped)
        flipped.dispose()

        loadCollection()
    }

    fun handleCollectionInput(onSelectTower: () -> Unit, onCloseCollection: () -> Unit) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.A) || Gdx.input.isKeyJustPressed(Input.Keys.LEFT)) {
            if (savedTowers.size > 1) {
                prevSelectedIndex = selectedIndex
                selectedIndex = (selectedIndex - 1 + savedTowers.size) % savedTowers.size
                cardSlideDir = -1
                cardAnimTimer = 0f
            }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.D) || Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)) {
            if (savedTowers.size > 1) {
                prevSelectedIndex = selectedIndex
                selectedIndex = (selectedIndex + 1) % savedTowers.size
                cardSlideDir = 1
                cardAnimTimer = 0f
            }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.justTouched()) {
            if (savedTowers.isNotEmpty()) {
                init3DInspect()
                onSelectTower()
            }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.C)) {
            onCloseCollection()
        }
    }

    private fun init3DInspect() {
        val entry = savedTowers.getOrNull(selectedIndex) ?: return
        val blocks = entry.data.blocks
        val topY = blocks.lastOrNull()?.y ?: 0f
        inspectCamY = topY / 2f + 1f
        inspectAngle = 45f
    }

    fun update3DInspectCamera(delta: Float) {
        val entry = savedTowers.getOrNull(selectedIndex) ?: return
        val blocks = entry.data.blocks
        val topY = blocks.lastOrNull()?.y ?: 0f
        val towerHeight = topY + 0.5f
        val maxBlockDim = max(getMaxWidth(blocks), getMaxDepth(blocks))
        val centerY = towerHeight / 2f

        val camDist = max(7.5f, towerHeight * 1.15f + maxBlockDim)

        val speed = 90f * delta
        if (Gdx.input.isKeyPressed(Input.Keys.A)) inspectAngle -= speed
        if (Gdx.input.isKeyPressed(Input.Keys.D)) inspectAngle += speed
        if (Gdx.input.isKeyPressed(Input.Keys.W)) inspectCamY += 5f * delta
        if (Gdx.input.isKeyPressed(Input.Keys.S)) inspectCamY -= 5f * delta

        inspectCamY = MathUtils.clamp(inspectCamY, -3f, towerHeight + 15f)

        val rad = inspectAngle * MathUtils.degreesToRadians
        inspectCamera.position.set(camDist * MathUtils.cos(rad), inspectCamY, camDist * MathUtils.sin(rad))
        inspectCamera.lookAt(0f, centerY, 0f)
        inspectCamera.near = 0.1f
        inspectCamera.far = 500f
        inspectCamera.update()
    }

    fun renderCollectionUI(
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
                region.texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
            }
        }
        val smFont = smallFont!!

        var slideOffsetX = 0f
        if (isMenuOpening) {
            menuAnimTimer += delta
            val alpha = min(1f, menuAnimTimer / menuAnimDuration)
            slideOffsetX = MathUtils.lerp(800f, 0f, Interpolation.pow3Out.apply(alpha))
            if (alpha >= 1f) isMenuOpening = false
        }

        shapeRenderer.projectionMatrix = uiViewport.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = Color(0.10f, 0.12f, 0.16f, 0.96f)
        shapeRenderer.rect(slideOffsetX, 0f, 800f, 600f)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        spriteBatch.projectionMatrix = uiViewport.camera.combined
        spriteBatch.begin()

        val centerX = 400f + slideOffsetX

        titleFont.color = Color(0.95f, 0.96f, 0.98f, 1f)
        drawCenteredText(spriteBatch, titleFont, "TOWER COLLECTION", centerX, 550f)

        if (savedTowers.isEmpty()) {
            font.color = Color.LIGHT_GRAY
            drawCenteredText(spriteBatch, font, "NO SAVED TOWERS YET!", centerX, 320f)
            smFont.color = Color.GRAY
            drawCenteredText(spriteBatch, smFont, "Build towers to unlock your collection", centerX, 270f)
        } else {
            val cardW = 210f
            val cardH = 280f
            val cardCenterY = 285f

            if (cardAnimTimer < cardAnimDuration) {
                cardAnimTimer += delta
                val p = min(1f, cardAnimTimer / cardAnimDuration)
                val progress = Interpolation.pow2Out.apply(p)

                val prevEntry = savedTowers.getOrNull(prevSelectedIndex)
                val curEntry = savedTowers[selectedIndex]

                if (prevEntry != null) {
                    val oldX = centerX - cardSlideDir * progress * 380f
                    spriteBatch.color = Color(1f, 1f, 1f, 1f - progress)
                    spriteBatch.draw(prevEntry.texture, oldX - cardW / 2f, cardCenterY - cardH / 2f, cardW, cardH)
                }

                val newX = centerX + cardSlideDir * (1f - progress) * 380f
                spriteBatch.color = Color(1f, 1f, 1f, progress)
                spriteBatch.draw(curEntry.texture, newX - cardW / 2f, cardCenterY - cardH / 2f, cardW, cardH)

                spriteBatch.color = Color.WHITE
            } else {
                val entry = savedTowers[selectedIndex]
                spriteBatch.draw(entry.texture, centerX - cardW / 2f, cardCenterY - cardH / 2f, cardW, cardH)
            }

            titleFont.color = Color(0.8f, 0.85f, 0.95f, 1f)
            if (selectedIndex > 0) {
                titleFont.draw(spriteBatch, "<", centerX - 160f, cardCenterY + 20f)
            }
            if (selectedIndex < savedTowers.size - 1) {
                titleFont.draw(spriteBatch, ">", centerX + 130f, cardCenterY + 20f)
            }

            val curEntry = savedTowers[selectedIndex]
            font.color = Color.WHITE
            drawCenteredText(spriteBatch, font, "TOWER ${selectedIndex + 1} / ${savedTowers.size}", centerX, 115f)

            font.color = Color(0.5f, 0.9f, 0.6f, 1f)
            drawCenteredText(spriteBatch, font, "SCORE: ${curEntry.data.score}", centerX, 80f)

            smFont.color = Color.LIGHT_GRAY
            drawCenteredText(spriteBatch, smFont, "A / D  SCROLL   |   SPACE / CLICK  INSPECT 3D", centerX, 45f)
        }

        smFont.color = Color.GRAY
        smFont.draw(spriteBatch, "ESC / C - BACK TO MENU", 35f + slideOffsetX, 575f)

        spriteBatch.end()
    }

    fun renderInspectOverlay(spriteBatch: SpriteBatch, font: BitmapFont, uiViewport: FitViewport) {
        if (smallFont == null) {
            smallFont = BitmapFont().apply {
                data.setScale(1.2f)
                region.texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
            }
        }
        val smFont = smallFont!!

        uiViewport.apply()
        spriteBatch.projectionMatrix = uiViewport.camera.combined
        spriteBatch.begin()

        smFont.color = Color(0.9f, 0.9f, 0.95f, 0.85f)
        drawCenteredText(spriteBatch, smFont, "WASD - ROTATE / MOVE CAMERA   |   ESC - EXIT INSPECT", 400f, 575f)

        spriteBatch.end()
    }

    private fun drawCenteredText(batch: SpriteBatch, font: BitmapFont, text: String, xCenter: Float, y: Float) {
        glyphLayout.setText(font, text)
        font.draw(batch, text, xCenter - glyphLayout.width / 2f, y)
    }

    fun dispose() {
        for (entry in savedTowers) {
            entry.texture.dispose()
        }
        savedTowers.clear()
        smallFont?.dispose()
    }
}