package com.def.musicplayer.data

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore

/**
 * На Android 10+ система вимагає окремого підтвердження користувача для видалення
 * медіафайлів, які не належать застосунку (Scoped Storage). Це не помилка коду —
 * так влаштована ОС. Ми або видаляємо напряму (старі версії), або показуємо
 * системний діалог підтвердження через IntentSender.
 */
object DeleteHelper {

    fun requestDelete(
        context: Context,
        songId: Long,
        onDeletedImmediately: () -> Unit,
        onNeedsConsent: (IntentSender) -> Unit,
        onFailed: (Exception) -> Unit
    ) {
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
                onNeedsConsent(pendingIntent.intentSender)
            }
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                try {
                    context.contentResolver.delete(uri, null, null)
                    onDeletedImmediately()
                } catch (e: RecoverableSecurityException) {
                    onNeedsConsent(e.userAction.actionIntent.intentSender)
                } catch (e: Exception) {
                    onFailed(e)
                }
            }
            else -> {
                try {
                    context.contentResolver.delete(uri, null, null)
                    onDeletedImmediately()
                } catch (e: Exception) {
                    onFailed(e)
                }
            }
        }
    }
}
