package id.irham.runanalyst

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

val DarkBg = Color(0xFF0F172A)
val PanelBg = Color(0xFF1E293B)
val CardBg = Color(0xFF162032)
val TextMain = Color(0xFFE2E8F0)
val TextMuted = Color(0xFF94A3B8)
val Accent = Color(0xFFF97316)
val Blue = Color(0xFF38BDF8)
val Green = Color(0xFF22C55E)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RunAnalystApp() }
    }
}

@Composable
fun RunAnalystApp() {
    val context = LocalContext.current
    val manager = remember { HealthConnectManager(context) }
    val scope = rememberCoroutineScope()
    var runs by remember { mutableStateOf<List<RunActivity>>(emptyList()) }
    var status by remember { mutableStateOf("Memeriksa Health Connect…") }
    var needPermission by remember { mutableStateOf(false) }

    fun loadRuns() {
        scope.launch {
            when {
                !manager.isAvailable() ->
                    status = "Health Connect tidak tersedia di HP ini. Pasang dari Play Store: \"Health Connect\"."
                !manager.hasPermissions() -> {
                    status = "Butuh izin Health Connect untuk membaca data lari."
                    needPermission = true
                }
                else -> {
                    status = "Memuat data…"
                    runs = manager.readRuns(180)
                    status = ""
                }
            }
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(manager.permissions)) loadRuns()
        else {
            status = "Izin ditolak. Data lari tidak bisa dibaca tanpa izin Health Connect."
            needPermission = true
        }
    }

    LaunchedEffect(Unit) { loadRuns() }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize(), color = DarkBg) {
            if (status.isNotEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(status, color = TextMuted, fontSize = 15.sp)
                    Spacer(Modifier.height(16.dp))
                    if (manager.isAvailable() && needPermission) {
                        Button(
                            onClick = { permLauncher.launch(manager.permissions) },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent)
                        ) { Text("Beri Izin Health Connect") }
                    }
                }
            } else {
                Dashboard(runs)
            }
        }
    }
}

@Composable
fun Dashboard(runs: List<RunActivity>) {
    val weeks = weeklyStats(runs)
    val recs = buildRecommendations(weeks, runs)
    val last = weeks.last()
    val prev = weeks[10]
    val fmtDate = DateTimeFormatter.ISO_DATE

    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Run Analyst", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp)) }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Jarak minggu ini", "${fmt1(last.km)} km", "vs minggu lalu: ${fmt1(prev.km)} km", Modifier.weight(1f))
                StatCard("Pace rata-rata", fmtPace(last.pace), "minggu ini", Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Sesi minggu ini", "${last.runs}", "target: 3–5", Modifier.weight(1f))
                StatCard("Volume 4 mggu", "${weeks.subList(8, 12).sumOf { it.km }.toInt()} km", "blok terakhir", Modifier.weight(1f))
            }
        }

        item { Panel("Tren Jarak Mingguan") {
            BarChart(weeks.map { it.km }, weeks.mapIndexed { i, _ -> if (i == 11) "skg" else "M${i + 1}" },
                target = weeks.subList(0, 11).sumOf { it.km } / 11.0)
        } }

        item { Panel("Training Load") {
            BarChart(weeks.map { it.load / 100 }, weeks.mapIndexed { i, _ -> if (i == 11) "skg" else "M${i + 1}" },
                target = weeks.subList(0, 11).sumOf { it.load } / 1100.0, barColor = Color(0xFFA78BFA))
        } }

        item { Panel("Konsistensi (12 minggu)") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                weeks.forEach { w ->
                    val c = when {
                        w.runs == 0 -> Color(0xFF1E293B)
                        w.runs <= 2 -> Color(0xFF14532D)
                        w.runs <= 3 -> Color(0xFF166534)
                        w.runs <= 4 -> Color(0xFF22C55E)
                        else -> Color(0xFF4ADE80)
                    }
                    Box(Modifier.weight(1f).aspectRatio(1f).background(c, RoundedCornerShape(6.dp)))
                }
            }
        } }

        item { Panel("🔎 Rekomendasi") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recs.forEach { RecRow(it) }
            }
        } }

        item { Panel("Riwayat Terakhir") {
            val recent = runs.takeLast(8).reversed()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                recent.forEach { a ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${a.date.format(fmtDate)} · ${a.name}", color = TextMain, fontSize = 13.sp)
                        Text("${fmt1(a.distanceKm)} km · ${fmtPace(a.paceMinPerKm)}/km", color = TextMuted, fontSize = 13.sp)
                    }
                }
            }
        } }

        item {
            Text("Data diambil dari Health Connect (Huawei Health / Strava).",
                color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(8.dp))
        }
    }
}

@Composable
fun StatCard(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Column(modifier
        .background(CardBg, RoundedCornerShape(12.dp))
        .padding(12.dp)) {
        Text(label.uppercase(), color = TextMuted, fontSize = 10.sp)
        Text(value, color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(sub, color = TextMuted, fontSize = 10.sp)
    }
}

@Composable
fun Panel(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(PanelBg, RoundedCornerShape(14.dp)).padding(16.dp)
    ) {
        Text(title, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
fun RecRow(r: Recommendation) {
    val bg = when (r.level) { "good" -> Color(0x1A22C55E); "warn" -> Color(0x1AEAB308); else -> Color(0x1AEF4444) }
    Row(
        Modifier.fillMaxWidth().background(bg, RoundedCornerShape(10.dp))
            .padding(10.dp), verticalAlignment = Alignment.Top
    ) {
        Text(r.icon, fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(r.text, color = TextMain, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
fun BarChart(values: List<Double>, labels: List<String>, target: Double, barColor: Color = Blue) {
    val max = ((values.maxOrNull() ?: 0.0).coerceAtLeast(target).coerceAtLeast(0.1)) * 1.15
    Column {
        BoxWithConstraints(Modifier.fillMaxWidth().height(180.dp)) {
            val h = maxHeight
            if (target > 0) {
                val y = h - ((target / max) * h.value).dp
                Box(Modifier.offset(y = y).fillMaxWidth().height(1.dp).background(Green))
            }
            Row(Modifier.fillMaxSize().align(Alignment.BottomCenter), verticalAlignment = Alignment.Bottom) {
                values.forEachIndexed { i, v ->
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(fmt1(v), color = TextMuted, fontSize = 8.sp)
                        Box(
                            Modifier
                                .fillMaxWidth(0.6f)
                                .height((((v / max) * h.value)).dp.coerceAtLeast(2.dp))
                                .background(if (i == values.size - 1) Accent else barColor,
                                    RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach {
                Text(it, color = TextMuted, fontSize = 8.sp, modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}
