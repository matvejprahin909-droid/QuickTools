package com.quicktools.app

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private val tick = mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = isSystemInDarkTheme()
            val ctx = LocalContext.current
            val scheme = if (Build.VERSION.SDK_INT >= 31) {
                if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            } else if (dark) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = scheme) { Surface(Modifier.fillMaxSize()) { Home(tick.intValue) } }
        }
    }
    override fun onResume() { super.onResume(); tick.intValue++ }
}

fun Context.toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

fun Context.open(action: String) = try {
    startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
} catch (e: Exception) { toast("Не удалось открыть") }

fun Context.startTimer(seconds: Int, label: String) = try {
    startActivity(Intent(AlarmClock.ACTION_SET_TIMER)
        .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
        .putExtra(AlarmClock.EXTRA_MESSAGE, label)
        .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    toast("Таймер: $label")
} catch (e: Exception) { toast("Не удалось поставить таймер") }

fun Context.setTorch(on: Boolean): Boolean = try {
    val cm = getSystemService(CameraManager::class.java)
    val id = cm.cameraIdList.first { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    cm.setTorchMode(id, on); true
} catch (e: Exception) { toast("Фонарик недоступен"); false }

@Composable
fun Card2(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 16.sp)
        Switch(checked, onChange)
    }
}

@Composable
fun RowScope.Btn(label: String, onClick: () -> Unit) =
    FilledTonalButton(onClick, Modifier.weight(1f)) { Text(label, maxLines = 1) }

@Composable
fun Home(tick: Int) {
    val ctx = LocalContext.current
    val nm = remember { ctx.getSystemService(NotificationManager::class.java) }
    val am = remember { ctx.getSystemService(AudioManager::class.java) }
    var dnd by remember(tick) { mutableStateOf(nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) }
    var ringer by remember(tick) { mutableIntStateOf(am.ringerMode) }
    var torch by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf("") }

    val rows = listOf(
        listOf("5 мин" to 300, "10 мин" to 600, "15 мин" to 900),
        listOf("30 мин" to 1800, "1 час" to 3600, "2 часа" to 7200)
    )

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Быстрые действия", fontSize = 32.sp, fontWeight = FontWeight.Bold)

        Card2("Таймер") {
            rows.forEach { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    r.forEach { (l, s) -> Btn(l) { ctx.startTimer(s, l) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    custom, { custom = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f),
                    label = { Text("Свои минуты") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Button(onClick = {
                    custom.toIntOrNull()?.takeIf { it > 0 }?.let { ctx.startTimer(it * 60, "$it мин") }
                }) { Text("Старт") }
            }
        }

        Card2("Режимы") {
            SwitchRow("Не беспокоить", dnd) { on ->
                if (!nm.isNotificationPolicyAccessGranted) {
                    ctx.toast("Дайте приложению доступ")
                    ctx.open(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                } else {
                    nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
                    dnd = on
                }
            }
            SwitchRow("Фонарик", torch) { on -> if (ctx.setTorch(on)) torch = on }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Звук" to AudioManager.RINGER_MODE_NORMAL, "Вибро" to AudioManager.RINGER_MODE_VIBRATE, "Тихо" to AudioManager.RINGER_MODE_SILENT)
                    .forEach { (l, m) ->
                        FilterChip(ringer == m, {
                            try { am.ringerMode = m; ringer = m } catch (e: SecurityException) {
                                ctx.toast("Нужен доступ к «Не беспокоить»")
                                ctx.open(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                            }
                        }, { Text(l) })
                    }
            }
        }

        Card2("Быстрые настройки") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Интернет") { ctx.open(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) }
                Btn("Bluetooth") { ctx.open(Settings.ACTION_BLUETOOTH_SETTINGS) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Экран") { ctx.open(Settings.ACTION_DISPLAY_SETTINGS) }
                Btn("Экономия") { ctx.open(Settings.ACTION_BATTERY_SAVER_SETTINGS) }
            }
        }
    }
}
