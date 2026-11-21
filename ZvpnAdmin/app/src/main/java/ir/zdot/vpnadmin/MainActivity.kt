package ir.zdot.vpnadmin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AdminTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(Modifier.fillMaxSize()) {
                        AppRoot()
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs.get(ctx) }
    val api = remember { AdminApi(prefs) }
    var loggedIn by remember { mutableStateOf(prefs.adminToken != null) }

    if (!loggedIn) {
        AdminLogin(onDone = { loggedIn = true })
        return
    }

    var tab by remember { mutableStateOf(0) }
    var openUser by remember { mutableStateOf<Int?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    val onAuth: () -> Unit = { loggedIn = false }

    BackHandler(enabled = openUser != null) { openUser = null; refreshKey++ }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("ZVPN مدیر", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                UpdateButton(prefs.baseUrl, "admin")
                TextButton(onClick = { prefs.adminToken = null; loggedIn = false }) { Text("خروج") }
            }
        }

        Box(Modifier.weight(1f)) {
            val u = openUser
            if (u != null) {
                UserDetail(api, u, onBack = { openUser = null; refreshKey++ }, onAuth = onAuth)
            } else {
                when (tab) {
                    0 -> CustomersTab(api, refreshKey, onAuth, onOpen = { openUser = it })
                    1 -> DepositsTab(api, refreshKey, onAuth)
                    else -> GatewayTab(api, onAuth)
                }
            }
        }

        if (openUser == null) {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f), tonalElevation = 0.dp) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("👥") }, label = { Text("مشتریان") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("💳") }, label = { Text("واریزها") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Text("📩") }, label = { Text("پیامک") })
            }
        }
    }
}
