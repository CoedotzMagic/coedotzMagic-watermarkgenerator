package id.coedotz.watermarkgenerator

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.github.chrisbanes.photoview.PhotoView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import gun0912.tedimagepicker.builder.TedImagePicker
import id.coedotz.watermarkgenerator.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val selectedImages = mutableListOf<Uri>()
    private val dataStore by lazy { applicationContext.appDataStore }

    companion object {
        const val REQUEST_APP_SETTINGS = 321
        private val Context.appDataStore by preferencesDataStore(name = "permissions")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.selectImagesButton.setOnClickListener {
            if (hasGalleryPermission()) {
                TedImagePicker.with(this)
                    .startMultiImage { uriList ->
                        if (uriList.isEmpty()) {
                            setSnackbar(getString(R.string.batal_memilih_gambar))
                        } else {
                            selectedImages.clear()
                            selectedImages.addAll(uriList)
                            applyWatermarkToImages()
                            displaySelectedImages()
                        }
                    }
            } else {
                requestGalleryPermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            displaySelectedImages()
        }

        displaySelectedImages()
    }

    private fun setSnackbar(msg: String) {
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
    }

    private fun displaySelectedImages() {
        binding.imagesContainer.removeAllViews()

        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "coedotzmagic watermark"
        )
        if (!directory.exists() || directory.listFiles()?.none { file -> file.name.contains("coedotzmagic-watermarked") } == true) {
            binding.swipeRefreshLayout.isRefreshing = false
            binding.welcome.visibility = View.VISIBLE
            binding.welcome.text = getString(R.string.welcome)
            return
        } else {
            binding.swipeRefreshLayout.isRefreshing = false
            binding.welcome.visibility = View.GONE
        }

        directory.listFiles()?.filter { file -> file.name.contains("coedotzmagic-watermarked") }
            ?.sortedByDescending { file -> file.lastModified() }?.forEach { file ->
                val imageView = ImageView(this@MainActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 6.dpToPx(), 0, 6.dpToPx())
                    }
                    setPadding(0, 6.dpToPx(), 0, 6.dpToPx())
                }
                binding.imagesContainer.addView(imageView)

                Glide.with(this@MainActivity)
                    .load(Uri.fromFile(file))
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .into(imageView)

                imageView.setOnClickListener {
                    showImagePreview(Uri.fromFile(file))
                }
            }
    }

    private fun showImagePreview(uri: Uri) {
        val inflater = LayoutInflater.from(this)
        val dialogView = inflater.inflate(R.layout.dialog_image_preview, null)
        val photoView: PhotoView = dialogView.findViewById(R.id.photo_view)
        photoView.setImageURI(uri)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.tinjau_gambar))
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.hapus_gambar)) { dialog, _ ->
                confirmationDelete(uri)
            }
            .show()
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
            setSnackbar(getString(R.string.gambar_tidak_ada))
            e.printStackTrace()
            null
        }
    }

    private fun addWatermark(original: Bitmap, watermark: String): Bitmap {
        val result = original.copy(original.config, true)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            color = Color.WHITE
            alpha = 150
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

    private fun confirmationDelete(uri: Uri) {
        MaterialAlertDialogBuilder(this)
            .setIcon(android.R.drawable.stat_sys_warning)
            .setTitle(getString(R.string.konfirmasi_hapus))
            .setMessage(getString(R.string.anda_yakin_ingin_menghapus_file))
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                deleteImage(uri)
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.batal)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun deleteImage(uri: Uri) {
        try {
            if (uri.scheme == "content") {
                contentResolver.delete(uri, null, null)
            } else if (uri.scheme == "file") {
                val file = File(uri.path!!)
                if (file.exists()) {
                    file.delete()
                }
            }
            selectedImages.remove(uri)
            displaySelectedImages()
            setSnackbar(getString(R.string.gambar_berhasil_dihapus))
        } catch (e: Exception) {
            e.printStackTrace()
            setSnackbar(getString(R.string.gambar_gagal_dihapus))
        }
    }

    fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private val requestGalleryPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                lifecycleScope.launch {
                    val denialCount = getDenialCountFromDataStore() + 1
                    saveDenialCountToDataStore(denialCount)
                    if (denialCount >= 3) {
                        val snackbar =
                            Snackbar.make(
                                this@MainActivity.findViewById(android.R.id.content),
                                "Izinkan Akses Galeri agar bisa melakukan Watermark.",
                                Snackbar.LENGTH_LONG
                            )
                        snackbar.setAction("IZINKAN AKSES") {
                            goToSettings()
                        }
                        snackbar.show()
                    }
                }
            }
        }

    private fun hasGalleryPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun getDenialCountFromDataStore(): Int {
        val preferencesKey = intPreferencesKey("denial_count")
        val dataStoreValue = dataStore.data.first()
        return dataStoreValue[preferencesKey] ?: 0
    }

    private suspend fun saveDenialCountToDataStore(count: Int) {
        val preferencesKey = intPreferencesKey("denial_count")
        dataStore.edit { preferences ->
            preferences[preferencesKey] = count
        }
    }

    fun goToSettings() {
        val myAppSettings = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${this.packageName}")
        )
        myAppSettings.addCategory(Intent.CATEGORY_DEFAULT)
        myAppSettings.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        this.startActivityForResult(myAppSettings, REQUEST_APP_SETTINGS)
    }
}