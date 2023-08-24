package sender

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class App {
    companion object {
        val showDialog = mutableStateOf(false)
        val textDialog = mutableStateOf("")
    }

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    fun alert() {
        AlertDialog(
            title = {
                Text(text = "Ошибка")
            },
            text = {
                Text(text = textDialog.value)
            },
            onDismissRequest = {

            },
            buttons = {
                Button(onClick = { showDialog.value = false }) {
                    Text("Ок")
                }
            }

        )
    }
    @Composable
    fun run() {
        var ipValue by rememberSaveable { mutableStateOf("127.0.0.1") }
        var portValue by rememberSaveable { mutableStateOf("8080") }
        var packetsValue by rememberSaveable { mutableStateOf("0") }

        MaterialTheme {
            if (showDialog.value) {
                alert()
            }
            Column(Modifier.fillMaxSize().background(Color.Gray).padding(vertical = 15.dp), Arrangement.spacedBy(30.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 30.dp), Arrangement.spacedBy(5.dp)) {
                    Text("Введите IP адрес получателя")
                    Spacer(Modifier.weight(1f))
                    TextField(value=ipValue, onValueChange = { ipValue = it })
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 30.dp), Arrangement.spacedBy(5.dp)) {
                    Text("Выберите порт отправки")
                    Spacer(Modifier.weight(1f))
                    TextField(portValue, { portValue = it })
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 30.dp), Arrangement.spacedBy(5.dp)) {
                    Text("Введите количество пакетов для отправки")
                    Spacer(Modifier.weight(1f))
                    TextField(packetsValue, { packetsValue = it })
                }
                Button(modifier = Modifier.align(Alignment.CenterHorizontally),
                    onClick = {
                        Backend().sendClick(ipValue, portValue, packetsValue)
                    }) {
                    Text("Отправить пакеты")
                }
            }

        }
    }
}