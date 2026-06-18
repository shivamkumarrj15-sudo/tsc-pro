package com.example.tscpro

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class AndroidBridge(private val context: Context) {

    @JavascriptInterface
    fun shareImage(base64Data: String, filename: String) {
        try {
            val file = saveBase64ToCache(base64Data, filename) ?: return
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share TSC Report"))
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Failed to share image: ${e.message}")
        }
    }

    @JavascriptInterface
    fun downloadImage(base64Data: String, filename: String) {
        try {
            val cleanBase64 = cleanBase64(base64Data)
            val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            val savedUri = saveImageToDownloads(bytes, filename)
            if (savedUri != null) {
                showToast("Image saved to Pictures/TSCPro: $filename")
            } else {
                showToast("Failed to save image")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Download failed: ${e.message}")
        }
    }

    @JavascriptInterface
    fun shareMultipleImages(jsonString: String) {
        try {
            val jsonArray = JSONArray(jsonString)
            val uris = ArrayList<Uri>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val base64Data = obj.getString("dataUrl")
                val filename = obj.getString("filename")
                val file = saveBase64ToCache(base64Data, filename)
                if (file != null) {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    uris.add(uri)
                }
            }

            if (uris.isNotEmpty()) {
                val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/png"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share All TSC Reports"))
            } else {
                showToast("No images generated to share")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Failed to share multiple images: ${e.message}")
        }
    }

    private fun cleanBase64(base64Data: String): String {
        return if (base64Data.contains(",")) {
            base64Data.substring(base64Data.indexOf(",") + 1)
        } else {
            base64Data
        }
    }

    private fun saveBase64ToCache(base64Data: String, filename: String): File? {
        return try {
            val cleanBase64 = cleanBase64(base64Data)
            val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            val cachePath = File(context.cacheDir, "shared_images")
            if (!cachePath.exists()) {
                cachePath.mkdirs()
            }
            val file = File(cachePath, filename)
            val stream = FileOutputStream(file)
            stream.write(bytes)
            stream.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun saveImageToDownloads(bytes: ByteArray, filename: String): Uri? {
        var outputStream: OutputStream? = null
        var uri: Uri? = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/TSCPro")
                }
                uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    outputStream = context.contentResolver.openOutputStream(uri)
                }
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val tscDir = File(picturesDir, "TSCPro")
                if (!tscDir.exists()) {
                    tscDir.mkdirs()
                }
                val file = File(tscDir, filename)
                outputStream = FileOutputStream(file)
                uri = Uri.fromFile(file)
            }
            
            if (outputStream != null) {
                outputStream.write(bytes)
                outputStream.flush()
                outputStream.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            uri = null
        } finally {
            outputStream?.close()
        }
        return uri
    }

    private fun showToast(message: String) {
        if (context is Activity) {
            context.runOnUiThread {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    @JavascriptInterface
    fun initiatePhonePePayment(amountInPaise: Long, customerMobile: String) {
        val activity = context as? Activity ?: return
        Thread {
            try {
                val txnId = "TXN_" + System.currentTimeMillis()
                val payload = JSONObject().apply {
                    put("merchantId", "PGTESTPAYUAT86")
                    put("merchantTransactionId", txnId)
                    put("merchantUserId", "USER_" + System.currentTimeMillis())
                    put("amount", amountInPaise)
                    put("redirectUrl", "https://tscpro.payment/status")
                    put("redirectMode", "REDIRECT")
                    put("callbackUrl", "https://tscpro.payment/callback")
                    put("mobileNumber", customerMobile)
                    put("paymentInstrument", JSONObject().apply {
                        put("type", "PAY_PAGE")
                    })
                }

                val base64Payload = Base64.encodeToString(payload.toString().toByteArray(), Base64.NO_WRAP)
                val saltKey = "96434309-7796-489d-8924-ab56988a6076"
                val saltIndex = "1"
                val signatureStr = base64Payload + "/pg/v1/pay" + saltKey
                
                val bytes = MessageDigest.getInstance("SHA-256").digest(signatureStr.toByteArray())
                val checksumHex = bytes.joinToString("") { "%02x".format(it) }
                val checksum = checksumHex + "###" + saltIndex

                val requestObj = JSONObject().apply {
                    put("request", base64Payload)
                }

                val url = URL("https://api-preprod.phonepe.com/apis/pg-sandbox/pg/v1/pay")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("X-VERIFY", checksum)
                conn.doOutput = true

                conn.outputStream.use { os ->
                    os.write(requestObj.toString().toByteArray())
                    os.flush()
                }

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()

                    val resObj = JSONObject(response.toString())
                    val code = resObj.optString("code")
                    if (code == "SUCCESS") {
                        val dataObj = resObj.optJSONObject("data")
                        val instrumentResponse = dataObj?.optJSONObject("instrumentResponse")
                        val redirectInfo = instrumentResponse?.optJSONObject("redirectInfo")
                        val redirectUrl = redirectInfo?.optString("url")

                        if (!redirectUrl.isNullOrEmpty()) {
                            activity.runOnUiThread {
                                if (activity is MainActivity) {
                                    activity.loadPaymentUrl(redirectUrl)
                                }
                            }
                        } else {
                            showToast("Error: No redirect URL returned")
                        }
                    } else {
                        val msg = resObj.optString("message", "Initiation failed")
                        showToast("Failed: $msg")
                    }
                } else {
                    val reader = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()
                    showToast("Server Error: $responseCode - $response")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Network Error: ${e.message}")
            }
        }.start()
    }
}
