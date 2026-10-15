import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Preferences
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.viewport.FitViewport
import kotlin.math.max
import kotlin.math.min

data class StockAsset(
    val symbol: String,
    val name: String,
    var currentPrice: Double,
    val history: MutableList<Double> = mutableListOf(),
    var ownedCount: Double = 0.0,
    val volatility: Double,
    var trend: Int = 0
)

class StockExchangeGui {
    private val prefs: Preferences get() = Gdx.app.getPreferences("StackGamePrefs")
    private val glyphLayout = GlyphLayout()

    private var smallFont: BitmapFont? = null

    var totalScore: Int = 0
        private set
    var araBalance: Double = 0.0
        private set

    val assets = mutableListOf<StockAsset>()

    // Вкладки: 0 = HOME, 1 = ARA, 2 = Batcoin, 3 = OPPL, 4 = OVDA, 5 = GOGL
    var currentTab = 0

    // Выбранная кнопка на страницах валют (0..5)
    var selectedButtonIndex = 0

    // Плавное пролистывание верхних вкладок
    private var tabScrollXTarget = 0f
    private var tabScrollXCurrent = 0f

    // Плавный скроллинг на Главной странице (HOME)
    private var homeScrollYTarget = 0f
    private var homeScrollYCurrent = 0f
    private val maxHomeScrollY = 480f

    private var priceUpdateTimer = 0f

    init {
        assets.add(StockAsset("Batcoin", "Batcoin Crypto", 50.0, volatility = 6.5))
        assets.add(StockAsset("OPPL", "OPPL Tech Corp", 22.0, volatility = 2.5))
        assets.add(StockAsset("OVDA", "OVDA Industry", 9.5, volatility = 1.2))
        assets.add(StockAsset("GOGL", "GOGL Cloud Inc", 34.0, volatility = 3.0))

        loadMarketData()
    }

    fun loadMarketData() {
        totalScore = prefs.getInteger("totalScore", 0)
        araBalance = prefs.getFloat("araBalance", 0.0f).toDouble()

        for (asset in assets) {
            asset.currentPrice = prefs.getFloat("price_${asset.symbol}", asset.currentPrice.toFloat()).toDouble()
            asset.ownedCount = prefs.getFloat("owned_${asset.symbol}", 0.0f).toDouble()

            val historyRaw = prefs.getString("history_${asset.symbol}", "")
            asset.history.clear()
            if (historyRaw.isNotEmpty()) {
                val parts = historyRaw.split(",")
                for (p in parts) {
                    p.toDoubleOrNull()?.let { asset.history.add(it) }
                }
            }
            if (asset.history.isEmpty()) {
                asset.history.add(asset.currentPrice)
            }
        }
    }

    fun saveMarketData() {
        prefs.putInteger("totalScore", totalScore)
        prefs.putFloat("araBalance", araBalance.toFloat())

        for (asset in assets) {
            prefs.putFloat("price_${asset.symbol}", asset.currentPrice.toFloat())
            prefs.putFloat("owned_${asset.symbol}", asset.ownedCount.toFloat())

            val historyStr = asset.history.joinToString(",") { String.format("%.2f", it).replace(",", ".") }
            prefs.putString("history_${asset.symbol}", historyStr)
        }
        prefs.flush()
    }

    fun addAccumulatedScore(score: Int) {
        totalScore += score
        saveMarketData()
    }

    fun updateMarketPrices(delta: Float) {
        priceUpdateTimer += delta
        if (priceUpdateTimer >= 2.5f) {
            priceUpdateTimer = 0f

            for (asset in assets) {
                val factor = (Math.random() * 2.0 - 0.98) * (asset.volatility / 100.0)
                val oldPrice = asset.currentPrice
                var newPrice = max(0.1, asset.currentPrice * (1.0 + factor))
                newPrice = Math.round(newPrice * 100.0) / 100.0

                asset.trend = if (newPrice > oldPrice) 1 else if (newPrice < oldPrice) -1 else 0
                asset.currentPrice = newPrice

                asset.history.add(newPrice)
                if (asset.history.size > 22) {
                    asset.history.removeAt(0)
                }
            }
            saveMarketData()
        }
    }

