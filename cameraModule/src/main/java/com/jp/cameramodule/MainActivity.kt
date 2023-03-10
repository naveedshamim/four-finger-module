package com.jp.cameramodule


import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.StreamConfigurationMap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony.Mms.Part.FILENAME
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.CameraView
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.jp.cameramodule.databinding.ActivityMainBinding
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import retrofit2.Retrofit
import java.io.File
import java.io.FileOutputStream
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.file.Files.createFile
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
typealias LumaListener = (luma: Double) -> Unit


class MainActivity : AppCompatActivity() {

    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    lateinit var file: File
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    lateinit var cameraCharacteristics: CameraCharacteristics
    lateinit var cameraValues: MutableList<String>

    lateinit var streamConfigurationMap: StreamConfigurationMap
    lateinit var imageUri : Uri
    private lateinit var cameraExecutor: ExecutorService

    private lateinit var imageAnalysis: ImageAnalysis

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var outputFile: File
    private lateinit var refocusDetector: RefocusDetector




    @SuppressLint("UnsafeOptInUsageError")
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        var a = binding.previewView.viewPort
        binding.buttonCaptureImage.setOnClickListener { onCaptureImage() }
        requestRuntimePermission()
        var cameraManager = this.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraCharacteristics =
            cameraManager.getCameraCharacteristics(cameraManager.cameraIdList[0])
        streamConfigurationMap =
            cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        var size: Array<Size> = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG)
        cameraValues = size[0].toString().split("x") as MutableList<String>


        outputFile = File(externalMediaDirs.first(), "${System.currentTimeMillis()}.jpg")

        // Initialize camera executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Initialize refocus detector
        refocusDetector = RefocusDetector()

        // Initialize image capture and analysis use cases
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(windowManager.defaultDisplay.rotation)
            .build()

        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()




    }

    private fun requestRuntimePermission() {
        Dexter.withContext(this)
            .withPermissions(Manifest.permission.CAMERA)
            .withListener(multiplePermissionsListener)
            .check()

    }

    private fun setupCameraProvider() {
//        ProcessCameraProvider.getInstance(this).also { provider ->
//            provider.addListener({
                bindPreview()
//            }, ContextCompat.getMainExecutor(this))
//        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindPreview() {

            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

            cameraProviderFuture.addListener({
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(binding.previewView.surfaceProvider)
                    }

                imageCapture = ImageCapture.Builder()
                    .build()

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor, { image ->
                    // Process the image to detect refocus
                    if (refocusDetector.detectRefocus(image)) {


                    }
                    image.close()
                })

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture, imageAnalysis
                    )

                } catch (exc: Exception) {
                    Log.e("TAG", "Use case binding failed", exc)
                }

            }, ContextCompat.getMainExecutor(this))


    }

    private fun saveImage() {

        // Trigger image capture
//        imageCapture?.takePicture(outputFile, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
//            override fun onImageSaved(outputFile: File) {
//                Log.d("TAG", "Image saved: ${outputFile.absolutePath}")
//                runOnUiThread {
//                    Toast.makeText(applicationContext, "Image saved to ${outputFile.absolutePath}", Toast.LENGTH_SHORT).show()
//                }
//            }
//
//            override fun onError(error: ImageCaptureException) {
//                Log.e("TAG", "Error saving image", error)
//                runOnUiThread {
//                    Toast.makeText(applicationContext, "Error saving image", Toast.LENGTH_SHORT).show()
//                }
//            }
//        })

    }


    private class FocusDetectionAnalyzer(private val onFocused: (Boolean) -> Unit) : ImageAnalysis.Analyzer {
        private var lastFocused = false
        private var focusCount = 0

        override fun analyze(image: ImageProxy) {
            // Compute image sharpness using OpenCV or any other library
            // Here we use a simple edge detection algorithm to determine sharpness
            val sharpness = computeSharpness(image)

            // Update focus state based on image sharpness
            val isFocused = sharpness >= SHARPNESS_THRESHOLD
            if (isFocused != lastFocused) {
                lastFocused = isFocused
                focusCount = if (isFocused) focusCount + 1 else 0
                if (focusCount >= FOCUS_COUNT_THRESHOLD) {
                    onFocused(true)
                }
            }

            // Close image when done processing
            image.close()
        }

        private fun computeSharpness(image: ImageProxy): Double {
            // Get image buffer and properties
            val yBuffer = image.planes[0].buffer
            val ySize = image.cropRect.width() * image.cropRect.height()
            val yArray = ByteArray(ySize)
            yBuffer.get(yArray)

            // Compute image edges using Sobel operator
            val sobel = SobelOperator(yArray, image.cropRect.width(), image.cropRect.height())
            val edges = sobel.getEdges()

            // Compute edge density and sharpness
            val edgeDensity = edges.sum() / (image.cropRect.width() * image.cropRect.height()).toDouble()
            return edgeDensity * edgeDensity
        }

        private class SobelOperator(
            private val input: ByteArray,
            private val width: Int,
            private val height: Int
        ) {
            private val kernelX = intArrayOf(-1, 0, 1, -2, 0, 2, -1, 0, 1)
            private val kernelY = intArrayOf(-1, -2, -1, 0, 0, 0, 1, 2, 1)
            private val output = DoubleArray(width * height)

            fun getEdges(): DoubleArray {
                for (y in 1 until height - 1) {
                    for (x in 1 until width - 1) {
                        val pixelX = convolve(x, y, kernelX)
                        val pixelY = convolve(x, y, kernelY)
                        output[x + y * width] = Math.sqrt(pixelX * pixelX + pixelY * pixelY)
                    }
                }
                return output
            }

            private fun convolve(x: Int, y: Int, kernel: IntArray): Double {
                var result = 0.0
                for (i in kernel.indices) {
                    val pixel = input[(x - 1 + (y - 1) * width) + i]
                    result += pixel * kernel[i]
                }
                return result
            }
        }

        companion object {
            private const val SHARPNESS_THRESHOLD = 0.1
            private const val FOCUS_COUNT_THRESHOLD = 1
        }
    }


    private fun onCaptureImage() {
        file = File(filesDir.absoluteFile, "unfair_left.jpg")
        val outputFileOptions: ImageCapture.OutputFileOptions =
            ImageCapture.OutputFileOptions.Builder(file).build()
        imageCapture?.takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(this),
            imageSavedCallback
        )
    }

    private val imageSavedCallback = object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
            //  binding.imageView.visibility = View.VISIBLE
            //  binding.previewView.visibility = View.GONE

            //    Log.e("filepath" , file.path)
            imageUri = outputFileResults.savedUri ?: Uri.fromFile(file)
            //  imageUri = file.absoluteFile

            upload()

