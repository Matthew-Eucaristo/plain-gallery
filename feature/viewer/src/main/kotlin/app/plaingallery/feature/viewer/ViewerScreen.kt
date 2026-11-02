package app.plaingallery.feature.viewer

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.plaingallery.core.data.contentUri
import app.plaingallery.core.model.Media
import app.plaingallery.core.model.MediaCollection
import app.plaingallery.core.ui.LocalGalleryUiState
import app.plaingallery.core.ui.actions.rememberMediaActions
import app.plaingallery.core.ui.browser.DeleteForeverDialog
import app.plaingallery.core.ui.browser.rememberTrashWithUndo
import app.plaingallery.core.ui.format.dateTimeLabel
import app.plaingallery.core.ui.format.sourceLabel
import app.plaingallery.core.ui.rememberCollection
import app.plaingallery.core.ui.share.ShareSheet
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import app.plaingallery.core.ui.R as UiR

/**
 * Full-screen pager over the same collection the user came from. Tap toggles the chrome,
 * swipe down closes, pinch zooms; actions are one tap away at the bottom.
 */
@Composable
fun ViewerScreen(collection: MediaCollection, initialId: Long, onBack: () -> Unit) {
    val items by rememberCollection(collection)
    val list = items.orEmpty()
    val uiState = LocalGalleryUiState.current
    val initialPage = remember { list.indexOfFirst { it.id == initialId }.coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage) { list.size }
    val current = list.getOrNull(pager.currentPage)
    val isTrash = collection == MediaCollection.Trash

    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf<List<Media>?>(null) }
    var info by remember { mutableStateOf<Media?>(null) }
    var confirmDelete by remember { mutableStateOf<Media?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val actions = rememberMediaActions()
    val trash = rememberTrashWithUndo(actions, snackbar)
    val player = rememberViewerPlayer()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(items) { if (items != null && list.isEmpty()) onBack() }
    LaunchedEffect(current?.id) { current?.let { uiState.lastViewedId = it.id } }
    ImmersiveEffect(chromeVisible)
    val hdrIds = remember { mutableStateSetOf<Long>() }
    HdrWindowEffect(current?.id in hdrIds)

    // Swipe down to close: the photo follows the finger and the background fades.
    val dismissOffset = remember { Animatable(0f) }
    val dismissDistance = with(LocalDensity.current) { 160.dp.toPx() }
    val progress = (abs(dismissOffset.value) / (dismissDistance * 2)).coerceIn(0f, 1f)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 1f - progress))
            .draggable(
                state = rememberDraggableState { delta -> scope.launch { dismissOffset.snapTo(dismissOffset.value + delta) } },
                orientation = Orientation.Vertical,
                enabled = !zoomed,
                onDragStopped = { velocity ->
                    if (abs(dismissOffset.value) > dismissDistance || abs(velocity) > DISMISS_VELOCITY) onBack()
                    else dismissOffset.animateTo(0f)
                },
            ),
    ) {
        HorizontalPager(
            state = pager,
            key = { list[it].id },
            beyondViewportPageCount = 1,
            pageSpacing = 16.dp,
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, dismissOffset.value.roundToInt()) }
                .graphicsLayer {
                    val scale = 1f - progress * 0.2f
                    scaleX = scale
                    scaleY = scale
                },
        ) { page ->
            val media = list[page]
            val toggleChrome = { chromeVisible = !chromeVisible }
            if (media.isVideo) {
                VideoPage(media, player, active = page == pager.settledPage, showControls = chromeVisible, onTap = toggleChrome)
            } else {
                ImagePage(
                    media,
                    onTap = toggleChrome,
                    onZoomedChange = { if (page == pager.currentPage) zoomed = it },
                    onHdrChange = { if (it) hdrIds.add(media.id) else hdrIds.remove(media.id) },
                )
            }
        }

        AnimatedVisibility(
            visible = chromeVisible && current != null && progress == 0f,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            current ?: return@AnimatedVisibility
            Box(Modifier.fillMaxSize()) {
                TopBar(dateTimeLabel(current.takenAt), sourceLabel(current), onBack, Modifier.align(Alignment.TopCenter))
                BottomBar(Modifier.align(Alignment.BottomCenter)) {
                    if (isTrash) {
                        Action(painterResource(UiR.drawable.ic_restore), stringResource(UiR.string.action_restore)) {
                            actions.restore(listOf(current))
                        }
                        Action(rememberVectorPainter(Icons.Filled.Delete), stringResource(UiR.string.action_delete_forever)) {
                            confirmDelete = current
                        }
                    } else {
                        Action(rememberVectorPainter(Icons.Filled.Share), stringResource(UiR.string.action_share)) {
                            sharing = listOf(current)
                        }
                        if (!current.isVideo) {
                            val editWith = stringResource(R.string.edit_with)
                            val noEditor = stringResource(R.string.no_editor)
                            Action(rememberVectorPainter(Icons.Filled.Edit), stringResource(R.string.action_edit)) {
                                val edit = Intent(Intent.ACTION_EDIT)
                                    .setDataAndType(current.contentUri, current.mimeType)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                runCatching { context.startActivity(Intent.createChooser(edit, editWith)) }
                                    .onFailure { Toast.makeText(context, noEditor, Toast.LENGTH_SHORT).show() }
                            }
                        }
                        Action(
                            rememberVectorPainter(if (current.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder),
                            stringResource(if (current.isFavorite) UiR.string.action_unfavorite else UiR.string.action_favorite),
                        ) { actions.setFavorite(listOf(current), !current.isFavorite) }
                        Action(rememberVectorPainter(Icons.Filled.Info), stringResource(R.string.action_info)) { info = current }
                        Action(rememberVectorPainter(Icons.Filled.Delete), stringResource(UiR.string.action_delete)) {
                            trash(listOf(current))
                        }
                    }
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp))
    }

    sharing?.let { ShareSheet(it, onDismiss = { sharing = null }) }
    info?.let { InfoSheet(it, onDismiss = { info = null }) }
    confirmDelete?.let { media ->
        DeleteForeverDialog(
            count = 1,
            onConfirm = {
                actions.deleteForever(listOf(media))
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
internal fun TopBar(title: String, subtitle: String?, onBack: () -> Unit, modifier: Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(UiR.string.action_back), tint = Color.White)
        }
        Column(Modifier.padding(start = 4.dp)) {
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    it,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun BottomBar(modifier: Modifier, content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
            .navigationBarsPadding()
            .padding(top = 24.dp, bottom = 8.dp),
    ) { content() }
}

@Composable
internal fun Action(icon: Painter, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) { Icon(icon, contentDescription = label, tint = Color.White) }
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/** Hides the system bars with the chrome and keeps their icons light on the black background. */
@Composable
internal fun ImmersiveEffect(chromeVisible: Boolean) {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    val controller = remember(window, view) { WindowCompat.getInsetsController(window, view) }
    DisposableEffect(controller) {
        val lightStatus = controller.isAppearanceLightStatusBars
        val lightNav = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.isAppearanceLightStatusBars = lightStatus
            controller.isAppearanceLightNavigationBars = lightNav
        }
    }
    LaunchedEffect(chromeVisible) {
        if (chromeVisible) controller.show(WindowInsetsCompat.Type.systemBars())
        else controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}

/** Lets the display show Ultra HDR photos at full brightness range (Android 14+), only while one is on screen. */
@Composable
private fun HdrWindowEffect(enabled: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
    val window = LocalActivity.current?.window ?: return
    DisposableEffect(enabled) {
        window.colorMode = if (enabled) ActivityInfo.COLOR_MODE_HDR else ActivityInfo.COLOR_MODE_DEFAULT
        onDispose { window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT }
    }
}

private const val DISMISS_VELOCITY = 2500f
