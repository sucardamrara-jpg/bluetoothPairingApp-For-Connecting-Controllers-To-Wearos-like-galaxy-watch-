package com.example.wearbtpair

import android.app.Activity
import android.bluetooth.*
import android.content.*
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.core.app.ActivityCompat
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.lang.reflect.Method

class MainActivity : Activity() {

    private lateinit var bt: BluetoothAdapter
    private lateinit var list: ListView
    private lateinit var logView: TextView

    private val devices = mutableListOf<BluetoothDevice>()
    private val adapter by lazy {
        ArrayAdapter<String>(this, android.R.layout.simple_list_item_1)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bt = BluetoothAdapter.getDefaultAdapter()
        requestPerms()
        setContentView(buildUI())

        list.adapter = adapter
        list.setOnItemClickListener { _, _, i, _ -> pair(devices[i]) }
        list.setOnItemLongClickListener { _, _, i, _ -> unpair(devices[i]); true }

        registerReceiver(rx, IntentFilter(BluetoothDevice.ACTION_FOUND))
        registerReceiver(rx, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
    }

    // ================= WEAR UI =================

    private fun buildUI(): ScrollView {
        val padding = 28

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(padding, padding, padding, padding)
        }

        fun wearBtn(text: String, action: () -> Unit) =
            Button(this).apply {
                this.text = text
                textSize = 15f
                setOnClickListener { action() }
            }

        root.addView(wearBtn("🔍 Scan Devices") { scan() })
        root.addView(wearBtn("📂 Paired Devices") { paired() })
        root.addView(wearBtn("🧪 Experimental Pair") { experimental() })
        root.addView(wearBtn("📺 Android TV Pair Trick") { androidTvTrick() })
        root.addView(wearBtn("📊 Dump Bluetooth Stack") { dumpBtStack() })
        root.addView(wearBtn("⚙ Bluetooth Settings") { openBtSettings() })
        root.addView(wearBtn("📤 Export Logs (ADB)") { exportLogs() })
        root.addView(wearBtn("🧹 Clear Logs") { logView.text = "" })

        list = ListView(this)
        logView = TextView(this).apply {
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
        }

        root.addView(list, LinearLayout.LayoutParams.MATCH_PARENT, 420)
        root.addView(logView)

        return ScrollView(this).apply { addView(root) }
    }

    // ================= PERMISSIONS =================

    private fun requestPerms() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ),
            0
        )
    }

    // ================= LOG =================

    private fun log(m: String) {
        logView.append("$m\n")
    }

    // ================= BLUETOOTH =================

    private fun scan() {
        devices.clear()
        adapter.clear()
        log("Scanning...")
        bt.startDiscovery()
    }

    private fun paired() {
        devices.clear()
        adapter.clear()
        bt.bondedDevices.forEach {
            devices.add(it)
            adapter.add(format(it))
        }
        log("Loaded paired devices")
    }

    private fun pair(d: BluetoothDevice) {
        log("Pairing: ${d.name}")
        log("Type:${d.type} Bond:${d.bondState}")
        log("Class:${d.bluetoothClass}")
        log("HID:${hidType(d)}")
        log("KeyboardMode:${keyboardLike(d)}")
        log("Blacklist:${blacklisted(d)}")

        try {
            log("createBond() -> ${d.createBond()}")
        } catch (e: Exception) {
            log("Pair error: ${e.message}")
        }
    }

    // ================= UNPAIR =================

    private fun unpair(d: BluetoothDevice) {
        log("Unpairing: ${d.name}")
        try {
            val m: Method = d.javaClass.getMethod("removeBond")
            val result = m.invoke(d) as Boolean
            log("removeBond() -> $result")
        } catch (e: Exception) {
            log("Unpair failed: ${e.message}")
        }
    }

    // ================= EXPERIMENTAL =================

    private fun experimental() {
        log("Experimental pairing attempt")
        try {
            sendBroadcast(Intent("android.bluetooth.device.action.PAIRING_REQUEST"))
            log("PAIRING_REQUEST broadcast sent")
        } catch (_: Exception) {}
        bt.startDiscovery()
    }

    private fun androidTvTrick() {
        log("Android TV pairing intent sent")
        try {
            startActivity(
                Intent("android.settings.BLUETOOTH_SETTINGS")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            log("TV trick blocked")
        }
    }

    // ================= STACK DUMP =================

    private fun dumpBtStack() {
        log("Dumping bluetooth stack...")
        try {
            val p = Runtime.getRuntime().exec("dumpsys bluetooth_manager")
            val r = BufferedReader(InputStreamReader(p.inputStream))
            var line: String?
            while (r.readLine().also { line = it } != null) {
                log(line!!)
            }
        } catch (e: Exception) {
            log("Dump failed")
        }
    }

    // ================= RECEIVER =================

    private val rx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val d =
                        i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                            ?: return
                    val rssi = i.getShortExtra(BluetoothDevice.EXTRA_RSSI, 0)
                    if (!devices.contains(d)) {
                        devices.add(d)
                        adapter.add(format(d, rssi))
                        log("Found: ${d.name} RSSI:$rssi")
                    }
                }

                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val d =
                        i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                            ?: return
                    log("Bond state: ${d.name} -> ${d.bondState}")
                }
            }
        }
    }

    // ================= ANALYSIS =================

    private fun blacklisted(d: BluetoothDevice): String {
        val n = d.name?.lowercase() ?: return "Unknown"
        return when {
            "xbox" in n -> "Xbox (blocked)"
            "dualshock" in n || "dualsense" in n || "ps" in n -> "PlayStation (blocked)"
            "switch" in n || "pro controller" in n -> "Switch (blocked)"
            else -> "No"
        }
    }

    private fun hidType(d: BluetoothDevice): String =
        when (d.bluetoothClass?.majorDeviceClass) {
            BluetoothClass.Device.Major.PERIPHERAL -> "HID"
            else -> "Non-HID"
        }

    private fun keyboardLike(d: BluetoothDevice): Boolean =
        d.bluetoothClass?.deviceClass == BluetoothClass.Device.PERIPHERAL_KEYBOARD

    private fun format(d: BluetoothDevice, rssi: Short? = null): String =
        """
        ${d.name ?: "Unknown"}
        ${d.address}
        Bond:${d.bondState} Type:${d.type}
        HID:${hidType(d)} Keyboard:${keyboardLike(d)}
        RSSI:${rssi ?: "?"}
        ${blacklisted(d)}
        """.trimIndent()

    // ================= EXPORT =================

    private fun exportLogs() {
        val f = File(filesDir, "bt_logs.txt")
        f.writeText(logView.text.toString())
        log("Saved: ${f.absolutePath}")
        log("adb pull ${f.absolutePath}")
    }

    // ================= SETTINGS =================

    private fun openBtSettings() {
        try {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        } catch (_: Exception) {
            log("Settings blocked")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(rx)
        bt.cancelDiscovery()
    }
}
