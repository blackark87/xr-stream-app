package blackark.app.vr.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import blackark.app.vr.R
import blackark.app.vr.dmm.DmmCatalogItem
import blackark.app.vr.dmm.DmmDownloadItem
import blackark.app.vr.dmm.DmmPage
import blackark.app.vr.dmm.DmmPreparedMedia
import blackark.app.vr.dmm.DmmRepository
import blackark.app.vr.ui.theme.CardBackground
import blackark.app.vr.ui.theme.CardBackgroundHover
import blackark.app.vr.ui.theme.DividerGray
import blackark.app.vr.ui.theme.NetflixRed
import blackark.app.vr.ui.theme.TextPrimary
import blackark.app.vr.ui.theme.TextTertiary
import coil3.compose.AsyncImage

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DmmPanel(
    repository: DmmRepository,
    onPlayReady: (DmmPreparedMedia) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by repository.state.collectAsStateWithLifecycle()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var currentPageUrl by rememberSaveable { mutableStateOf(state.activeUrl) }
    var mediaUrl by rememberSaveable { mutableStateOf("") }
    var canGoBack by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let(repository::importOffline) },
    )

    fun sync(view: WebView?) {
        currentPageUrl = view?.url ?: currentPageUrl
        canGoBack = view?.canGoBack() == true
    }

    LaunchedEffect(state.preparedMedia) {
        state.preparedMedia?.let { prepared ->
            onPlayReady(prepared)
            repository.consumePreparedMedia()
        }
    }

    LaunchedEffect(Unit) {
        if (state.signedInUserId != null && state.catalog.isEmpty()) {
            repository.showLibrary()
        }
    }

    LaunchedEffect(state.activeUrl) {
        if (state.activeUrl.isNotBlank() && webView?.url != state.activeUrl) {
            webView?.let { repository.loadWebPage(it, state.activeUrl) }
        }
    }

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
        sync(webView)
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.dmm_fanza),
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                    )
                    Text(
                        text = state.signedInUserId?.let {
                            stringResource(R.string.dmm_signed_in)
                        } ?: stringResource(R.string.dmm_signed_out),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                    )
                }
                IconButton(
                    enabled = canGoBack,
                    onClick = {
                        webView?.goBack()
                        sync(webView)
                    },
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
                IconButton(onClick = { webView?.reload() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.dmm_reload))
                }
                IconButton(
                    onClick = repository::showLibrary,
                ) {
                    Icon(Icons.Filled.Home, contentDescription = stringResource(R.string.dmm_library))
                }
                if (state.signedInUserId == null) {
                    IconButton(onClick = repository::startLogin) {
                        Icon(Icons.Filled.Login, contentDescription = stringResource(R.string.dmm_login))
                    }
                } else {
                    IconButton(onClick = repository::logout) {
                        Icon(Icons.Filled.Logout, contentDescription = stringResource(R.string.dmm_logout))
                    }
                }
            }

            if (!state.configured || !state.runtimeProvisioned ||
                (state.signedInUserId != null && !state.playableApiConfigured)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        if (!state.configured) {
                            Text(
                                text = stringResource(R.string.dmm_oauth_not_configured),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                        if (!state.runtimeProvisioned) {
                            Text(
                                text = stringResource(R.string.dmm_runtime_not_provisioned),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                        if (state.signedInUserId != null && !state.playableApiConfigured) {
                            Text(
                                text = stringResource(R.string.dmm_playable_api_not_configured),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }

            state.statusMessage?.let { message ->
                Surface(
                    color = CardBackgroundHover,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Text(message, modifier = Modifier.weight(1f), color = TextPrimary)
                        if (!state.isBusy) {
                            TextButton(onClick = repository::clearStatus) {
                                Text(stringResource(R.string.dmm_dismiss))
                            }
                        }
                    }
                }
            }

            if (state.page == DmmPage.Browser) {
                OutlinedTextField(
                    value = mediaUrl,
                    onValueChange = { mediaUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.dmm_wsdcf_url)) },
                    placeholder = { Text("https://…/content.wsdcf") },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { repository.play(mediaUrl) },
                        enabled = mediaUrl.isNotBlank() && !state.isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Text(stringResource(R.string.dmm_stream))
                    }
                    Button(
                        onClick = { repository.download(mediaUrl) },
                        enabled = mediaUrl.isNotBlank() && !state.isBusy,
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Text(stringResource(R.string.dmm_download))
                    }
                    Button(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Text(stringResource(R.string.dmm_import_file))
                    }
                    if (state.signedInUserId == null) {
                        Button(onClick = repository::startLogin, enabled = state.configured) {
                            Icon(Icons.Filled.Login, contentDescription = null)
                            Text(stringResource(R.string.dmm_login))
                        }
                    }
                }

                if (state.signedInUserId != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(
                                R.string.dmm_purchased_content,
                                state.catalogTotalItems,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                        )
                        TextButton(
                            onClick = repository::refreshPurchasedContent,
                            enabled = !state.isBusy,
                        ) {
                            Text(stringResource(R.string.dmm_refresh_library))
                        }
                    }
                    if (state.catalog.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(state.catalog, key = { it.myLibraryId }) { item ->
                                DmmCatalogCard(
                                    item = item,
                                    isBusy = state.isBusy,
                                    onStream = { repository.stream(item) },
                                    onDownload = { repository.download(item) },
                                )
                            }
                        }
                    }
                }

                if (state.downloads.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.dmm_offline_downloads),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.downloads, key = { it.path }) { item ->
                            DmmDownloadCard(item = item, onPlay = { repository.playOffline(item) })
                        }
                    }
                }
            }

            HorizontalDivider(color = DividerGray)
            Text(
                text = currentPageUrl,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = true
                        settings.loadsImagesAutomatically = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.userAgentString = repository.decorateUserAgent(
                            settings.userAgentString,
                        )
                        android.webkit.CookieManager.getInstance()
                            .setAcceptThirdPartyCookies(this, true)
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                val url = request?.url?.toString() ?: return false
                                return repository.handleNavigation(url, view?.title)
                            }

                            @Deprecated("Deprecated in Android")
                            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                                url?.let { repository.handleNavigation(it, view?.title) } == true

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                sync(view)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                sync(view)
                            }
                        }
                        setDownloadListener(
                            DownloadListener { url, _, contentDisposition, mimeType, _ ->
                                val title = contentDisposition.orEmpty()
                                    .substringAfter("filename=", "")
                                    .trim(' ', '\"', '\'')
                                    .takeIf { it.isNotBlank() }
                                repository.handleDownload(url, title, mimeType.orEmpty())
                            }
                        )
                        repository.loadWebPage(this, state.activeUrl)
                        webView = this
                        sync(this)
                    }
                },
                update = { view ->
                    webView = view
                    sync(view)
                },
            )
        }
    }
}

@Composable
private fun DmmCatalogCard(
    item: DmmCatalogItem,
    isBusy: Boolean,
    onStream: () -> Unit,
    onDownload: () -> Unit,
) {
    Card(
        modifier = Modifier.width(220.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundHover),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item.packageImageUrl?.let { imageUrl ->
                AsyncImage(
                    model = imageUrl,
                    contentDescription = stringResource(R.string.dmm_cover_image, item.title),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(116.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
            Text(
                text = item.title,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
            )
            item.purchasedQualityGroup.takeIf(String::isNotBlank)?.let { quality ->
                Text(
                    text = quality,
                    maxLines = 1,
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = onStream,
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text(stringResource(R.string.dmm_stream))
                }
                Button(onClick = onDownload, enabled = !isBusy) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Text(stringResource(R.string.dmm_download))
                }
            }
        }
    }
}

@Composable
private fun DmmDownloadCard(
    item: DmmDownloadItem,
    onPlay: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBackgroundHover),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = item.title,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.progress?.let { progress ->
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
            Button(onClick = onPlay, enabled = item.isComplete) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text(stringResource(R.string.dmm_play_offline))
            }
        }
    }
}
