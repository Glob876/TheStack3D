import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.environment.PointLight
import com.badlogic.gdx.graphics.g3d.environment.SpotLight
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3

class FirstPersonEngine {
    lateinit var camera: PerspectiveCamera
        private set

    val position = Vector3()
    val direction = Vector3(0f, 0f, -1f)
    val up = Vector3(0f, 1f, 0f)

    var yaw = -90f
    var pitch = 0f

    val playerRadius = 0.38f
    val moveSpeed = 4.6f
    val mouseSensitivity = 0.22f

    private var walkTimer = 0f
    private val eyeHeight = 1.6f

    // 3D Фонарик игрока
    val spotLight = SpotLight()
    val ambientPointLight = PointLight()

    fun init(spawnPos: Vector3) {
        camera = PerspectiveCamera(68f, 800f, 600f)
        position.set(spawnPos.x, eyeHeight, spawnPos.z)
        yaw = -90f
        pitch = 0f
        walkTimer = 0f

        camera.near = 0.05f
        camera.far = 80f
        updateCameraDirection()

        spotLight.set(
            Color(1f, 0.96f, 0.88f, 1f),
            position,
            direction,
            65f,
            34f,
            8f
        )
        ambientPointLight.set(
            Color(0.25f, 0.28f, 0.32f, 1f),
            position,
            6.5f
        )
    }

    fun setupLighting(environment: Environment) {
        environment.add(spotLight)
        environment.add(ambientPointLight)
    }

    fun update(delta: Float, worldData: MazeWorldData) {
        handleMouseLook()
        handleMovement(delta, worldData)
        updateCameraAndFlashlight(delta)
    }

    private fun handleMouseLook() {
        val dx = Gdx.input.deltaX
        val dy = Gdx.input.deltaY

        yaw -= dx * mouseSensitivity
        pitch -= dy * mouseSensitivity
        pitch = MathUtils.clamp(pitch, -85f, 85f)

        updateCameraDirection()
    }

    private fun updateCameraDirection() {
        val radYaw = yaw * MathUtils.degreesToRadians
        val radPitch = pitch * MathUtils.degreesToRadians

        direction.x = MathUtils.cos(radPitch) * MathUtils.cos(radYaw)
        direction.y = MathUtils.sin(radPitch)
        direction.z = MathUtils.cos(radPitch) * MathUtils.sin(radYaw)
        direction.nor()
    }

    private fun handleMovement(delta: Float, worldData: MazeWorldData) {
        var forward = 0f
        var strafe = 0f

        if (Gdx.input.isKeyPressed(Input.Keys.W)) forward += 1f
        if (Gdx.input.isKeyPressed(Input.Keys.S)) forward -= 1f
        if (Gdx.input.isKeyPressed(Input.Keys.A)) strafe -= 1f
        if (Gdx.input.isKeyPressed(Input.Keys.D)) strafe += 1f

        if (forward != 0f || strafe != 0f) {
            walkTimer += delta * 11f
            val radYaw = yaw * MathUtils.degreesToRadians

            val fwdX = MathUtils.cos(radYaw)
            val fwdZ = MathUtils.sin(radYaw)

            val rightX = -MathUtils.sin(radYaw)
            val rightZ = MathUtils.cos(radYaw)

            var moveX = fwdX * forward + rightX * strafe
            var moveZ = fwdZ * forward + rightZ * strafe

            val len = Math.sqrt((moveX * moveX + moveZ * moveZ).toDouble()).toFloat()
            if (len > 0.001f) {
                moveX = (moveX / len) * moveSpeed * delta
                moveZ = (moveZ / len) * moveSpeed * delta
            }

            // Коллизия со стенами
            val nextX = position.x + moveX
            if (!checkWallCollision(nextX, position.z, worldData)) {
                position.x = nextX
            }

            val nextZ = position.z + moveZ
            if (!checkWallCollision(position.x, nextZ, worldData)) {
                position.z = nextZ
            }
        }
    }

    private fun checkWallCollision(worldX: Float, worldZ: Float, worldData: MazeWorldData): Boolean {
        val minGX = ((worldX - playerRadius) / worldData.cellSize).toInt()
        val maxGX = ((worldX + playerRadius) / worldData.cellSize).toInt()
        val minGZ = ((worldZ - playerRadius) / worldData.cellSize).toInt()
        val maxGZ = ((worldZ + playerRadius) / worldData.cellSize).toInt()

        for (gx in minGX..maxGX) {
            for (gz in minGZ..maxGZ) {
                if (gx < 0 || gx >= worldData.width || gz < 0 || gz >= worldData.height) {
                    return true
                }
                if (worldData.grid[gx][gz] == MazeTileType.WALL) {
                    return true
                }
            }
        }
        return false
    }

    private fun updateCameraAndFlashlight(delta: Float) {
        val bobbingY = MathUtils.sin(walkTimer) * 0.06f
        val bobbingX = MathUtils.cos(walkTimer * 0.5f) * 0.04f

        camera.position.set(position.x + bobbingX, eyeHeight + bobbingY, position.z)
        camera.direction.set(direction)
        camera.up.set(up)
        camera.update()

        spotLight.position.set(camera.position.x, camera.position.y - 0.1f, camera.position.z)
        spotLight.direction.set(camera.direction)

        ambientPointLight.position.set(camera.position)
    }
}