import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration

fun main() {
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("The Stack 3D")
        setWindowedMode(800, 600)
        setResizable(false) // Запрещаем изменять размер окна и кнопку Maximize
        useVsync(true)
    }
    Lwjgl3Application(StackGame(), config)
}