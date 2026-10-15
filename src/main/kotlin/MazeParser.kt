import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Texture.TextureFilter
import com.badlogic.gdx.graphics.Texture.TextureWrap
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import kotlin.math.min

enum class MazeTileType {
    WALL, FLOOR, SPAWN, GOAL
}

data class MazeWorldData(
    val models: List<Model>,
    val instances: List<ModelInstance>,
    val goalBeaconModel: Model,
    val width: Int,
    val height: Int,
    val cellSize: Float,
    val wallHeight: Float,
    val spawnPosition: Vector3,
    val goalPosition: Vector3,
    val grid: Array<Array<MazeTileType>>,
    val grassTexture: Texture,
    val bricksTexture: Texture
)

class MazeParser {
    val cellSize = 2.4f
    val wallHeight = 4.2f

    fun loadAndBuildMaze(): MazeWorldData {
        val grassTex = loadGrassTexture()
        val bricksTex = loadBricksTexture()

        val pixmap = loadMazePixmap()
        val w = pixmap.width
        val h = pixmap.height

        val grid = Array(w) { Array(h) { MazeTileType.WALL } }
        var spawnPos = Vector3(1.5f * cellSize, 1.2f, 1.5f * cellSize)
        var goalPos = Vector3((w - 2.5f) * cellSize, 1.2f, (h - 2.5f) * cellSize)

        var foundSpawn = false
        var foundGoal = false

        for (x in 0 until w) {
            for (y in 0 until h) {
                val rgba = pixmap.getPixel(x, y)
                val r = ((rgba ushr 24) and 0xFF) / 255f
                val g = ((rgba ushr 16) and 0xFF) / 255f
                val b = ((rgba ushr 8) and 0xFF) / 255f

                if (r < 0.2f && g > 0.7f && b < 0.2f) {
                    grid[x][y] = MazeTileType.SPAWN
                    spawnPos = Vector3((x + 0.5f) * cellSize, 1.3f, (y + 0.5f) * cellSize)
                    foundSpawn = true
                } else if (r > 0.7f && g < 0.2f && b < 0.2f) {
                    grid[x][y] = MazeTileType.GOAL
                    goalPos = Vector3((x + 0.5f) * cellSize, 1.3f, (y + 0.5f) * cellSize)
                    foundGoal = true
                } else if (r > 0.75f && g > 0.75f && b > 0.75f) {
                    grid[x][y] = MazeTileType.FLOOR
                } else {
                    grid[x][y] = MazeTileType.WALL
                }
            }
        }

        if (!foundSpawn) {
            for (x in 1 until w - 1) {
                for (y in 1 until h - 1) {
                    if (grid[x][y] != MazeTileType.WALL) {
                        grid[x][y] = MazeTileType.SPAWN
                        spawnPos = Vector3((x + 0.5f) * cellSize, 1.3f, (y + 0.5f) * cellSize)
                        break
                    }
                }
            }
        }

        pixmap.dispose()

        val attr = (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()

        val grassMat = Material(
            TextureAttribute.createDiffuse(grassTex),
            ColorAttribute.createDiffuse(Color(0.85f, 0.95f, 0.85f, 1f))
        )
        val bricksMat = Material(
            TextureAttribute.createDiffuse(bricksTex),
            ColorAttribute.createDiffuse(Color(0.9f, 0.9f, 0.9f, 1f))
        )
        val ceilingMat = Material(
            ColorAttribute.createDiffuse(Color(0.04f, 0.04f, 0.05f, 1f))
        )
        val goalFloorMat = Material(
            ColorAttribute.createDiffuse(Color(1f, 0.15f, 0.15f, 1f))
        )
        val spawnFloorMat = Material(
            ColorAttribute.createDiffuse(Color(0.2f, 0.9f, 0.25f, 1f))
        )

        val modelsList = mutableListOf<Model>()
        val instancesList = mutableListOf<ModelInstance>()

        // Разбиение на чанки 16x16 для предотвращения переполнения вершин (Too many vertices used)
        val chunkSize = 16
        val chunksX = (w + chunkSize - 1) / chunkSize
        val chunksY = (h + chunkSize - 1) / chunkSize

        for (cx in 0 until chunksX) {
            for (cy in 0 until chunksY) {
                val startX = cx * chunkSize
                val startY = cy * chunkSize
                val endX = min(w, startX + chunkSize)
                val endY = min(h, startY + chunkSize)

                val mb = ModelBuilder()
                mb.begin()

                val wallPart = mb.part("walls", GL20.GL_TRIANGLES, attr, bricksMat)
                val floorPart = mb.part("floor", GL20.GL_TRIANGLES, attr, grassMat)
                val ceilingPart = mb.part("ceiling", GL20.GL_TRIANGLES, attr, ceilingMat)
                val goalFloorPart = mb.part("goalFloor", GL20.GL_TRIANGLES, attr, goalFloorMat)
                val spawnFloorPart = mb.part("spawnFloor", GL20.GL_TRIANGLES, attr, spawnFloorMat)

                wallPart.setUVRange(0f, 0f, 1f, 2f)
                floorPart.setUVRange(0f, 0f, 1f, 1f)

                var hasGeometry = false

                for (x in startX until endX) {
                    for (y in startY until endY) {
                        val x1 = x * cellSize
                        val x2 = (x + 1) * cellSize
                        val z1 = y * cellSize
                        val z2 = (y + 1) * cellSize

                        when (grid[x][y]) {
                            MazeTileType.WALL -> {
                                // Отрисовываем ТОЛЬКО видимые грани стен (соприкасающиеся с проходами)
                                if (y + 1 >= h || grid[x][y + 1] != MazeTileType.WALL) {
                                    wallPart.rect(x1, 0f, z2, x2, 0f, z2, x2, wallHeight, z2, x1, wallHeight, z2, 0f, 0f, 1f)
                                    hasGeometry = true
                                }
                                if (y - 1 < 0 || grid[x][y - 1] != MazeTileType.WALL) {
                                    wallPart.rect(x2, 0f, z1, x1, 0f, z1, x1, wallHeight, z1, x2, wallHeight, z1, 0f, 0f, -1f)
                                    hasGeometry = true
                                }
                                if (x + 1 >= w || grid[x + 1][y] != MazeTileType.WALL) {
                                    wallPart.rect(x2, 0f, z2, x2, 0f, z1, x2, wallHeight, z1, x2, wallHeight, z2, 1f, 0f, 0f)
                                    hasGeometry = true
                                }
                                if (x - 1 < 0 || grid[x - 1][y] != MazeTileType.WALL) {
                                    wallPart.rect(x1, 0f, z1, x1, 0f, z2, x1, wallHeight, z2, x1, wallHeight, z1, -1f, 0f, 0f)
                                    hasGeometry = true
                                }
                            }
                            MazeTileType.GOAL -> {
                                goalFloorPart.rect(x1, 0f, z2, x2, 0f, z2, x2, 0f, z1, x1, 0f, z1, 0f, 1f, 0f)
                                ceilingPart.rect(x1, wallHeight, z1, x2, wallHeight, z1, x2, wallHeight, z2, x1, wallHeight, z2, 0f, -1f, 0f)
                                hasGeometry = true
                            }
                            MazeTileType.SPAWN -> {
                                spawnFloorPart.rect(x1, 0f, z2, x2, 0f, z2, x2, 0f, z1, x1, 0f, z1, 0f, 1f, 0f)
                                ceilingPart.rect(x1, wallHeight, z1, x2, wallHeight, z1, x2, wallHeight, z2, x1, wallHeight, z2, 0f, -1f, 0f)
                                hasGeometry = true
                            }
                            MazeTileType.FLOOR -> {
                                floorPart.rect(x1, 0f, z2, x2, 0f, z2, x2, 0f, z1, x1, 0f, z1, 0f, 1f, 0f)
                                ceilingPart.rect(x1, wallHeight, z1, x2, wallHeight, z1, x2, wallHeight, z2, x1, wallHeight, z2, 0f, -1f, 0f)
                                hasGeometry = true
                            }
                        }
                    }
                }

                val model = mb.end()
                if (hasGeometry) {
                    modelsList.add(model)
                    instancesList.add(ModelInstance(model))
                } else {
                    model.dispose()
                }
            }
        }

        // Светящийся цилиндр-маркер для финиша
        val beaconBuilder = ModelBuilder()
        val beaconMat = Material(ColorAttribute.createDiffuse(Color(1f, 0.2f, 0.2f, 1f)))
        val goalBeaconModel = beaconBuilder.createCylinder(0.45f, wallHeight * 0.8f, 0.45f, 16, beaconMat, attr)

        return MazeWorldData(
            models = modelsList,
            instances = instancesList,
            goalBeaconModel = goalBeaconModel,
            width = w,
            height = h,
            cellSize = cellSize,
            wallHeight = wallHeight,
            spawnPosition = spawnPos,
            goalPosition = goalPos,
            grid = grid,
            grassTexture = grassTex,
            bricksTexture = bricksTex
        )
    }

    private fun loadMazePixmap(): Pixmap {
        val file = sequenceOf(
            Gdx.files.local("maze.png"),
            Gdx.files.internal("maze.png"),
            Gdx.files.internal("src/main/resources/maze.png")
        ).firstOrNull { it.exists() }

        if (file != null) {
            try {
                return Pixmap(file)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return createFallbackMazePixmap()
    }

    private fun createFallbackMazePixmap(): Pixmap {
        val w = 17
        val h = 17
        val pixmap = Pixmap(w, h, Pixmap.Format.RGBA8888)
        pixmap.setColor(Color.BLACK)
        pixmap.fill()

        val layout = arrayOf(
            "11111111111111111",
            "1S000001000000001",
            "10111101011111101",
            "10100000010000101",
            "10101111110110101",
            "10001000000100001",
            "11101011110101111",
            "10001010000100001",
            "10111010111111101",
            "10000010000000101",
            "11111011111110101",
            "10001000000010101",
            "10101111111010101",
            "10100000001000101",
            "10111111101110101",
            "10000000100000GE1",
            "11111111111111111"
        )

        for (y in 0 until h) {
            val line = layout.getOrNull(y) ?: continue
            for (x in 0 until w) {
                val c = line.getOrNull(x) ?: '1'
                when (c) {
                    '1' -> pixmap.setColor(Color.BLACK)
                    '0' -> pixmap.setColor(Color.WHITE)
                    'S' -> pixmap.setColor(Color.GREEN)
                    'E', 'G' -> pixmap.setColor(Color.RED)
                }
                pixmap.drawPixel(x, y)
            }
        }
        return pixmap
    }

    private fun loadGrassTexture(): Texture {
        val file = sequenceOf(
            Gdx.files.local("grass.png"),
            Gdx.files.internal("grass.png"),
            Gdx.files.internal("src/main/resources/grass.png")
        ).firstOrNull { it.exists() }

        return if (file != null) {
            try {
                Texture(file).apply {
                    setFilter(TextureFilter.Linear, TextureFilter.Linear)
                    setWrap(TextureWrap.Repeat, TextureWrap.Repeat)
                }
            } catch (e: Exception) {
                createProceduralGrassTexture()
            }
        } else {
            createProceduralGrassTexture()
        }
    }

    private fun loadBricksTexture(): Texture {
        val file = sequenceOf(
            Gdx.files.local("bricks.png"),
            Gdx.files.internal("bricks.png"),
            Gdx.files.internal("src/main/resources/bricks.png"),
            Gdx.files.local("brick.png"),
            Gdx.files.internal("brick.png")
        ).firstOrNull { it.exists() }

        return if (file != null) {
            try {
                Texture(file).apply {
                    setFilter(TextureFilter.Linear, TextureFilter.Linear)
                    setWrap(TextureWrap.Repeat, TextureWrap.Repeat)
                }
            } catch (e: Exception) {
                createProceduralBricksTexture()
            }
        } else {
            createProceduralBricksTexture()
        }
    }

    private fun createProceduralGrassTexture(): Texture {
        val pixmap = Pixmap(128, 128, Pixmap.Format.RGBA8888)
        pixmap.setColor(Color(0.20f, 0.45f, 0.18f, 1f))
        pixmap.fill()
        pixmap.setColor(Color(0.26f, 0.55f, 0.22f, 1f))
        for (i in 0 until 140) {
            pixmap.drawPixel((Math.random() * 128).toInt(), (Math.random() * 128).toInt())
        }
        val tex = Texture(pixmap).apply {
            setWrap(TextureWrap.Repeat, TextureWrap.Repeat)
        }
        pixmap.dispose()
        return tex
    }

    private fun createProceduralBricksTexture(): Texture {
        val pixmap = Pixmap(128, 128, Pixmap.Format.RGBA8888)
        pixmap.setColor(Color(0.48f, 0.24f, 0.18f, 1f))
        pixmap.fill()

        pixmap.setColor(Color(0.28f, 0.28f, 0.30f, 1f))
        for (y in 0 until 128 step 16) {
            pixmap.drawLine(0, y, 128, y)
        }
        for (y in 0 until 128 step 16) {
            val offset = if ((y / 16) % 2 == 0) 0 else 16
            for (x in offset until 128 step 32) {
                pixmap.drawLine(x, y, x, y + 16)
            }
        }
        val tex = Texture(pixmap).apply {
            setWrap(TextureWrap.Repeat, TextureWrap.Repeat)
        }
        pixmap.dispose()
        return tex
    }
}