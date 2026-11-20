package com.v2ray.ang.ui.main

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AngApplication
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.ui.wallet.WalletActivity
import com.v2ray.ang.ui.wallet.WalletStore
import com.v2ray.ang.zvpn.ServerApi
import com.v2ray.ang.zvpn.ZvpnApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : HelperBaseComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application, MainRepository(application as AngApplication))
    }

    private var busy by mutableStateOf(false)
    private var refreshKey by mutableStateOf(0)
    private var loggedIn by mutableStateOf(false)

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) startV2Ray()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loggedIn = ZvpnApi.isLoggedIn(this)
        mainViewModel.onAction(MainAction.Initialize)
        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}
        // keep wallet / subscription / account status fresh while the app is open
        lifecycleScope.launch {
            while (true) {
                delay(60_000)
                refreshState()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loggedIn = ZvpnApi.isLoggedIn(this)
        refreshKey++
        refreshState()
    }

    private fun refreshState() {
        if (!ZvpnApi.isLoggedIn(this)) return
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { ZvpnApi.refreshState(this@MainActivity) }
            } catch (e: ZvpnApi.ApiException) {
                if (e.code == "login") loggedIn = false
            } catch (e: Exception) {
                // offline: keep showing the cached state
            }
            refreshKey++
        }
    }

    @Composable
    override fun ScreenContent() {
        BackHandler { moveTaskToBack(false) }
        val state by mainViewModel.uiState.collectAsState()
        if (!loggedIn) {
            LoginScreen(onLoggedIn = {
                loggedIn = true
                refreshKey++
            })
            return
        }
        HomeScreen(
            isRunning = state.isRunning,
            busy = busy,
            refreshKey = refreshKey,
            onConnectClick = { onConnectClick(state.isRunning) },
            onWalletClick = { startActivity(Intent(this, WalletActivity::class.java)) },
            onExpired = { LauncherManager.stopService(this) },
        )
    }

    private fun onConnectClick(isRunning: Boolean) {
        if (busy) return
        if (isRunning) {
            LauncherManager.stopService(this)
            return
        }
        if (!WalletStore.hasActiveSubscription(this)) {
            Toast.makeText(this, "اعتبار شما به پایان رسیده است", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, WalletActivity::class.java))
            return
        }
        busy = true
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { ServerApi.fetchAndImport(this@MainActivity) }
            busy = false
            if (result.ok) {
                requestServiceStart()
            } else {
                Toast.makeText(this@MainActivity, result.error ?: "خطا در دریافت کانفیگ", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun requestServiceStart() {
        if (!SettingsManager.isVpnMode()) {
            startV2Ray()
            return
        }
        val intent = VpnService.prepare(this)
        if (intent == null) startV2Ray() else requestVpnPermission.launch(intent)
    }

    private fun startV2Ray() {
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            Toast.makeText(this, "کانفیگی برای اتصال وجود ندارد", Toast.LENGTH_LONG).show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            checkAndRequestPermission(PermissionType.ACCESS_LOCAL_NETWORK) {}
        }
        LauncherManager.startService(this)
    }
}
