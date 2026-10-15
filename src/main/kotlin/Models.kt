import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.ModelInstance

enum class GameState {
    START, PLAYING, FAIL_ANIMATION, GAME_OVER, COLLECTION, TOWER_INSPECT, DIALOGUE, SETTINGS, STOCK_EXCHANGE, SECRET_MINIGAME, MAZE_MINIGAME
}

data class BlockData(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var width: Float = 0f,
    var height: Float = 0f,
    var depth: Float = 0f,
    var color: Color = Color.WHITE,
    @Transient var instance: ModelInstance? = null
)

data class FallingBlock(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var width: Float = 0f,
    var height: Float = 0f,
    var depth: Float = 0f,
    var color: Color = Color.WHITE,
    var velocityX: Float = 0f,
    var velocityY: Float = 0f,
    var velocityZ: Float = 0f,
    var rotationX: Float = 0f,
    var rotationZ: Float = 0f,
    var rotSpeedX: Float = 100f,
    var rotSpeedZ: Float = 100f,
    @Transient var instance: ModelInstance? = null
)

class SavedTowerData {
    var id: String = ""
    var score: Int = 0
    var blocks: ArrayList<BlockData> = ArrayList()
}

class SavedTowerEntry(
    val data: SavedTowerData,
    val texture: Texture
)