//           val bitmap = BitmapFactory.decodeFile(file.path)

//            cropImage(bitmap)
//            Glide
//                .with(this@MainActivity)
//               // .load(cropImage(bitmap))
//                .load(bitmap)
//                .centerCrop()
//                .into(binding.imageView)
//            binding.buttonCaptureImage2.visibility = View.VISIBLE
        }

        override fun onError(exception: ImageCaptureException) {
            showResultMessage(
                getString(
                    R.string.image_capture_error,
                    exception.message,
                    exception.imageCaptureError
                )
            )
        }
    }

    private val multiplePermissionsListener = object : ShortenMultiplePermissionListener() {
        override fun onPermissionsChecked(report: MultiplePermissionsReport) {
            if (report.areAllPermissionsGranted()) {
                onPermissionGrant()
            } else {
                onPermissionDenied()
            }
        }
    }

    private fun onPermissionGrant() {
        setupCameraProvider()
    }

    private fun onPermissionDenied() {
        showResultMessage(getString(R.string.permission_denied))
        finish()
    }

    private fun showResultMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun cropImage(resultBitmap: Bitmap): Bitmap? {
        val koefX = resultBitmap.width.toFloat() / cameraValues[1].toInt()
        val koefY = resultBitmap.height.toFloat() / cameraValues[0].toInt()

        val x1: Int = binding.previewView.left
        val y1: Int = binding.previewView.top

        val x2: Int = binding.previewView.width
        val y2: Int = binding.previewView.height
        val cropStartX = (x1 * koefX).roundToInt()
        val cropStartY = (y1 * koefY).roundToInt()

        val cropWidthX = (x2 * koefX).roundToInt()
        val cropHeightY = (y2 * koefY).roundToInt()

        return if (resultBitmap.width >= cropStartX + cropWidthX && resultBitmap.height >= cropStartY + cropHeightY) {
            Bitmap.createBitmap(
                resultBitmap,
                cropStartX,
                cropStartY,
                cropWidthX,
                cropHeightY
            )
        } else {
            null
        }
    }

    private fun upload(){
        val fileDir = applicationContext.filesDir
        val file = File(fileDir , "image.jpg")
        val inputStream = contentResolver.openInputStream(imageUri)
        val outStream = FileOutputStream(file)
        inputStream!!.copyTo(outStream)

        val requestBody = file.asRequestBody("image/*".toMediaTypeOrNull())

        val part = MultipartBody.Part.createFormData("file" ,file.name ,requestBody)

        val hand: RequestBody = RequestBody.create("multipart/form-data".toMediaTypeOrNull(), "left")

        val httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        // Create Retrofit
        val retrofit = Retrofit.Builder()
            .client(httpClient)
            .baseUrl("http://192.168.18.96:5000/")
            .build()

        // Create Service
        val service = retrofit.create(UploadService::class.java)

        CoroutineScope(Dispatchers.IO).launch {
            // Do the POST request and get response

            try {

                val response = service.uploadImage(part , hand)

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {

                        // Convert raw JSON to pretty JSON using GSON library
                        val gson = GsonBuilder().setPrettyPrinting().create()
                        val prettyJson = gson.toJson(
                            JsonParser.parseString(
                                response.body()
                                    ?.string()
                            )
                        )

                        val jsonArray = JSONArray(prettyJson)

                        for (i in 0 until jsonArray.length()) {
                            val imageUrl = jsonArray.getString(i)
                            //   val item = prettyJson[0]
                            Log.d("PrettyJSON" , imageUrl)
                        }

                    } else {

                        Log.e("PrettyJSON", response.code().toString())
                    }
                }

            }catch (e: SocketTimeoutException){
                Log.d("PrettyJSON" , e.toString())
            }
        }
    }

}

