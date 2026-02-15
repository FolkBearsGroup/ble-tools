package net.moonmile.folkbears.transmitter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import net.moonmile.folkbears.transmitter.service.ENSimTransmitter
import net.moonmile.folkbears.transmitter.service.GattAdvertise

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
            TransmitterTab.FolkBears -> FolkBearsTransmitterTab()
            TransmitterTab.EnApi -> EnApiTransmitterTab()
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

@Composable
private fun FolkBearsTransmitterTab() {
    val context = LocalContext.current
    val advertiser = remember { GattAdvertise(context) }
    var isAdvertising by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = if (isAdvertising) "FolkBears 発信中" else "FolkBears 停止中",
            style = MaterialTheme.typography.titleMedium
        )

        Row(modifier = Modifier.padding(top = 12.dp)) {
            Switch(
                checked = isAdvertising,
                onCheckedChange = { checked ->
                    if (checked) {
                        advertiser.startAdvertising()
                    } else {
                        advertiser.stopAdvertising()
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
            text = "UUID: ${GattAdvertise.SERVICE_UUID}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Composable
private fun EnApiTransmitterTab() {
    val context = LocalContext.current
    var tempIdHex by rememberSaveable {
        var hex = ""
        for (i in 0 until 16) {
            hex += (0..0xFF).random().toString(16).uppercase().padStart(2, '0')
        }
        mutableStateOf(hex)
    } // 16 bytes hex
    var useAlt by rememberSaveable { mutableStateOf(false) }
    var isAdvertising by rememberSaveable { mutableStateOf(false) }
    val transmitter = remember(tempIdHex, useAlt) {
        val bytes = tempIdHex.toByteArrayFromHex()
        ENSimTransmitter(context, tempIdBytes = bytes, useAltService = useAlt)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = if (isAdvertising) "EN API 発信中" else "EN API 停止中",
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            value = tempIdHex,
            onValueChange = { tempIdHex = it.filterHex(limit = 32) },
            label = { Text("TempId (32 hex chars)") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        )

        Row(modifier = Modifier.padding(top = 12.dp)) {
            Switch(
                checked = useAlt,
                onCheckedChange = { checked -> useAlt = checked }
            )
            Text(
                text = if (useAlt) "ALT UUID FF00" else "FD6F UUID",
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Row(modifier = Modifier.padding(top = 12.dp)) {
            Switch(
                checked = isAdvertising,
                onCheckedChange = { checked ->
                    if (checked) {
                        transmitter.stopTransmitter() // reset before restart
                            val bytes = tempIdHex.toByteArrayFromHex()
                            transmitter.useAltService = useAlt
                            transmitter.tempIdBytes = bytes
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
    }
}

private fun String.filterHex(limit: Int): String {
    return uppercase()
        .filter { it.isDigit() || it in 'A'..'F' }
        .take(limit)
}

private fun String.toByteArrayFromHex(): ByteArray {
    if (length % 2 != 0) return ByteArray(0)
    return chunked(2)
        .mapNotNull { it.toIntOrNull(16)?.toByte() }
        .toByteArray()
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
    ManufacturerData("MfD")
}

@Preview(showBackground = true)
@Composable
fun TransmitterScreenPreview() {
    FolkbearsTransmitterTheme {
        TransmitterScreen()
    }
}