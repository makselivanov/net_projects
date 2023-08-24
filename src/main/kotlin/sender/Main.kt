package sender

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Отправитель TCP",
        state = rememberWindowState(width = 800.dp, height = 350.dp)
    ) {
        val app = App()
        app.run()
    }
}