package net.moonmile.folkbears.transmitter

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import net.moonmile.folkbears.transmitter.service.BeaconTransmitter
import net.moonmile.folkbears.transmitter.service.ENSimTransmitter
import net.moonmile.folkbears.transmitter.service.GattAdvertise
import net.moonmile.folkbears.transmitter.service.ManufacturerDataTransmitter
import net.moonmile.folkbears.transmitter.ui.theme.FolkbearsTransmitterTheme

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
            TransmitterTab.ManufacturerData -> ManufacturerDataTransmitterTab()
        }
    }
}

private fun hasScanPermissions(context: Context): Boolean {
    val adv = ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
    val connect = ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    val legacy = ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
    val admin = ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        adv && connect
    } else {
        legacy && admin
    }
}


@Composable
private fun IBeaconTransmitterTab() {
    val context = LocalContext.current
    var majorHex by rememberSaveable { mutableStateOf((0..0xFFFF).random().toString(16).uppercase().padStart(4, '0')) }
    var minorHex by rememberSaveable { mutableStateOf((0..0xFFFF).random().toString(16).uppercase().padStart(4, '0')) }
    val transmitter = remember(majorHex, minorHex) { BeaconTransmitter(context, major = majorHex.toIntOrNull(16) ?: 0, minor = minorHex.toIntOrNull(16) ?: 0) }
    var isAdvertising by rememberSaveable { mutableStateOf(false) }

    var hasPermission by remember { mutableStateOf(hasScanPermissions(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermission = result.values.all { it }
    }

    // Collect scan results
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            // 権限が設定されたときの初期化
        }
    }

    if (!hasPermission) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Bluetoothスキャン権限が必要です。許可してください。",
                style = MaterialTheme.typography.bodyLarge
            )
            Button(
                onClick = {
                    permissionLauncher.launch(
                        arrayOf(
                            android.Manifest.permission.BLUETOOTH_ADVERTISE,
                            android.Manifest.permission.BLUETOOTH_CONNECT,
                            android.Manifest.permission.BLUETOOTH,
                            android.Manifest.permission.BLUETOOTH_ADMIN,
                        )
                    )
                },
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Text("権限をリクエスト")
            }
        }
        return
    }

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
private fun ManufacturerDataTransmitterTab() {
    val context = LocalContext.current
    var tempIdHex by rememberSaveable {
        // 16 bytes random
        var hex = ""
        repeat(16) { hex += (0..0xFF).random().toString(16).uppercase().padStart(2, '0') }
        mutableStateOf(hex)
    }
    var manufacturerIdHex by rememberSaveable { mutableStateOf("FFFF") }
    var isAdvertising by rememberSaveable { mutableStateOf(false) }
    val transmitter = remember(manufacturerIdHex, tempIdHex) {
        val tempBytes = tempIdHex.toByteArrayFromHex()
        val mId : Int = manufacturerIdHex.toIntOrNull(16) ?: 0xFFFF
        ManufacturerDataTransmitter(context, manufacturerId = mId, tempIdBytes = tempBytes)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = if (isAdvertising) "Manufacturer Data 発信中" else "Manufacturer Data 停止中",
            style = MaterialTheme.typography.titleMedium
        )

        Row(modifier = Modifier.padding(top = 12.dp)) {
            OutlinedTextField(
                value = manufacturerIdHex,
                onValueChange = { manufacturerIdHex = it.filterHex(limit = 4) },
                label = { Text("Manufacturer ID (hex)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        Row(modifier = Modifier.padding(top = 12.dp)) {
            OutlinedTextField(
                value = tempIdHex,
                onValueChange = { tempIdHex = it.filterHex(limit = 32) },
                label = { Text("TempId (32 hex chars)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Row(modifier = Modifier.padding(top = 12.dp)) {
            Switch(
                checked = isAdvertising,
                onCheckedChange = { checked ->
                    if (checked) {
                        val tempBytes = tempIdHex.toByteArrayFromHex()
                        val mId : Int = manufacturerIdHex.toIntOrNull(16) ?: 0xFFFF
                        transmitter.manufacturerId = mId
                        transmitter.tempIdBytes = tempBytes

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

        Row(modifier = Modifier.padding(top = 12.dp)) {
            OutlinedTextField(
                value = tempIdHex,
                onValueChange = { tempIdHex = it.filterHex(limit = 32) },
                label = { Text("TempId (32 hex chars)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

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