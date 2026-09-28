package com.def.musicplayer.data

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.mpatric.mp3agic.ID3v24Tag
import com.mpatric.mp3agic.Mp3File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed class PrepareCoverResult {
    data class Ready(val targetUri: Uri, val preparedFile: File) : PrepareCoverResult()
    data class Failed(val reason: String) : PrepareCoverResult()
}

sealed class WriteCoverResult {
    data object Success : WriteCoverResult()
    data class NeedsConsent(val intentSender: IntentSender) : WriteCoverResult()
    data class Failed(val reason: String) : WriteCoverResult()
}

/**
 * Записує нову обкладинку напряму в ID3v2-тег MP3-файлу (APIC-кадр), а не просто
 * зберігає картинку окремо — тому вона потім показується в будь-якому іншому плеєрі теж.
 *
 * Розбито на два кроки навмисно: підготовка (тегування у тимчасовий файл) і запис назад
 * у MediaStore. Це потрібно тому, що на Android 10+ система може попросити підтвердження
 * користувача на запис (як і з видаленням) — і тоді треба повторити ЛИШЕ запис, не
 * перегенеровуючи тег заново, а раніше готовий тимчасовий файл видалявся одразу,
 * тому повторити запис після підтвердження було вже нічим.
 *
 * Наразі підтримує лише MP3 (через mp3agic) — FLAC/M4A/OGG залишено на майбутнє.
 */
object CoverArtHelper {

    suspend fun prepareCoverEdit(
        context: Context,
        songId: Long,
        newImageUri: Uri
    ): PrepareCoverResult = withContext(Dispatchers.IO) {
        val songUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)
        val mimeType = context.contentResolver.getType(songUri) ?: ""
        if (!mimeType.contains("mpeg") && !mimeType.contains("mp3")) {
            return@withContext PrepareCoverResult.Failed(
                "Наразі підтримується редагування обкладинок лише для MP3-файлів"
            )
        }

        val tempInput = File(context.cacheDir, "cover_edit_in_$songId.mp3")
        try {
            context.contentResolver.openInputStream(songUri)?.use { input ->
                tempInput.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext PrepareCoverResult.Failed("Не вдалося прочитати аудіофайл")

            val imageBytes = context.contentResolver.openInputStream(newImageUri)?.use { it.readBytes() }
                ?: return@withContext PrepareCoverResult.Failed("Не вдалося прочитати зображення")
            val imageMime = context.contentResolver.getType(newImageUri) ?: "image/jpeg"

            val mp3File = Mp3File(tempInput)
            val tag = if (mp3File.hasId3v2Tag()) {
                mp3File.id3v2Tag
            } else {
                ID3v24Tag().also { mp3File.id3v2Tag = it }
            }
            tag.setAlbumImage(imageBytes, imageMime)

            val tempOutput = File(context.cacheDir, "cover_edit_out_$songId.mp3")
            if (tempOutput.exists()) tempOutput.delete()
            mp3File.save(tempOutput.absolutePath)

            PrepareCoverResult.Ready(targetUri = songUri, preparedFile = tempOutput)
        } catch (e: Exception) {
            PrepareCoverResult.Failed(e.message ?: "Не вдалося обробити файл")
        } finally {
            tempInput.delete()
        }
    }

    /** Можна викликати повторно (після підтвердження дозволу користувачем). */
    fun writeBack(context: Context, targetUri: Uri, preparedFile: File): WriteCoverResult {
        return try {
            context.contentResolver.openOutputStream(targetUri, "wt")?.use { out ->
                preparedFile.inputStream().use { it.copyTo(out) }
            }
            WriteCoverResult.Success
        } catch (e: RecoverableSecurityException) {
            WriteCoverResult.NeedsConsent(e.userAction.actionIntent.intentSender)
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent = MediaStore.createWriteRequest(context.contentResolver, listOf(targetUri))
                WriteCoverResult.NeedsConsent(pendingIntent.intentSender)
            } else {
                WriteCoverResult.Failed("Немає дозволу на запис у цей файл")
            }
        } catch (e: Exception) {
            WriteCoverResult.Failed(e.message ?: "Не вдалося зберегти зміни")
        }
    }

    fun cleanup(preparedFile: File) {
        preparedFile.delete()
    }
}
