package net.moonmile.folkbears.monitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.bluetooth.le.ScanSettings
import net.moonmile.folkbears.monitor.data.IBeaconAdvertisement
import net.moonmile.folkbears.monitor.data.TraceDataEntity
import net.moonmile.folkbears.monitor.service.BeaconScan
import net.moonmile.folkbears.monitor.service.ENSimScan
import net.moonmile.folkbears.monitor.service.GattClient
import net.moonmile.folkbears.monitor.service.ManufacturerDataScan
import net.moonmile.folkbears.monitor.ui.theme.FolkBearsMonitorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FolkBearsMonitorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MonitorScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MonitorScreen(modifier: Modifier = Modifier) {
    var selectedTab by rememberSaveable { mutableStateOf(MonitorTab.IBeacon) }
    var iBeaconScanMode by rememberSaveable { mutableStateOf(ScanSettings.SCAN_MODE_LOW_POWER) }
    var folkScanMode by rememberSaveable { mutableStateOf(ScanSettings.SCAN_MODE_LOW_POWER) }
    var enApiScanMode by rememberSaveable { mutableStateOf(ScanSettings.SCAN_MODE_LOW_POWER) }
    var mdScanMode by rememberSaveable { mutableStateOf(ScanSettings.SCAN_MODE_LOW_POWER) }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab.ordinal) {
            MonitorTab.entries.forEach { tab ->
                Tab(
                    selected = tab == selectedTab,
                    onClick = { selectedTab = tab },
                    text = { Text(tab.title) }
                )
            }
        }

        when (selectedTab) {
            MonitorTab.IBeacon -> IBeaconTabContent(
                scanMode = iBeaconScanMode,
                onScanModeChange = { iBeaconScanMode = it }
            )
            MonitorTab.FolkBears -> FolkBearsTabContent(
                scanMode = folkScanMode,
                onScanModeChange = { folkScanMode = it }
            )
            MonitorTab.EnApi -> EnApiTabContent(
                scanMode = enApiScanMode,
                onScanModeChange = { enApiScanMode = it }
            )
            MonitorTab.ManufacturerData -> ManufacturerDataTabContent(
                scanMode = mdScanMode,
                onScanModeChange = { mdScanMode = it }
            )
        }
    }
}

@Composable
private fun PlaceholderTab(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
    )
}

@Composable
private fun IBeaconTabContent(
    scanMode: Int,
    onScanModeChange: (Int) -> Unit
) {
    val context = LocalContext.current
    val scan = remember { BeaconScan(context) }
    val ads: SnapshotStateList<IBeaconAdvertisement> = remember { mutableStateListOf() }
    val windowMs = 5 * 60 * 1000L // 5 minutes

    var hasPermission by remember { mutableStateOf(hasScanPermissions(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermission = result.values.all { it }
    }

    // Collect scan results
    LaunchedEffect(scan, hasPermission, scanMode) {
        if (hasPermission) {
            scan.stopScan()
            scan.onIBeacon = { ad ->
                ads.add(ad)
                pruneOld(ads, windowMs)
            }
            scan.startScan(scanMode)
        }
    }

    // Periodic prune to keep window sliding
    LaunchedEffect(ads) {
        while (true) {
            pruneOld(ads, windowMs)
            delay(10_000)
        }
    }

    // Stop scan when composable leaves the composition
    DisposableEffect(hasPermission) {
        onDispose { scan.stopScan() }
    }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "スキャンモード",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            var expanded by remember { mutableStateOf(false) }
            val modes = listOf(
                ScanSettings.SCAN_MODE_LOW_POWER to "Low Power",
                ScanSettings.SCAN_MODE_BALANCED to "Balanced",
                ScanSettings.SCAN_MODE_LOW_LATENCY to "Low Latency",
            )
            val currentLabel = modes.find { it.first == scanMode }?.second ?: "Low Power"

            androidx.compose.material3.OutlinedButton(
                onClick = { expanded = !expanded },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text(currentLabel)
            }

            if (expanded) {
                androidx.compose.material3.DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    modes.forEach { (mode, label) ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onScanModeChange(mode)
                                expanded = false
                            }
                        )
                    }
                }
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
                            android.Manifest.permission.BLUETOOTH_SCAN,
                            android.Manifest.permission.BLUETOOTH_CONNECT,
                            android.Manifest.permission.ACCESS_FINE_LOCATION
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

    val grouped = ads.groupBy { Triple(it.serviceUuid, it.major, it.minor) }
        .map { (key, values) ->
            val last = values.maxByOrNull { it.timestamp }!!
            IBeaconRowData(
                serviceUuid = key.first,
                major = key.second,
                minor = key.third,
                count = values.size,
                lastSeen = last.timestamp,
                rssi = last.rssi,
                txPower = last.txPower
            )
        }
        .sortedByDescending { it.lastSeen }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(grouped, key = { "${it.serviceUuid}-${it.major}-${it.minor}" }) { row ->
            IBeaconRow(row)
        }
    }
}

