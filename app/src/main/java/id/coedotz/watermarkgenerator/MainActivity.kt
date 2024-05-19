package id.coedotz.watermarkgenerator

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import gun0912.tedimagepicker.builder.TedImagePicker
import id.coedotz.watermarkgenerator.databinding.ActivityMainBinding
import java.util.Calendar

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val selectedImages = mutableListOf<Uri>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.selectImagesButton.setOnClickListener {
            TedImagePicker.with(this)
                .startMultiImage { uriList ->
                    selectedImages.clear()
                    selectedImages.addAll(uriList)
                    displaySelectedImages()
                }
        }

        binding.applyWatermarkButton.setOnClickListener {
            applyWatermarkToImages()
        }
    }

    private fun setSnackbar(msg: String) {
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
    }

    private fun displaySelectedImages() {
        binding.imagesContainer.removeAllViews()
        selectedImages.forEach { uri ->
            val imageView = ImageView(this)
            imageView.setImageURI(uri)
            imageView.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            binding.imagesContainer.addView(imageView)
        }
    }

    private fun applyWatermarkToImages() {
        if (selectedImages.isEmpty()) {
            setSnackbar(getString(R.string.silahkan_pilih_gambar))
            return
        } else {
            val currentYearUsingCalendar = Calendar.getInstance().get(Calendar.YEAR)
            setSnackbar(getString(R.string.sedang_menempelkan_watermark))
            var success = true
            selectedImages.forEach { uri ->
                val bitmap = getBitmapFromUri(uri)
                success = if (bitmap != null) {
                    val watermarkedBitmap =
                        addWatermark(bitmap, "@meonkbycoedotz - $currentYearUsingCalendar")
                    success && saveBitmapToFile(watermarkedBitmap)
                } else {
                    Log.e("Watermark", "Failed to decode file: $uri")
                    setSnackbar(getString(R.string.gagal_untuk_membuat_gambar, uri))
                    false
                }
            }
            if (success) {
                setSnackbar(getString(R.string.semua_gambar_sudah_diberikan_watermark))
            } else {
                setSnackbar(getString(R.string.beberapa_gambar_gagal_di_proses))
            }
        }
    }

    private fun getBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            }
        } catch (e: Exception) {
            setSnackbar("Gambar tidak ada")
            e.printStackTrace()
            null
        }
    }

    private fun addWatermark(original: Bitmap, watermark: String): Bitmap {
        val result = original.copy(original.config, true)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            color = Color.WHITE
            alpha = 60
            textSize = 128f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // Mengukur lebar dari teks
        val textWidth = paint.measureText(watermark)

        // Mendapatkan FontMetrics dari teks
        val fontMetrics = paint.fontMetrics
        val textHeight = fontMetrics.descent - fontMetrics.ascent

        // Menghitung koordinat x dan y untuk menempelkan teks ke bawah tengah
        val x = (original.width - textWidth) / 2
        val y = original.height - textHeight - 20 // 20 piksel padding dari bawah

        // Mengambar teks kedalam canvas
        canvas.drawText(watermark, x, y, paint)
        return result
    }


    private fun saveBitmapToFile(bitmap: Bitmap): Boolean {
        return try {
            val contentValues = ContentValues().apply {
                put(
                    MediaStore.Images.Media.DISPLAY_NAME,
                    "coedotzmagic-watermarked_${System.currentTimeMillis()}.jpg"
                )
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/coedotzmagic watermark"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val uri =
                contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            if (uri != null) {
                contentResolver.openOutputStream(uri).use { out ->
                    if (out != null) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                        out.flush()
                    }
                }

                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, contentValues, null, null)

                Log.i("Watermark", "Saved watermarked file: $uri")
                true
            } else {
                Log.e("Watermark", "Failed to create new MediaStore record.")
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("Watermark", "Failed to save file", e)
            false
        }
    }
}