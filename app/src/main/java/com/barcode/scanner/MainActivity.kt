package com.barcode.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.barcode.scanner.databinding.ActivityMainBinding
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@ExperimentalGetImage
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var isScanned = false
    var imageWidth = 0
    var imageHeight  = 0
    private var lastAnalyzedTime = 0L
    private var isProcessing = false



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        cameraExecutor = Executors.newSingleThreadExecutor()
        setContentView(binding.root)
        initCamera()
    }

    private fun initCamera() {
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                101
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        binding.previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val barcodeScanner = BarcodeScanning.getClient()

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                imageWidth = imageProxy.width
                imageHeight = imageProxy.height
                processImageProxy(barcodeScanner, imageProxy)
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )

        }, ContextCompat.getMainExecutor(this))
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun processImageProxy(
        scanner: BarcodeScanner,
        imageProxy: ImageProxy
    ) {
        val currentTime = System.currentTimeMillis()

        if (isScanned) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        isProcessing = true
        lastAnalyzedTime = currentTime

        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        scanner.process(image)
            .addOnSuccessListener { barcodes ->

                val overlay = findViewById<ScannerOverlayView>(R.id.overlay)
                val scanRect = overlay.getScanRect() ?: return@addOnSuccessListener

                for (barcode in barcodes) {
                    val boundingBox = barcode.boundingBox ?: continue
                    val mappedRect = mapToPreviewView(boundingBox, imageProxy)

                    if (isInsideOverlay(mappedRect, scanRect)) {
                        isScanned = true

                        runOnUiThread {
                            binding.tvBarcode.text = barcode.rawValue ?: ""
                        }
                        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                        vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                        // Reset scanner after delay
                        Handler(Looper.getMainLooper()).postDelayed({
                            isScanned = false
                        }, 1000)
                    }
                }
            }
            .addOnFailureListener {
                // optional logging
            }
            .addOnCompleteListener {
                isProcessing = false
                imageProxy.close()
            }
    }

    private fun mapToPreviewView(rect: Rect, imageProxy: ImageProxy): RectF {
        val previewView = binding.previewView

        val rotation = imageProxy.imageInfo.rotationDegrees

        val imageWidth: Int
        val imageHeight: Int

        if (rotation == 90 || rotation == 270) {
            imageWidth = imageProxy.height
            imageHeight = imageProxy.width
        } else {
            imageWidth = imageProxy.width
            imageHeight = imageProxy.height
        }

        val scaleX = previewView.width.toFloat() / imageWidth
        val scaleY = previewView.height.toFloat() / imageHeight

        return RectF(
            rect.left * scaleX,
            rect.top * scaleY,
            rect.right * scaleX,
            rect.bottom * scaleY
        )
    }

    private fun isInsideOverlay(barcodeRect: RectF, overlayRect: RectF): Boolean {
        val margin = 20f
        Log.d("SCAN_DEBUG", "Barcode: $barcodeRect Overlay: $overlayRect")
        return overlayRect.left <= barcodeRect.left + margin &&
                overlayRect.top <= barcodeRect.top + margin &&
                overlayRect.right >= barcodeRect.right - margin &&
                overlayRect.bottom >= barcodeRect.bottom - margin
    }

    private fun allPermissionsGranted() =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}