@Composable
private fun EnApiTabContent(
    scanMode: Int,
    onScanModeChange: (Int) -> Unit
) {
    val context = LocalContext.current
    val scan = remember { ENSimScan(context) }
    val ads: SnapshotStateList<TraceDataEntity> = remember { mutableStateListOf() }
    val windowMs = 5 * 60 * 1000L // 5 minutes

    LaunchedEffect(scan, scanMode) {
        scan.stopScan()
        scan.onReadTraceData = { ad ->
            ads.add(ad)
            pruneOldTrace(ads, windowMs)
        }
        scan.startScan(scanMode)
    }

    LaunchedEffect(ads) {
        while (true) {
            pruneOldTrace(ads, windowMs)
            delay(10_000)
        }
    }

    DisposableEffect(Unit) {
        onDispose { scan.stopScan() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "スキャンモード",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        var expanded by remember { mutableStateOf(false) }
        val modes = listOf(
            ScanSettings.SCAN_MODE_LOW_POWER to "Low Power",
            ScanSettings.SCAN_MODE_BALANCED to "Balanced",
            ScanSettings.SCAN_MODE_LOW_LATENCY to "Low Latency",
        )
        val currentLabel = modes.find { it.first == scanMode }?.second ?: "Low Power"

        androidx.compose.material3.OutlinedButton(
            onClick = { expanded = !expanded },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Text(currentLabel)
        }

        if (expanded) {
            androidx.compose.material3.DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                modes.forEach { (mode, label) ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onScanModeChange(mode)
                            expanded = false
                        }
                    )
                }
            }
        }
    }

    val grouped = ads.groupBy { it.tempId }
        .map { (tempId, values) ->
            val last = values.maxByOrNull { it.timestamp }!!
            EnApiRowData(
                tempId = tempId,
                count = values.size,
                lastSeen = last.timestamp,
                rssi = last.rssi,
                txPower = last.txPower
            )
        }
        .sortedByDescending { it.lastSeen }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(grouped, key = { it.tempId }) { row ->
            EnApiRow(row)
        }
    }
}

