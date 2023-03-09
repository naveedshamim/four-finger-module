package com.jp.cameramodule


import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.CameraView
import androidx.core.content.ContextCompat
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
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt


class MainActivity : AppCompatActivity() {
    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }
//    lateinit var camerav: Camera

    lateinit var file: File
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    lateinit var cameraview: CameraView
    lateinit var cameraCharacteristics: CameraCharacteristics
    lateinit var cameraValues: MutableList<String>

    lateinit var streamConfigurationMap: StreamConfigurationMap
    lateinit var imageUri : Uri

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
//        Log.d("CAMVAL", "H: ${cameraValues[0]} \nW: ${cameraValues[1]}")
//        Log.d("CameraSize", cameraValues[0])

//        binding.buttonCaptureImage2.setOnClickListener {
//
//            startActivity(Intent(this, MainActivity::class.java))
//        }

    }

    private fun requestRuntimePermission() {
        Dexter.withContext(this)
            .withPermissions(Manifest.permission.CAMERA)
            .withListener(multiplePermissionsListener)
            .check()

    }

    private fun setupCameraProvider() {
        ProcessCameraProvider.getInstance(this).also { provider ->
            provider.addListener({
                bindPreview(provider.get())
            }, ContextCompat.getMainExecutor(this))
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val preview: Preview = Preview.Builder()
            .build()

        imageCapture = ImageCapture.Builder()
            .build()

        val cameraSelector: CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        val useCaseGroup = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(imageCapture!!)
            .build()


        camera = cameraProvider.bindToLifecycle(this, cameraSelector, useCaseGroup)

        val cameraControl = camera!!.cameraControl
        cameraControl.enableTorch(true) // enable torch

        cameraview = CameraView(this)
        camera?.let {
            preview.setSurfaceProvider(binding.previewView.surfaceProvider)
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

//        val httpClient = OkHttpClient.Builder()
//        httpClient.addInterceptor(HttpLoggingInterceptor().apply {
//            level = HttpLoggingInterceptor.Level.BODY
//        })

//        val retrofit = Retrofit.Builder().baseUrl("http://192.168.18.96:5000/")
//            .addConverterFactory(GsonConverterFactory.create())
//            .client(httpClient.build())
//            .build()
//            .create(UploadService::class.java)

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

