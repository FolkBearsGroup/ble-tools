package net.moonmile.folkbears.transmitter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.moonmile.folkbears.transmitter.ui.theme.FolkbearsTransmitterTheme
import net.moonmile.folkbears.transmitter.service.BeaconTransmitter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FolkbearsTransmitterTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TransmitterScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun TransmitterScreen(modifier: Modifier = Modifier) {
    var selectedTab by rememberSaveable { mutableStateOf(TransmitterTab.IBeacon) }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab.ordinal) {
            TransmitterTab.entries.forEach { tab ->
                Tab(
                    selected = tab == selectedTab,
                    onClick = { selectedTab = tab },
                    text = { Text(tab.title) }
                )
            }
        }

        when (selectedTab) {
            TransmitterTab.IBeacon -> IBeaconTransmitterTab()
            TransmitterTab.FolkBears -> TabPlaceholder("FolkBears の送信設定やステータスをここに追加してください。")
            TransmitterTab.EnApi -> TabPlaceholder("EN API の送信設定やステータスをここに追加してください。")
            TransmitterTab.ManufacturerData -> TabPlaceholder("Manufacturer Data の送信設定やステータスをここに追加してください。")
        }
    }
}

@Composable
private fun IBeaconTransmitterTab() {
    val context = LocalContext.current
    var majorHex by rememberSaveable { mutableStateOf((0..0xFFFF).random().toString(16).uppercase().padStart(4, '0')) }
    var minorHex by rememberSaveable { mutableStateOf((0..0xFFFF).random().toString(16).uppercase().padStart(4, '0')) }
    val transmitter = remember(majorHex, minorHex) { BeaconTransmitter(context, major = majorHex.toIntOrNull(16) ?: 0, minor = minorHex.toIntOrNull(16) ?: 0) }
    var isAdvertising by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        Text(text = if (isAdvertising) "iBeacon 発信中" else "iBeacon 停止中", style = MaterialTheme.typography.titleMedium)

        Row(modifier = Modifier.padding(top = 12.dp)) {
            OutlinedTextField(
                value = majorHex,
                onValueChange = { majorHex = it.filterHex(limit = 4) },
                label = { Text("Major (hex)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = minorHex,
                onValueChange = { minorHex = it.filterHex(limit = 4) },
                label = { Text("Minor (hex)") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
        }

        Row(modifier = Modifier.padding(top = 12.dp)) {
            Switch(
                checked = isAdvertising,
                onCheckedChange = { checked ->
                    if (checked) {
                        transmitter.major = majorHex.toIntOrNull(16) ?: 0
                        transmitter.minor = minorHex.toIntOrNull(16) ?: 0
                        transmitter.startTransmitter()
                    } else {
                        transmitter.stopTransmitter()
                    }
                    isAdvertising = checked
                }
            )
            Text(
                text = if (isAdvertising) "ON" else "OFF",
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Text(
            text = "UUID: ${BeaconTransmitter.SERVICE_UUID}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

private fun String.filterHex(limit: Int): String {
    return uppercase()
        .filter { it.isDigit() || it in 'A'..'F' }
        .take(limit)
}

@Composable
private fun TabPlaceholder(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
    )
}

private enum class TransmitterTab(val title: String) {
    IBeacon("iBeacon"),
    FolkBears("FolkBears"),
    EnApi("EN API"),
    ManufacturerData("MD")
}

@Preview(showBackground = true)
@Composable
fun TransmitterScreenPreview() {
    FolkbearsTransmitterTheme {
        TransmitterScreen()
    }
}