@Composable
private fun ManufacturerDataTabContent(
    scanMode: Int,
    onScanModeChange: (Int) -> Unit
) {
    val context = LocalContext.current
    val manufacturerId = 0xFFFF // Adjust if targeting a different vendor
    val scan = remember { ManufacturerDataScan(context, manufacturerId) }
    val ads: SnapshotStateList<ManufacturerDataScan.ManufacturerRecord> = remember { mutableStateListOf() }
    val windowMs = 5 * 60 * 1000L // 5 minutes

    var hasPermission by remember { mutableStateOf(hasScanPermissions(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermission = result.values.all { it }
    }

    LaunchedEffect(scan, hasPermission, scanMode) {
        if (hasPermission) {
            scan.stopScan()
            scan.onReadManufacturerData = { ad ->
                ads.add(ad)
                pruneOldManufacturer(ads, windowMs)
            }
            scan.startScan(scanMode)
        }
    }

    LaunchedEffect(ads) {
        while (true) {
            pruneOldManufacturer(ads, windowMs)
            delay(10_000)
        }
    }

    DisposableEffect(hasPermission) {
        onDispose { scan.stopScan() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "スキャンモード",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        var expanded by remember { mutableStateOf(false) }
        val modes = listOf(
            ScanSettings.SCAN_MODE_LOW_POWER to "Low Power",
            ScanSettings.SCAN_MODE_BALANCED to "Balanced",
            ScanSettings.SCAN_MODE_LOW_LATENCY to "Low Latency",
        )
        val currentLabel = modes.find { it.first == scanMode }?.second ?: "Low Power"

        androidx.compose.material3.OutlinedButton(
            onClick = { expanded = !expanded },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Text(currentLabel)
        }

        if (expanded) {
            androidx.compose.material3.DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                modes.forEach { (mode, label) ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onScanModeChange(mode)
                            expanded = false
                        }
                    )
                }
            }
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
                            android.Manifest.permission.BLUETOOTH_SCAN,
                            android.Manifest.permission.BLUETOOTH_CONNECT,
                            android.Manifest.permission.ACCESS_FINE_LOCATION
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

    val grouped = ads.groupBy { it.tempid.toHexString() }
        .map { (tempId, values) ->
            val last = values.maxByOrNull { it.timestamp }!!
            ManufacturerRowData(
                tempId = tempId,
                count = values.size,
                lastSeen = last.timestamp,
                rssi = last.rssi,
                txPower = last.txPower,
                deviceAddress = last.deviceAddress
            )
        }
        .sortedByDescending { it.lastSeen }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(grouped, key = { it.tempId }) { row ->
            ManufacturerRow(row)
        }
    }
}

@Composable
private fun FolkBearsTabContent(
    scanMode: Int,
    onScanModeChange: (Int) -> Unit
) {
    val context = LocalContext.current
    val client = remember { GattClient(context) }
    val hits: SnapshotStateList<FolkDeviceHit> = remember { mutableStateListOf() }
    val windowMs = 5 * 60 * 1000L // 5 minutes

    LaunchedEffect(client, scanMode) {
        client.stopSearchDevice()
        client.onScanGattDevice = { mac, result ->
            val name = result.device.name ?: "(no name)"
            hits.add(
                FolkDeviceHit(
                    mac = mac,
                    name = name,
                    rssi = result.rssi,
                    timestamp = System.currentTimeMillis()
                )
            )
            pruneOldFolk(hits, windowMs)
        }
        client.startSearchDevice(scanMode)
    }

    LaunchedEffect(hits) {
        while (true) {
            pruneOldFolk(hits, windowMs)
            delay(10_000)
        }
    }

    DisposableEffect(Unit) {
        onDispose { client.stopSearchDevice() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "スキャンモード",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        var expanded by remember { mutableStateOf(false) }
        val modes = listOf(
            ScanSettings.SCAN_MODE_LOW_POWER to "Low Power",
            ScanSettings.SCAN_MODE_BALANCED to "Balanced",
            ScanSettings.SCAN_MODE_LOW_LATENCY to "Low Latency",
        )
        val currentLabel = modes.find { it.first == scanMode }?.second ?: "Low Power"

        androidx.compose.material3.OutlinedButton(
            onClick = { expanded = !expanded },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Text(currentLabel)
        }

        if (expanded) {
            androidx.compose.material3.DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                modes.forEach { (mode, label) ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onScanModeChange(mode)
                            expanded = false
                        }
                    )
                }
            }
        }
    }

    val grouped = hits.groupBy { it.mac }
        .map { (mac, values) ->
            val last = values.maxByOrNull { it.timestamp }!!
            FolkRowData(
                mac = mac,
                name = last.name,
                count = values.size,
                lastSeen = last.timestamp,
                rssi = last.rssi
            )
        }
        .sortedByDescending { it.lastSeen }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(grouped, key = { it.mac }) { row ->
            FolkBearsRow(row)
        }
    }
}

private fun pruneOld(list: SnapshotStateList<IBeaconAdvertisement>, windowMs: Long) {
    val cutoff = System.currentTimeMillis() - windowMs
    list.removeAll { it.timestamp < cutoff }
}

private fun pruneOldTrace(list: SnapshotStateList<TraceDataEntity>, windowMs: Long) {
    val cutoff = System.currentTimeMillis() - windowMs
    list.removeAll { it.timestamp < cutoff }
}

private fun pruneOldFolk(list: SnapshotStateList<FolkDeviceHit>, windowMs: Long) {
    val cutoff = System.currentTimeMillis() - windowMs
    list.removeAll { it.timestamp < cutoff }
}

