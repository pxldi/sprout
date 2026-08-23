/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.today

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.sprout.core.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The seventh and sixty-sixth completion, said properly.
 *
 * A card in the list under the habit it belongs to, rather than a dialog over the top of it.
 * A dialog would need to remember having been dismissed — which is a stored flag, a migration and
 * a thing to forget when a habit is deleted — and interrupting somebody to congratulate them is
 * not this app's manner. The card is derived from the log like everything else here, so it simply
 * stops being true tomorrow. See [Milestone].
 *
 * Only [MilestoneArt] goes into the shared picture; the Share button is outside the recorded layer
 * so it cannot end up in the image of itself.
 */
@Composable
internal fun MilestoneCard(
    habitName: String,
    milestone: Milestone,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var sharing by remember { mutableStateOf(false) }
    val chooserTitle = stringResource(R.string.milestone_share_chooser)
    val failed = stringResource(R.string.milestone_share_failed)

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        MilestoneArt(
            habitName = habitName,
            milestone = milestone,
            // Records what it draws on the way past, so sharing is a read of a layer that is
            // already there rather than a second, off-screen rendering that could drift from it.
            modifier = Modifier.drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            },
        )
        TextButton(
            enabled = !sharing,
            onClick = {
                sharing = true
                scope.launch {
                    runCatching { share(context, layer.toImageBitmap(), chooserTitle) }
                        .onFailure { Toast.makeText(context, failed, Toast.LENGTH_SHORT).show() }
                    sharing = false
                }
            },
            modifier = Modifier.align(Alignment.End).padding(end = 8.dp, bottom = 4.dp),
        ) {
            Icon(imageVector = Icons.Outlined.IosShare, contentDescription = null)
            Text(
                text = stringResource(R.string.milestone_share),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/**
 * The part that becomes the picture.
 *
 * The count is rendered from [Milestone.completions], never from a string, for the same reason the
 * shine lines carry their numbers: a card that claims sixty-six has to have counted to sixty-six.
 * It draws its own background because a recorded layer keeps whatever transparency it was given,
 * and a transparent PNG dropped into a chat app is a picture of that app's wallpaper.
 */
@Composable
private fun MilestoneArt(habitName: String, milestone: Milestone, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = habitName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center,
        )
        Text(
            text = pluralStringResource(
                R.plurals.milestone_count,
                milestone.completions,
                milestone.completions,
            ),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = stringResource(milestone.bodyRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.app_wordmark),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = WORDMARK_ALPHA),
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/**
 * Hands the picture to whatever the user picks.
 *
 * No accompanying text: the image already says all of it, and several popular targets drop the
 * image when text is attached to it. Nothing is written anywhere until the button is pressed, so
 * a milestone nobody shares leaves no file behind.
 */
private suspend fun share(context: Context, image: ImageBitmap, chooserTitle: String) {
    val uri = withContext(Dispatchers.IO) { writePng(context, image) }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = PNG
        putExtra(Intent.EXTRA_STREAM, uri)
        // The receiving app is chosen after this flag is set, so the grant follows whoever is
        // picked; a FileProvider uri without it is a permission denial in the other app.
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, chooserTitle))
}

/**
 * One file, overwritten every time, in the cache.
 *
 * A cache directory because the picture is disposable — the milestone lives in the log, and this
 * is a rendering of it that can be made again. The system may delete it whenever it likes, and by
 * then the share has long since happened.
 */
private fun writePng(context: Context, image: ImageBitmap): Uri {
    val directory = File(context.cacheDir, SHARED_DIRECTORY).apply { mkdirs() }
    val file = File(directory, "milestone.png")
    // A layer captured on a modern device comes back as a HARDWARE bitmap, which has no pixels
    // in this process to compress. The copy is the readback.
    val source = image.asAndroidBitmap()
    val bitmap = if (source.config == Bitmap.Config.HARDWARE) {
        source.copy(Bitmap.Config.ARGB_8888, false) ?: source
    } else {
        source
    }
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
    // Matches the flavour's real application id, so the debug build addresses its own provider.
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private const val PNG = "image/png"

/** Ignored for PNG, which is lossless, but the parameter is not optional. */
private const val PNG_QUALITY = 100

/** Has to match the `cache-path` in the app module's `file_paths.xml`. */
private const val SHARED_DIRECTORY = "shared"

private const val WORDMARK_ALPHA = 0.6f
