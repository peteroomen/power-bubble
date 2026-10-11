package io.github.peteroomen.powerbubble

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import rikka.shizuku.Shizuku

/** Setup and settings: turn on the service, connect Shizuku, choose how the bubble behaves. */
class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var accessibilityStatus: TextView
    private lateinit var openAccessibility: Button
    private lateinit var shizukuStatus: TextView
    private lateinit var shizukuAction: Button
    private lateinit var showBubble: Switch
    private lateinit var tapMode: RadioGroup

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, _ -> runOnUiThread { refresh() } }
    private val binderReceived = Shizuku.OnBinderReceivedListener { runOnUiThread { refresh() } }
    private val binderDead = Shizuku.OnBinderDeadListener { runOnUiThread { refresh() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        accessibilityStatus = findViewById(R.id.accessibilityStatus)
        openAccessibility = findViewById(R.id.openAccessibility)
        shizukuStatus = findViewById(R.id.shizukuStatus)
        shizukuAction = findViewById(R.id.shizukuAction)
        showBubble = findViewById(R.id.showBubble)
        tapMode = findViewById(R.id.tapMode)

        openAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.openAppInfo).setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
        shizukuAction.setOnClickListener { onShizukuAction() }

        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val running = BubbleService.instance != null
        accessibilityStatus.setText(if (running) R.string.accessibility_on else R.string.accessibility_off)

        when (PowerActions.shizukuState()) {
            ShizukuState.NOT_RUNNING -> {
                shizukuStatus.setText(R.string.shizuku_not_running)
                shizukuAction.setText(if (shizukuInstalled()) R.string.open_shizuku else R.string.get_shizuku)
                shizukuAction.isEnabled = true
            }
            ShizukuState.NEEDS_PERMISSION -> {
                shizukuStatus.setText(R.string.shizuku_needs_permission)
                shizukuAction.setText(R.string.grant_shizuku)
                shizukuAction.isEnabled = true
            }
            ShizukuState.READY -> {
                shizukuStatus.setText(R.string.shizuku_ready)
                shizukuAction.setText(R.string.grant_shizuku)
                shizukuAction.isEnabled = false
            }
        }

        showBubble.setOnCheckedChangeListener(null)
        showBubble.isChecked = prefs.visible
        showBubble.setOnCheckedChangeListener { _, checked ->
            prefs.visible = checked
            BubbleService.instance?.setBubbleVisible(checked)
            BubbleTileService.refresh(this)
        }

        tapMode.setOnCheckedChangeListener(null)
        tapMode.check(if (prefs.tapMode == TapMode.MENU) R.id.tapMenu else R.id.tapScreenOff)
        tapMode.setOnCheckedChangeListener { _, id ->
            prefs.tapMode = if (id == R.id.tapMenu) TapMode.MENU else TapMode.SCREEN_OFF
            BubbleService.instance?.onSettingsChanged()
        }
    }

    private fun onShizukuAction() {
        when (PowerActions.shizukuState()) {
            ShizukuState.NOT_RUNNING -> {
                val launch = packageManager.getLaunchIntentForPackage(PowerActions.SHIZUKU_PACKAGE)
                startActivity(launch ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
            }
            ShizukuState.NEEDS_PERMISSION -> Shizuku.requestPermission(REQUEST_SHIZUKU)
            ShizukuState.READY -> Unit
        }
    }

    private fun shizukuInstalled() = packageManager.getLaunchIntentForPackage(PowerActions.SHIZUKU_PACKAGE) != null

    private companion object {
        const val REQUEST_SHIZUKU = 1
    }
}
