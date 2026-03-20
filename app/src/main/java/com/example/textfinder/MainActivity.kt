package com.example.textfinder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.textfinder.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var textRecognizer: TextRecognizer

    // Flag to avoid processing frames when a previous one is still running
    private val isProcessing = AtomicBoolean(false)

    // Current search query
    @Volatile
    private var searchQuery: String = ""

    companion object {
        private const val TAG = "TextFinderApp"
    }

    // Permission launcher
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "需要摄像头权限才能使用此功能 / Camera permission is required",
                    Toast.LENGTH_LONG
                ).show()
                binding.tvStatus.text = "❌ 未授予摄像头权限"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize ML Kit text recognizer with Chinese support
        textRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

        // Initialize camera executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        setupSearchBar()
        checkCameraPermission()
    }

    private fun setupSearchBar() {
        binding.etSearchText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                searchQuery = query

                // Show/hide clear button
                binding.btnClear.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE

                // Clear overlay when query is empty
                if (query.isEmpty()) {
                    binding.overlayView.clearBoxes()
                    binding.tvStatus.text = ""
                }
            }
        })

        binding.btnClear.setOnClickListener {
            binding.etSearchText.setText("")
            binding.overlayView.clearBoxes()
            binding.tvStatus.text = ""
        }
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                Toast.makeText(
                    this,
                    "此应用需要摄像头权限来识别文字 / This app needs camera permission for text recognition",
                    Toast.LENGTH_LONG
                ).show()
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Build Preview use case
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            // Build ImageAnalysis use case
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                processImageProxy(imageProxy)
            }

            // Select back camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind all use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

                Log.d(TAG, "Camera started successfully")

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                runOnUiThread {
                    binding.tvStatus.text = "❌ 摄像头启动失败"
                }
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.camera.core.ExperimentalGetImage
    private fun processImageProxy(imageProxy: ImageProxy) {
        // Skip if already processing a frame
        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            isProcessing.set(false)
            return
        }

        val currentQuery = searchQuery
        if (currentQuery.isEmpty()) {
            // No query — clear overlay and skip OCR
            runOnUiThread {
                binding.overlayView.clearBoxes()
                binding.tvStatus.text = ""
            }
            imageProxy.close()
            isProcessing.set(false)
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        // Get image dimensions (after rotation)
        val imageWidth: Int
        val imageHeight: Int
        if (rotationDegrees == 90 || rotationDegrees == 270) {
            imageWidth = imageProxy.height
            imageHeight = imageProxy.width
        } else {
            imageWidth = imageProxy.width
            imageHeight = imageProxy.height
        }

        textRecognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val query = searchQuery  // re-read in case it changed
                if (query.isEmpty()) {
                    runOnUiThread {
                        binding.overlayView.clearBoxes()
                        binding.tvStatus.text = ""
                    }
                    return@addOnSuccessListener
                }

                // Collect all bounding boxes that match the query
                val matchingBoxes = mutableListOf<android.graphics.RectF>()

                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        // Check if the line text contains the query (case-insensitive)
                        if (line.text.contains(query, ignoreCase = true)) {
                            line.boundingBox?.let { rect ->
                                matchingBoxes.add(android.graphics.RectF(rect))
                            }
                        }
                        // Also check individual elements for finer granularity
                        for (element in line.elements) {
                            if (element.text.contains(query, ignoreCase = true)) {
                                element.boundingBox?.let { rect ->
                                    // Only add if not already covered by line box
                                    val elementRectF = android.graphics.RectF(rect)
                                    if (matchingBoxes.none { it.contains(elementRectF) }) {
                                        matchingBoxes.add(elementRectF)
                                    }
                                }
                            }
                        }
                    }
                }

                // Also check at block level for multi-line matches
                for (block in visionText.textBlocks) {
                    if (block.text.contains(query, ignoreCase = true)) {
                        // Check if any sub-element already matched; if not, add block box
                        val hasSubMatch = matchingBoxes.isNotEmpty()
                        if (!hasSubMatch) {
                            block.boundingBox?.let { rect ->
                                matchingBoxes.add(android.graphics.RectF(rect))
                            }
                        }
                    }
                }

                val matchCount = matchingBoxes.size
                runOnUiThread {
                    // Pass image dimensions so OverlayView can scale correctly
                    binding.overlayView.setResults(
                        boxes = matchingBoxes,
                        imageWidth = imageWidth,
                        imageHeight = imageHeight
                    )
                    binding.tvStatus.text = when {
                        matchCount == 0 -> "🔍 未找到 \"$query\""
                        matchCount == 1 -> "✅ 找到 1 处匹配"
                        else -> "✅ 找到 $matchCount 处匹配"
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Text recognition failed", e)
                runOnUiThread {
                    binding.tvStatus.text = "⚠️ 识别出错"
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
                isProcessing.set(false)
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        textRecognizer.close()
    }
}
