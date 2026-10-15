package com.stackgame.core

enum class GameLanguage(val code: String) {
    RUSSIAN("ru"), ENGLISH("en");

    companion object {
        fun fromCode(code: String) = entries.firstOrNull { it.code == code } ?: RUSSIAN
    }
}

enum class UiText(val ru: String, val en: String) {
    TITLE("THE STACK 3D", "THE STACK 3D"),
    TAGLINE("Построй свою самую высокую башню", "Build your highest tower"),
    BEST("РЕКОРД", "BEST"), SCORE("СЧЁТ", "SCORE"),
    PLAY("ИГРАТЬ", "PLAY"), RECORDS("РЕКОРДЫ", "RECORDS"),
    SETTINGS("НАСТРОЙКИ", "SETTINGS"),
    TAP("КОСНИСЬ ЭКРАНА", "TAP TO STACK"), PERFECT("ИДЕАЛЬНО!", "PERFECT!"),
    OVER("ИГРА ОКОНЧЕНА", "GAME OVER"), AGAIN("ЕЩЁ РАЗ", "PLAY AGAIN"),
    PAUSED("ПАУЗА", "PAUSED"), CONTINUE("ПРОДОЛЖИТЬ", "CONTINUE"),
    END("ЗАВЕРШИТЬ ИГРУ", "END RUN"), BACK("НАЗАД", "BACK"),
    LOCAL_RECORDS("ЛОКАЛЬНЫЕ РЕКОРДЫ", "LOCAL RECORDS"),
    NO_RECORDS("Сыграй свою первую игру", "Play your first round"),
    LANGUAGE("ЯЗЫК ИГРЫ", "GAME LANGUAGE"), NEXT("ДАЛЕЕ", "NEXT"),
    SAVE("СОХРАНИТЬ", "SAVE"), GUIDE("ГАЙД ГЛОБА", "GLOB'S GUIDE"),
    REPLAY_GUIDE("ПОВТОРИТЬ ГАЙД", "REPLAY GUIDE"),
    VIBRATION_ON("ВИБРАЦИЯ: ВКЛ", "VIBRATION: ON"),
    VIBRATION_OFF("ВИБРАЦИЯ: ВЫКЛ", "VIBRATION: OFF"),
    GLOB("ГЛОБ", "GLOB"), SKIP("ПРОПУСТИТЬ", "SKIP"),
    REVEAL("ПОКАЗАТЬ ТЕКСТ", "SHOW TEXT");

    fun inLanguage(language: GameLanguage) = if (language == GameLanguage.RUSSIAN) ru else en
}

data class GuidePage(val portrait: String, val ru: String, val en: String) {
    fun inLanguage(language: GameLanguage) = if (language == GameLanguage.RUSSIAN) ru else en
}

/** Short mobile replicas: one idea per card, keeping the tower visible above it. */
object GlobGuide {
    val pages = listOf(
        GuidePage("glob1", "Добро пожаловать в Stack 3D!!!1!1 Я Глоб. Сейчас соберём слоёный пирог. Ну… башню.",
            "Welcome to Stack 3D!!!1!1 I'm Glob. Let's make a puff pastry pie. Well… a tower."),
        GuidePage("glob2", "Видишь движущийся блок? Коснись экрана, когда он окажется над башней. Так появится новый слой.",
            "See the moving block? Tap the screen when it's over the tower. That's your next layer."),
        GuidePage("glob1", "Попадёшь ровно — ИДЕАЛЬНО! Размер сохранится. А за серию точных попаданий будут крутые вспышки.",
            "Line it up — PERFECT! Your block keeps its size. Keep a perfect streak for bigger flashes."),
        GuidePage("glob2", "Лишний край отрежется. Совсем промахнёшься — БАБАХ, конец игры! Сверху есть кнопка паузы.",
            "Any overhang gets cut off. Miss completely — BOOM, game over! The pause button is at the top."),
        GuidePage("glob3", "В общем, строй повыше. А у меня важные дела. Если что, мой гайд и язык найдёшь в настройках!",
            "Anyway, build it high. I've got important things to do. My guide and the language are in settings!")
    )
}
