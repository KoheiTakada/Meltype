// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ninjin0802.keyboard.app.clipboard

import android.content.Context
import android.net.Uri
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions

/** The actual text is processed locally. Only translation model downloads use the network. */
object ClipboardProcessing {
    fun translate(text: String, complete: (Result<String>) -> Unit) {
        val detector = LanguageIdentification.getClient()
        detector.identifyLanguage(text)
            .addOnSuccessListener { language ->
                detector.close()
                val source = TranslateLanguage.fromLanguageTag(language)
                if (source == null) {
                    complete(Result.failure(IllegalArgumentException("Unsupported language")))
                    return@addOnSuccessListener
                }
                val target = if (source == TranslateLanguage.JAPANESE)
                    TranslateLanguage.ENGLISH else TranslateLanguage.JAPANESE
                val translator = Translation.getClient(
                    TranslatorOptions.Builder()
                        .setSourceLanguage(source)
                        .setTargetLanguage(target)
                        .build()
                )
                translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
                    .onSuccessTask { translator.translate(text) }
                    .addOnSuccessListener {
                        translator.close()
                        complete(Result.success(it))
                    }
                    .addOnFailureListener {
                        translator.close()
                        complete(Result.failure(it))
                    }
            }
            .addOnFailureListener {
                detector.close()
                complete(Result.failure(it))
            }
    }

    fun ocr(context: Context, clip: HistoryClip, complete: (Result<String>) -> Unit) {
        val file = ClipboardHistoryStore(context).mediaFile(clip)
        if (file == null) {
            complete(Result.failure(IllegalStateException("Missing image")))
            return
        }
        val input = try {
            InputImage.fromFilePath(context, Uri.fromFile(file))
        } catch (error: Exception) {
            complete(Result.failure(error))
            return
        }
        val recognizer = TextRecognition.getClient(
            JapaneseTextRecognizerOptions.Builder().build()
        )
        recognizer.process(input)
            .addOnSuccessListener {
                recognizer.close()
                if (it.text.isBlank()) complete(Result.failure(IllegalArgumentException("No text found")))
                else complete(Result.success(it.text))
            }
            .addOnFailureListener {
                recognizer.close()
                complete(Result.failure(it))
            }
    }
}