private fun pruneOldManufacturer(list: SnapshotStateList<ManufacturerDataScan.ManufacturerRecord>, windowMs: Long) {
    val cutoff = System.currentTimeMillis() - windowMs
    list.removeAll { it.timestamp < cutoff }
}

private data class IBeaconRowData(
    val serviceUuid: String,
    val major: Int,
    val minor: Int,
    val count: Int,
    val lastSeen: Long,
    val rssi: Int,
    val txPower: Int,
)

private data class EnApiRowData(
    val tempId: String,
    val count: Int,
    val lastSeen: Long,
    val rssi: Int,
    val txPower: Int,
)

private data class FolkDeviceHit(
    val mac: String,
    val name: String,
    val rssi: Int,
    val timestamp: Long,
)

private data class FolkRowData(
    val mac: String,
    val name: String,
    val count: Int,
    val lastSeen: Long,
    val rssi: Int,
)

private data class ManufacturerRowData(
    val tempId: String,
    val count: Int,
    val lastSeen: Long,
    val rssi: Int,
    val txPower: Int,
    val deviceAddress: String,
)

@Composable
private fun IBeaconRow(row: IBeaconRowData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text = "Service UUID: ${row.serviceUuid}", style = MaterialTheme.typography.titleSmall)
        Row(modifier = Modifier.padding(top = 4.dp)) {
            Text(text = "major=0x${row.major.toString(16)}")
            Text(text = "  minor=0x${row.minor.toString(16)}", modifier = Modifier.padding(start = 8.dp))
        }
        Row(modifier = Modifier.padding(top = 4.dp)) {
            Text(text = "受信回数(5分内): ${row.count}")
            Text(text = "  RSSI: ${row.rssi}", modifier = Modifier.padding(start = 8.dp))
            Text(text = "  Tx: ${row.txPower}", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun EnApiRow(row: EnApiRowData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text = "TempId: ${row.tempId}", style = MaterialTheme.typography.titleSmall)
        Row(modifier = Modifier.padding(top = 4.dp)) {
            Text(text = "受信回数(5分内): ${row.count}")
            Text(text = "  RSSI: ${row.rssi}", modifier = Modifier.padding(start = 8.dp))
            Text(text = "  Tx: ${row.txPower}", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun ManufacturerRow(row: ManufacturerRowData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text = "TempId: ${row.tempId}", style = MaterialTheme.typography.titleSmall)
        Row(modifier = Modifier.padding(top = 4.dp)) {
            Text(text = "受信回数(5分内): ${row.count}")
            Text(text = "  RSSI: ${row.rssi}", modifier = Modifier.padding(start = 8.dp))
            Text(text = "  Tx: ${row.txPower}", modifier = Modifier.padding(start = 8.dp))
        }
        Row(modifier = Modifier.padding(top = 4.dp)) {
            Text(text = "MAC: ${row.deviceAddress}")
        }
    }
}

private fun hasScanPermissions(context: Context): Boolean {
    val scan = ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    val connect = ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    val fine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val legacy = ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
    return (scan && connect) || (legacy && fine) || (scan && fine)
}

private fun ByteArray.toHexString(): String = joinToString(separator = "") { eachByte -> "%02X".format(eachByte) }

@Composable
private fun FolkBearsRow(row: FolkRowData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text = "MAC: ${row.mac}", style = MaterialTheme.typography.titleSmall)
        Row(modifier = Modifier.padding(top = 4.dp)) {
            Text(text = "Name: ${row.name}")
        }
        Row(modifier = Modifier.padding(top = 4.dp)) {
            Text(text = "受信回数(5分内): ${row.count}")
            Text(text = "  RSSI: ${row.rssi}", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

private enum class MonitorTab(val title: String, val contentLabel: String) {
    IBeacon("iBeacon", "iBeacon の受信結果をここに表示します"),
    FolkBears("FolkBears", "FolkBears の受信結果をここに表示します"),
    EnApi("EN API", "EN API モードの受信結果をここに表示します"),
    ManufacturerData("MD", "Manufacturer Data の受信結果をここに表示します")
}

@Preview(showBackground = true)
@Composable
fun MonitorScreenPreview() {
    FolkBearsMonitorTheme {
        MonitorScreen()
    }
}