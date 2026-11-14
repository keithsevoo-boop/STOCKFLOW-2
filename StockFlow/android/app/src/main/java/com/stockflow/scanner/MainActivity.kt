package com.stockflow.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val spp = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var socket: BluetoothSocket? = null
    private var output: java.io.OutputStream? = null
    private lateinit var status: TextView
    private lateinit var devicePicker: Spinner
    private lateinit var preview: PreviewView
    private val devices = mutableListOf<BluetoothDevice>()
    private val executor = Executors.newSingleThreadExecutor()
    private var lastValue = ""
    private var lastAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24); setBackgroundColor(0xFF0C1426.toInt()) }
        fun label(s: String, size: Float = 16f) = TextView(this).apply { text=s; textSize=size; setTextColor(0xFFF4F7FF.toInt()); setPadding(0, 8, 0, 8) }
        root.addView(label("◈ StockFlow Scanner", 25f))
        root.addView(label("Pair your phone with the StockFlow computer in Android Bluetooth settings. Select the PC below, then connect."))
        devicePicker = Spinner(this); root.addView(devicePicker)
        val connect = Button(this).apply { text = "Connect to StockFlow PC" }; root.addView(connect)
        status = label("Not connected. Bluetooth Classic / Serial Port Profile required."); root.addView(status)
        preview = PreviewView(this); root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        val manual = EditText(this).apply { hint="Enter barcode manually"; setSingleLine(true); setTextColor(-1); setHintTextColor(0xFF9AACC8.toInt()) }; root.addView(manual)
        val send = Button(this).apply { text="Send barcode" }; root.addView(send)
        setContentView(root)
        connect.setOnClickListener { ensureBluetoothPermission { if (devices.isEmpty()) loadPairedDevices() else connectBluetooth() } }
        send.setOnClickListener { sendBarcode(manual.text.toString()); manual.setText("") }
        manual.setOnEditorActionListener { _, _, _ -> send.performClick(); true }
        ensureCameraPermission { startCamera(); ensureBluetoothPermission { loadPairedDevices() } }
    }

    private fun ensureCameraPermission(done: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) done()
        else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 10)
    }
    private fun ensureBluetoothPermission(done: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 11)
        else done()
    }
    @SuppressLint("MissingPermission")
    private fun loadPairedDevices() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) { status.text="This phone has no Bluetooth adapter."; return }
        if (!adapter.isEnabled) { status.text="Turn Bluetooth on in Android settings."; return }
        devices.clear(); devices.addAll(adapter.bondedDevices ?: emptySet())
        devicePicker.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, devices.map { "${it.name ?: "Bluetooth device"} (${it.address})" })
        if (devices.isEmpty()) status.text="No paired devices. Pair this phone with DESKTOP-C2527B1 in Android Bluetooth settings first."
        else status.text="Choose DESKTOP-C2527B1, then tap Connect."
    }
    @SuppressLint("MissingPermission")
    private fun connectBluetooth() {
        if (devices.isEmpty() || devicePicker.selectedItemPosition !in devices.indices) return
        status.text="Connecting…"
        Thread {
            try {
                socket?.close()
                val s = devices[devicePicker.selectedItemPosition].createRfcommSocketToServiceRecord(spp)
                BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
                s.connect(); socket=s; output=s.outputStream
                runOnUiThread { status.text="Connected to ${s.remoteDevice.name}. Ready to scan." }
            } catch (e: Exception) { runOnUiThread { status.text="Connection failed: ${e.message}. Check PC Incoming COM port and pairing." } }
        }.start()
    }
    private fun sendBarcode(value: String) {
        val code=value.trim(); if(code.isEmpty()) return
        Thread { try { val out=output ?: throw IllegalStateException("Connect to the PC first"); out.write((code+"\r\n").toByteArray(Charsets.US_ASCII)); out.flush(); runOnUiThread { status.text="Sent: $code" } }
            catch(e: Exception) { runOnUiThread { status.text="Not sent: ${e.message}" } } }.start()
    }
    private fun startCamera() {
        val future=ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider=future.get()
            val previewUse=Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            val options=BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build()
            val scanner=BarcodeScanning.getClient(options)
            val analysis=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(executor) { imageProxy ->
                val media=imageProxy.image
                if(media==null){imageProxy.close();return@setAnalyzer}
                scanner.process(InputImage.fromMediaImage(media,imageProxy.imageInfo.rotationDegrees))
                    .addOnSuccessListener { list ->
                        val value=list.firstOrNull()?.rawValue ?: return@addOnSuccessListener
                        val now=System.currentTimeMillis()
                        if(value != lastValue || now-lastAt > 1800){lastValue=value;lastAt=now;sendBarcode(value)}
                    }.addOnCompleteListener { imageProxy.close() }
            }
            try { provider.unbindAll(); provider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,previewUse,analysis); status.text="Camera ready. Connect to PC, then scan a barcode." }
            catch(e:Exception){status.text="Camera error: ${e.message}"}
        }, ContextCompat.getMainExecutor(this))
    }
    override fun onRequestPermissionsResult(requestCode:Int, permissions:Array<out String>, grantResults:IntArray){ super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(grantResults.isNotEmpty() && grantResults[0]==PackageManager.PERMISSION_GRANTED){ if(requestCode==10) startCamera(); if(requestCode==11) loadPairedDevices() }
    }
    override fun onDestroy(){ super.onDestroy(); try{socket?.close()}catch(_:Exception){}; executor.shutdown() }
}