    fun handleInput(onClose: () -> Unit) {
        val totalTabs = 2 + assets.size // 0: HOME, 1: ARA, 2..5: ASSETS

        // Переключение ВKЛАДОК по стрелочкам ВЛЕВО / ВПРАВО
        if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT) || Gdx.input.isKeyJustPressed(Input.Keys.A)) {
            currentTab = (currentTab - 1 + totalTabs) % totalTabs
            selectedButtonIndex = 0
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT) || Gdx.input.isKeyJustPressed(Input.Keys.D)) {
            currentTab = (currentTab + 1) % totalTabs
            selectedButtonIndex = 0
        }

        if (currentTab == 0) {
            // ГЛАВНАЯ (HOME): Скроллинг вверх/вниз
            val scrollSpeed = 40f
            if (Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.S)) {
                homeScrollYTarget = min(maxHomeScrollY, homeScrollYTarget + scrollSpeed)
            }
            if (Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.W)) {
                homeScrollYTarget = max(0f, homeScrollYTarget - scrollSpeed)
            }
        } else if (currentTab == 1) {
            // ВКЛАДКА ARA: Управление кнопками конвертации Score
            val numAraButtons = 3
            if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W)) {
                selectedButtonIndex = (selectedButtonIndex - 1 + numAraButtons) % numAraButtons
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S)) {
                selectedButtonIndex = (selectedButtonIndex + 1) % numAraButtons
            }

            val trigger = Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
            if (trigger) {
                when (selectedButtonIndex) {
                    0 -> { // Convert 10 Score
                        if (totalScore >= 10) {
                            totalScore -= 10
                            araBalance += 1.0
                            saveMarketData()
                        }
                    }
                    1 -> { // Convert 100 Score
                        if (totalScore >= 100) {
                            totalScore -= 100
                            araBalance += 10.0
                            saveMarketData()
                        }
                    }
                    2 -> { // Convert ALL Score
                        if (totalScore >= 10) {
                            val convertAmount = (totalScore / 10) * 10
                            araBalance += convertAmount / 10.0
                            totalScore -= convertAmount
                            saveMarketData()
                        }
                    }
                }
            }
        } else {
            // ВКЛАДКИ АКТИВОВ: Переключение и вызов кнопок (Buy/Sell)
            val numButtons = 4
            if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W)) {
                selectedButtonIndex = (selectedButtonIndex - 1 + numButtons) % numButtons
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S)) {
                selectedButtonIndex = (selectedButtonIndex + 1) % numButtons
            }

            val trigger = Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
            val asset = assets.getOrNull(currentTab - 2)

            if (trigger && asset != null) {
                when (selectedButtonIndex) {
                    0 -> { // Buy 1
                        if (araBalance >= asset.currentPrice) {
                            araBalance -= asset.currentPrice
                            asset.ownedCount += 1.0
                            saveMarketData()
                        }
                    }
                    1 -> { // Buy MAX
                        val maxToBuy = (araBalance / asset.currentPrice).toInt()
                        if (maxToBuy > 0) {
                            araBalance -= maxToBuy * asset.currentPrice
                            asset.ownedCount += maxToBuy.toDouble()
                            saveMarketData()
                        }
                    }
                    2 -> { // Sell 1
                        if (asset.ownedCount >= 1.0) {
                            araBalance += asset.currentPrice
                            asset.ownedCount -= 1.0
                            saveMarketData()
                        }
                    }
                    3 -> { // Sell ALL
                        if (asset.ownedCount > 0.0) {
                            araBalance += asset.ownedCount * asset.currentPrice
                            asset.ownedCount = 0.0
                            saveMarketData()
                        }
                    }
                }
            }
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.T)) {
            onClose()
        }
    }

    fun renderMarketUI(
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

        homeScrollYCurrent += (homeScrollYTarget - homeScrollYCurrent) * 0.12f

        // Тёмный фон
        shapeRenderer.projectionMatrix = uiViewport.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = Color(0.08f, 0.10f, 0.14f, 0.96f)
        shapeRenderer.rect(0f, 0f, 800f, 600f)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        // 1. Отрисовка Прокручиваемой Панели Вкладок
        renderTabBar(delta, spriteBatch, shapeRenderer, font, smFont, uiViewport)

        // 2. Отрисовка страниц
        when (currentTab) {
            0 -> renderHomePage(delta, spriteBatch, shapeRenderer, font, titleFont, smFont, uiViewport)
            1 -> renderAraTab(delta, spriteBatch, shapeRenderer, font, titleFont, smFont, uiViewport)
            else -> {
                val asset = assets.getOrNull(currentTab - 2)
                if (asset != null) {
                    renderAssetPage(delta, asset, spriteBatch, shapeRenderer, font, titleFont, smFont, uiViewport)
                }
            }
        }
    }

    private fun renderTabBar(
        delta: Float,
        spriteBatch: SpriteBatch,
        shapeRenderer: ShapeRenderer,
        font: BitmapFont,
        smFont: BitmapFont,
        uiViewport: FitViewport
    ) {
        val tabs = listOf("HOME", "ARA", "BATCOIN", "OPPL", "OVDA", "GOGL")
        val tabW = 115f
        val itemW = tabW + 10f
        val tabY = 535f

        // Анимация плавной прокрутки полосы вкладок
        val activeCenterX = currentTab * itemW + tabW / 2f
        val targetTabScrollX = 400f - activeCenterX
        tabScrollXCurrent += (targetTabScrollX - tabScrollXCurrent) * 0.15f * (delta * 60f)

        shapeRenderer.projectionMatrix = uiViewport.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        for (i in tabs.indices) {
            val x = tabScrollXCurrent + i * itemW
            val isSelected = (i == currentTab)
            shapeRenderer.color = if (isSelected) Color(0.20f, 0.45f, 0.75f, 0.95f) else Color(0.15f, 0.18f, 0.24f, 0.8f)
            shapeRenderer.rect(x, tabY, tabW, 35f)
        }
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        spriteBatch.projectionMatrix = uiViewport.camera.combined
        spriteBatch.begin()

        for (i in tabs.indices) {
            val x = tabScrollXCurrent + i * itemW + tabW / 2f
            val isSelected = (i == currentTab)
            smFont.color = if (isSelected) Color.WHITE else Color.LIGHT_GRAY
            drawCenteredText(spriteBatch, smFont, tabs[i], x, tabY + 23f)
        }

        smFont.color = Color.GRAY
        smFont.draw(spriteBatch, "< A / D OR LEFT / RIGHT TO SWITCH TABS >", 400f - 145f, 588f)

        spriteBatch.end()
    }

    private fun renderHomePage(
        delta: Float,
        spriteBatch: SpriteBatch,
        shapeRenderer: ShapeRenderer,
        font: BitmapFont,
        titleFont: BitmapFont,
        smFont: BitmapFont,
        uiViewport: FitViewport
    ) {
        val scrollY = homeScrollYCurrent

        var portfolioValue = 0.0
        for (a in assets) {
            portfolioValue += a.ownedCount * a.currentPrice
        }
        val netWorth = araBalance + portfolioValue

        spriteBatch.projectionMatrix = uiViewport.camera.combined
        spriteBatch.begin()

        var y = 490f + scrollY

        // Чистые заголовки без декоративных знаков ==
        titleFont.color = Color(0.95f, 0.96f, 0.98f, 1f)
        drawCenteredText(spriteBatch, titleFont, "DASHBOARD OVERVIEW", 400f, y)
        y -= 45f

        val araStr = String.format("%.2f", araBalance)
        val worthStr = String.format("%.2f", netWorth)
        val portStr = String.format("%.2f", portfolioValue)

        font.color = Color(1f, 0.85f, 0.3f, 1f)
        drawCenteredText(spriteBatch, font, "TOTAL NET WORTH: $worthStr ARA", 400f, y)
        y -= 35f

        smFont.color = Color.WHITE
        drawCenteredText(spriteBatch, smFont, "FREE BALANCE: $araStr ARA   |   INVESTMENTS VALUE: $portStr ARA", 400f, y)
        y -= 25f
        smFont.color = Color(0.5f, 0.9f, 0.6f, 1f)
        drawCenteredText(spriteBatch, smFont, "ACCUMULATED SCORE: $totalScore  (Potential: ${totalScore / 10} Ara)", 400f, y)
        y -= 45f

        // Раздел портфеля
        font.color = Color(0.7f, 0.85f, 1f, 1f)
        drawCenteredText(spriteBatch, font, "PORTFOLIO BREAKDOWN", 400f, y)
        y -= 30f

        for (a in assets) {
            val totalAssetVal = a.ownedCount * a.currentPrice
            val priceStr = String.format("%.2f", a.currentPrice)
            val valStr = String.format("%.2f", totalAssetVal)
            val ownedStr = String.format("%.1f", a.ownedCount)
            val trendStr = if (a.trend > 0) "▲" else if (a.trend < 0) "▼" else "-"

            smFont.color = if (a.ownedCount > 0) Color.WHITE else Color.GRAY
            val line = "${a.symbol.padEnd(8)}: Owned ${ownedStr.padStart(5)} | Price ${priceStr.padStart(6)} Ara | Total $valStr Ara  $trendStr"
            drawCenteredText(spriteBatch, smFont, line, 400f, y)
            y -= 25f
        }

        y -= 20f
        font.color = Color(0.7f, 0.85f, 1f, 1f)
        drawCenteredText(spriteBatch, font, "MARKET ANALYTICS & STATS", 400f, y)
        y -= 30f

        val topAsset = assets.maxByOrNull { it.currentPrice }?.symbol ?: "None"
        val lowestAsset = assets.minByOrNull { it.currentPrice }?.symbol ?: "None"
        val bullCount = assets.count { it.trend > 0 }
        val bearCount = assets.count { it.trend < 0 }

        smFont.color = Color.LIGHT_GRAY
        drawCenteredText(spriteBatch, smFont, "Market Leader: $topAsset   |   Lowest Value: $lowestAsset", 400f, y)
        y -= 25f
        drawCenteredText(spriteBatch, smFont, "Market Sentiment: $bullCount Bullish (▲) / $bearCount Bearish (▼)", 400f, y)
        y -= 25f
        drawCenteredText(spriteBatch, smFont, "Currency Ara Rate: Fixed 10 Score = 1 Ara (Inflation 0.0%)", 400f, y)

        y -= 30f
        font.color = Color(0.7f, 0.85f, 1f, 1f)
        drawCenteredText(spriteBatch, font, "LIVE MARKET NEWS", 400f, y)
        y -= 30f

        smFont.color = Color.GRAY
        drawCenteredText(spriteBatch, smFont, "• Batcoin crypto market continues real-time fluctuation.", 400f, y)
        y -= 22f
        drawCenteredText(spriteBatch, smFont, "• OPPL and GOGL tech shares report steady investor volume.", 400f, y)
        y -= 22f
        drawCenteredText(spriteBatch, smFont, "• OVDA Industrials maintains low volatility profile.", 400f, y)

        smFont.color = Color.GRAY
        drawCenteredText(spriteBatch, smFont, "^ W / S OR UP / DOWN TO SCROLL DASHBOARD ^", 400f, 40f)
        smFont.draw(spriteBatch, "by MogDop", 800f - 120f, 20f)

        spriteBatch.end()
    }

    // Вкладка АРЫ (ARA CURRENCY CENTER)
    private fun renderAraTab(
        delta: Float,
        spriteBatch: SpriteBatch,
        shapeRenderer: ShapeRenderer,
        font: BitmapFont,
        titleFont: BitmapFont,
        smFont: BitmapFont,
        uiViewport: FitViewport
    ) {
        spriteBatch.projectionMatrix = uiViewport.camera.combined
        spriteBatch.begin()

        val centerX = 400f
        val araStr = String.format("%.2f", araBalance)

        titleFont.color = Color(0.95f, 0.96f, 0.98f, 1f)
        drawCenteredText(spriteBatch, titleFont, "ARA CURRENCY CENTER", centerX, 495f)

        font.color = Color(1f, 0.85f, 0.3f, 1f)
        drawCenteredText(spriteBatch, font, "CURRENT BALANCE: $araStr ARA", centerX, 450f)

        smFont.color = Color.LIGHT_GRAY
        drawCenteredText(spriteBatch, smFont, "UNCONVERTED SCORE: $totalScore   |   EXCHANGE RATE: 10 SCORE = 1 ARA", centerX, 415f)

        smFont.color = Color.GRAY
        drawCenteredText(spriteBatch, smFont, "Ara is the official stable reserve currency. Convert score anytime below:", centerX, 375f)

        // Кнопки обмена
        val startY = 300f
        val spacing = 45f

        val buttons = listOf(
            "[ CONVERT 10 SCORE -> 1 ARA ]",
            "[ CONVERT 100 SCORE -> 10 ARA ]",
            "[ CONVERT ALL SCORE TO ARA ]"
        )

        for (i in buttons.indices) {
            val y = startY - i * spacing
            val isSelected = (i == selectedButtonIndex)

            if (isSelected) {
                font.color = Color(1f, 0.85f, 0.3f, 1f)
                drawCenteredText(spriteBatch, font, "> ${buttons[i]} <", centerX, y)
            } else {
                font.color = Color(0.75f, 0.80f, 0.88f, 0.75f)
                drawCenteredText(spriteBatch, font, "  ${buttons[i]}  ", centerX, y)
            }
        }

        smFont.color = Color.GRAY
        drawCenteredText(spriteBatch, smFont, "W/S - SELECT BUTTON   |   SPACE / ENTER - EXECUTE CONVERSION", centerX, 35f)

        spriteBatch.end()
    }

    private fun renderAssetPage(
        delta: Float,
        asset: StockAsset,
        spriteBatch: SpriteBatch,
        shapeRenderer: ShapeRenderer,
        font: BitmapFont,
        titleFont: BitmapFont,
        smFont: BitmapFont,
        uiViewport: FitViewport
    ) {
        renderPriceChart(asset, shapeRenderer, uiViewport)

        spriteBatch.projectionMatrix = uiViewport.camera.combined
        spriteBatch.begin()

        val centerX = 400f
        val priceStr = String.format("%.2f", asset.currentPrice)
        val ownedStr = String.format("%.1f", asset.ownedCount)
        val trendStr = if (asset.trend > 0) "▲ RISING" else if (asset.trend < 0) "▼ FALLING" else "- STABLE"

        titleFont.color = Color(0.95f, 0.96f, 0.98f, 1f)
        drawCenteredText(spriteBatch, titleFont, "${asset.symbol} - ${asset.name}", centerX, 495f)

        font.color = if (asset.trend > 0) Color(0.4f, 0.9f, 0.5f, 1f) else if (asset.trend < 0) Color(0.9f, 0.4f, 0.4f, 1f) else Color.WHITE
        drawCenteredText(spriteBatch, font, "PRICE: $priceStr ARA   ($trendStr)", centerX, 455f)

        smFont.color = Color.LIGHT_GRAY
        val valStr = String.format("%.2f", asset.ownedCount * asset.currentPrice)
        drawCenteredText(spriteBatch, smFont, "YOU OWN: $ownedStr shares (Value: $valStr Ara)  |  BALANCE: ${String.format("%.2f", araBalance)} ARA", centerX, 425f)

        val startY = 220f
        val spacing = 32f

        val btnTexts = listOf(
            "[ BUY 1 SHARE (${priceStr} Ara) ]",
            "[ BUY MAX SHARES ]",
            "[ SELL 1 SHARE (${priceStr} Ara) ]",
            "[ SELL ALL SHARES ]"
        )

        for (i in btnTexts.indices) {
            val y = startY - i * spacing
            val isSelected = (i == selectedButtonIndex)

            if (isSelected) {
                font.color = Color(1f, 0.85f, 0.3f, 1f)
                drawCenteredText(spriteBatch, font, "> ${btnTexts[i]} <", centerX, y)
            } else {
                font.color = Color(0.75f, 0.80f, 0.88f, 0.75f)
                drawCenteredText(spriteBatch, font, "  ${btnTexts[i]}  ", centerX, y)
            }
        }

        smFont.color = Color.GRAY
        drawCenteredText(spriteBatch, smFont, "W/S - SELECT BUTTON   |   SPACE / ENTER - EXECUTE   |   A/D - SWITCH TABS", centerX, 25f)

        spriteBatch.end()
    }

    private fun renderPriceChart(asset: StockAsset, shapeRenderer: ShapeRenderer, uiViewport: FitViewport) {
        val history = asset.history
        if (history.size < 2) return

        val chartX = 150f
        val chartY = 250f
        val chartW = 500f
        val chartH = 140f

        shapeRenderer.projectionMatrix = uiViewport.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = Color(0.12f, 0.14f, 0.18f, 0.9f)
        shapeRenderer.rect(chartX, chartY, chartW, chartH)
        shapeRenderer.end()

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)

        val minPrice = max(0.01, history.minOrNull() ?: 1.0)
        val maxPrice = max(minPrice + 0.5, history.maxOrNull() ?: 10.0)
        val priceRange = maxPrice - minPrice

        val stepX = chartW / (history.size - 1)

        for (i in 0 until history.size - 1) {
            val p1 = history[i]
            val p2 = history[i + 1]

            val y1 = chartY + 15f + ((p1 - minPrice) / priceRange * (chartH - 30f)).toFloat()
            val y2 = chartY + 15f + ((p2 - minPrice) / priceRange * (chartH - 30f)).toFloat()

            val x1 = chartX + i * stepX
            val x2 = chartX + (i + 1) * stepX

            shapeRenderer.color = if (p2 >= p1) Color(0.4f, 0.9f, 0.5f, 1f) else Color(0.9f, 0.4f, 0.4f, 1f)
            shapeRenderer.line(x1, y1, x2, y2)
        }

        shapeRenderer.color = Color(0.3f, 0.35f, 0.45f, 0.8f)
        shapeRenderer.rect(chartX, chartY, chartW, chartH)

        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    private fun drawCenteredText(batch: SpriteBatch, font: BitmapFont, text: String, xCenter: Float, y: Float) {
        glyphLayout.setText(font, text)
        font.draw(batch, text, xCenter - glyphLayout.width / 2f, y)
    }
}