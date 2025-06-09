package com.example.gennino

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.example.gennino.ui.theme.GenNinoTheme
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.imagedescription.ImageDescriber
import com.google.mlkit.genai.imagedescription.ImageDescriberOptions
import com.google.mlkit.genai.imagedescription.ImageDescription
import com.google.mlkit.genai.imagedescription.ImageDescriptionRequest
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var options: ImageDescriberOptions
    private lateinit var imageDescriber: ImageDescriber


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        options = ImageDescriberOptions.builder(this).build()
        imageDescriber = ImageDescription.getClient(options)

        lifecycleScope.launch {
            prepareAndStartImageDescription(null)
        }

        enableEdgeToEdge()

        setContent {
            GenNinoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    GetImageFromGallery(modifier = Modifier.padding(innerPadding)) { uri ->
                        val data = loadBitmapFromUri(uri)
                        lifecycleScope.launch {
                            prepareAndStartImageDescription(bitmap = data)
                        }
                    }
                }
            }
        }
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream).also {
                inputStream?.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun prepareAndStartImageDescription(bitmap: Bitmap?) {
        val featureStatus = imageDescriber.checkFeatureStatus().await()

        when (featureStatus) {
            FeatureStatus.DOWNLOADABLE -> {
                imageDescriber.downloadFeature(object : DownloadCallback {
                    override fun onDownloadCompleted() {
                        startImageDescriptionRequest(bitmap, imageDescriber)
                    }

                    override fun onDownloadFailed(p0: GenAiException) {
                        Toast.makeText(
                            this@MainActivity,
                            "Download failed: ${p0.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    override fun onDownloadProgress(p0: Long) {
                        println(p0)
                    }

                    override fun onDownloadStarted(p0: Long) {
                        println(p0)
                    }
                })
            }
            FeatureStatus.AVAILABLE -> {
                startImageDescriptionRequest(bitmap, imageDescriber)
            }
            FeatureStatus.DOWNLOADING -> {
                // Handle the case where the feature is unavailable
                startImageDescriptionRequest(bitmap, imageDescriber)
            }
        }
    }


    private fun startImageDescriptionRequest(
        bitmap: Bitmap?,
        imageDescriber: ImageDescriber
    ) {
        if (bitmap == null)
            return

        // Create task request
        val imageDescriptionRequest = ImageDescriptionRequest
            .builder(bitmap)
            .build()

        imageDescriber.runInference(imageDescriptionRequest) { outputText ->
            // Append new output text to show in UI
            // This callback is called incrementally as the description
            // is generated
            println(outputText)
            Toast.makeText(
                this,
                "Image Description: $outputText",
                Toast.LENGTH_LONG
            ).show()
        }

        imageDescriber.close()

    }

}

@Composable
fun GetImageFromGallery(
    modifier: Modifier = Modifier,
    generateImageDescription: (uri: Uri) -> Unit,
) {

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val pickMedia =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                imageUri = uri
            }
        }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OpenImagePickerButton(modifier) {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        imageUri?.let {
            ImageLoader(uri = it)
            Button(
                onClick = { generateImageDescription(it) },
                modifier = modifier
            ) {
                Text(text = "Get Image Description")
            }
        }


    }
}

@Composable
fun ImageLoader(uri: Uri) {
    AsyncImage(
        model = uri,
        contentDescription = "Image from URI",
        modifier = Modifier.size(200.dp)
    )
}

@Composable
fun OpenImagePickerButton(modifier: Modifier, openImagePicker: () -> Unit) {
    Button(onClick = { openImagePicker() }, modifier = modifier) {
        Text(text = "Open Image Picker")
    }
}


@Preview
@Composable
fun DefaultPreview() {
    GenNinoTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            GetImageFromGallery(modifier = Modifier.padding(innerPadding)) {}
        }
    }
}
