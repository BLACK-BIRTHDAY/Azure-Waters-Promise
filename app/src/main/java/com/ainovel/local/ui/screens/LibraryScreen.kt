package com.ainovel.local.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import com.ainovel.local.ui.localization.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.graphics.scale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ainovel.local.data.model.NovelEntity
import com.ainovel.local.ui.components.PrimaryAction
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val COVER_ASPECT_RATIO = 2f / 3f

@Composable
fun LibraryScreen(
    novels: List<NovelEntity>,
    onCreate: (String) -> Unit,
    onSelect: (String) -> Unit,
    coverFile: (String?) -> File?,
    onDecodeCover: suspend (Uri) -> Bitmap,
    onSaveCover: (String, Bitmap, (Boolean) -> Unit) -> Unit,
) {
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var pendingNovelName by rememberSaveable { mutableStateOf("") }
    var coverTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var cropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var decodingCover by remember { mutableStateOf(false) }
    var coverError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && coverTargetId != null) {
            decodingCover = true
            coverError = null
            scope.launch {
                runCatching { onDecodeCover(uri) }
                    .onSuccess { cropBitmap = it }
                    .onFailure { coverError = it.message ?: "无法读取所选图片" }
                decodingCover = false
            }
        } else {
            coverTargetId = null
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(142.dp),
        modifier = Modifier.fillMaxSize().testTag("bookshelf-grid"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("我的书架", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "愿每一个故事，都能在这里拥有属于自己的灵魂。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (novels.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(Icons.Outlined.AutoStories, contentDescription = null, modifier = Modifier.size(32.dp))
                        Text("这里正等着第一个故事", style = MaterialTheme.typography.titleLarge)
                        Text("先为它取个名字吧，第一章会和它一起悄然诞生。")
                        PrimaryAction("开始一个故事", onClick = { showCreate = true }, icon = Icons.Outlined.Add)
                    }
                }
            }
        } else {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${novels.size} 本作品", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    PrimaryAction("新建", onClick = { showCreate = true }, icon = Icons.Outlined.Add)
                }
            }
            items(novels, key = { it.id }) { novel ->
                BookOnShelf(
                    novel = novel,
                    cover = coverFile(novel.coverFileName),
                    onOpen = { onSelect(novel.id) },
                    onPickCover = {
                        coverTargetId = novel.id
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false; pendingNovelName = "" },
            title = { Text("开启新故事") },
            text = {
                OutlinedTextField(
                    value = pendingNovelName,
                    onValueChange = { pendingNovelName = it },
                    label = { Text("故事的名字") },
                    supportingText = { Text("以后也可以随时为它换上喜欢的封面") },
                    modifier = Modifier.fillMaxWidth().testTag("novel-name-input"),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onCreate(pendingNovelName); showCreate = false; pendingNovelName = "" },
                    enabled = pendingNovelName.isNotBlank(),
                ) { Text("放上书架") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false; pendingNovelName = "" }) { Text("取消") } },
        )
    }

    if (decodingCover) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("正在打开图片") },
            text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(Modifier.size(28.dp)); Text("正在展开这张图片…") } },
            confirmButton = {},
        )
    }
    coverError?.let { message ->
        AlertDialog(
            onDismissRequest = { coverError = null },
            title = { Text("无法使用这张图片") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { coverError = null }) { Text("知道了") } },
        )
    }
    cropBitmap?.let { bitmap ->
        CoverCropScreen(
            bitmap = bitmap,
            onCancel = { cropBitmap = null; coverTargetId = null },
            onConfirm = { cropped, completion ->
                val novelId = coverTargetId ?: return@CoverCropScreen
                onSaveCover(novelId, cropped) { success ->
                    cropped.recycle()
                    if (success) {
                        cropBitmap = null
                        coverTargetId = null
                    }
                    completion(success)
                }
            },
        )
    }
}

@Composable
private fun BookOnShelf(
    novel: NovelEntity,
    cover: File?,
    onOpen: () -> Unit,
    onPickCover: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Box {
            Surface(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth().aspectRatio(COVER_ASPECT_RATIO),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(10.dp),
                shadowElevation = 5.dp,
            ) {
                CoverImage(file = cover, novelName = novel.name, cacheKey = novel.updatedAt)
            }
            FilledTonalIconButton(
                onClick = onPickCover,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(48.dp),
            ) {
                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = "为《${novel.name}》选择封面")
            }
        }
        Text(
            novel.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
        )
        Text(
            if (novel.bodyModelId == null) "只属于你的故事" else novel.bodyModelId,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun CoverImage(file: File?, novelName: String, cacheKey: Long) {
    var bitmap by remember(file?.absolutePath, cacheKey) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file?.absolutePath, cacheKey) {
        bitmap = withContext(Dispatchers.IO) { file?.let { BitmapFactory.decodeFile(it.absolutePath) } }
    }
    DisposableEffect(bitmap) {
        val displayedBitmap = bitmap
        onDispose { displayedBitmap?.takeUnless(Bitmap::isRecycled)?.recycle() }
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = "《$novelName》封面",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.Medium,
        )
    } ?: Box(Modifier.fillMaxSize().padding(14.dp)) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.AutoStories, contentDescription = null, modifier = Modifier.size(36.dp))
            Text(novelName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 3)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoverCropScreen(
    bitmap: Bitmap,
    onCancel: () -> Unit,
    onConfirm: (Bitmap, (Boolean) -> Unit) -> Unit,
) {
    var frameSize by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    DisposableEffect(bitmap) {
        onDispose { bitmap.takeUnless(Bitmap::isRecycled)?.recycle() }
    }

    fun baseScale(): Float = if (frameSize == IntSize.Zero) 1f else max(
        frameSize.width.toFloat() / bitmap.width.toFloat(),
        frameSize.height.toFloat() / bitmap.height.toFloat(),
    )

    fun clampedPan(candidate: Offset, candidateZoom: Float = zoom): Offset {
        if (frameSize == IntSize.Zero) return Offset.Zero
        val scale = baseScale() * candidateZoom
        val maxX = ((bitmap.width * scale) - frameSize.width).coerceAtLeast(0f) / 2f
        val maxY = ((bitmap.height * scale) - frameSize.height).coerceAtLeast(0f) / 2f
        return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
    }

    fun adjustZoom(delta: Float) {
        val oldZoom = zoom
        val newZoom = (oldZoom + delta).coerceIn(1f, 6f)
        zoom = newZoom
        pan = clampedPan(pan * (newZoom / oldZoom), newZoom)
    }

    fun moveImage(xDirection: Float, yDirection: Float) {
        val step = max(frameSize.width, frameSize.height) * 0.04f
        pan = clampedPan(pan + Offset(step * xDirection, step * yDirection))
    }

    Dialog(
        onDismissRequest = { if (!saving) onCancel() },
        properties = DialogProperties(
            dismissOnBackPress = !saving,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true,
        ),
    ) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        navigationIcon = { IconButton(onClick = onCancel, enabled = !saving) { Icon(Icons.Outlined.Close, contentDescription = "取消裁剪") } },
                        title = { Column { Text("调整封面"); Text("双指缩放 · 单指拖动", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                        actions = {
                            TextButton(
                                enabled = !saving && frameSize != IntSize.Zero,
                                onClick = {
                                    saving = true
                                    val snapshotSize = frameSize
                                    val snapshotZoom = zoom
                                    val snapshotPan = pan
                                    scope.launch {
                                        val cropped = withContext(Dispatchers.Default) {
                                            cropCover(bitmap, snapshotSize, snapshotZoom, snapshotPan)
                                        }
                                        onConfirm(cropped) { success -> if (!success) saving = false }
                                    }
                                },
                            ) {
                                if (saving) CircularProgressIndicator(Modifier.size(20.dp)) else Text("使用封面")
                            }
                        },
                    )
                },
                containerColor = Color.Black,
            ) { padding ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).background(Color.Black).padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(COVER_ASPECT_RATIO)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF101010))
                            .border(2.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                            .onSizeChanged {
                                frameSize = it
                                pan = clampedPan(pan)
                            }
                            .pointerInput(bitmap, frameSize) {
                                detectTransformGestures { centroid, gesturePan, gestureZoom, _ ->
                                    val oldZoom = zoom
                                    val newZoom = (oldZoom * gestureZoom).coerceIn(1f, 6f)
                                    val appliedZoom = newZoom / oldZoom
                                    val center = Offset(frameSize.width / 2f, frameSize.height / 2f)
                                    val focal = centroid - center
                                    val scaledAroundFocal = focal - (focal - pan) * appliedZoom
                                    zoom = newZoom
                                    pan = clampedPan(scaledAroundFocal + gesturePan, newZoom)
                                }
                            }
                            .semantics { contentDescription = "封面裁剪区域，可拖动和双指缩放" },
                        contentAlignment = Alignment.Center,
                    ) {
                        val image = remember(bitmap) { bitmap.asImageBitmap() }
                        Canvas(Modifier.fillMaxSize()) {
                            if (frameSize != IntSize.Zero) {
                                val scale = baseScale() * zoom
                                val width = (bitmap.width * scale).toInt()
                                val height = (bitmap.height * scale).toInt()
                                drawImage(
                                    image = image,
                                    dstOffset = IntOffset(
                                        ((size.width - width) / 2f + pan.x).toInt(),
                                        ((size.height - height) / 2f + pan.y).toInt(),
                                    ),
                                    dstSize = IntSize(width, height),
                                    filterQuality = FilterQuality.High,
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("封面比例 2:3", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        OutlinedButton(
                            onClick = { zoom = 1f; pan = Offset.Zero },
                            enabled = !saving && (zoom > 1.001f || pan != Offset.Zero),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White,
                                disabledContentColor = Color.White.copy(alpha = 0.38f),
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = if (saving) 0.28f else 0.78f)),
                        ) {
                            Icon(Icons.Outlined.Crop, contentDescription = null)
                            Text("复位", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        color = Color.White.copy(alpha = 0.09f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("缩放", color = Color.White, modifier = Modifier.weight(1f))
                                CropControlButton(
                                    icon = Icons.Outlined.ZoomOut,
                                    description = "缩小封面图片",
                                    enabled = !saving && zoom > 1f,
                                    onClick = { adjustZoom(-0.25f) },
                                )
                                Text(
                                    "${(zoom * 100).roundToInt()}%",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 8.dp).semantics {
                                        contentDescription = "当前缩放 ${(zoom * 100).roundToInt()}%"
                                    },
                                )
                                CropControlButton(
                                    icon = Icons.Outlined.ZoomIn,
                                    description = "放大封面图片",
                                    enabled = !saving && zoom < 6f,
                                    onClick = { adjustZoom(0.25f) },
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("移动图片", color = Color.White, modifier = Modifier.weight(1f))
                                CropControlButton(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "向左移动封面图片", !saving) { moveImage(-1f, 0f) }
                                CropControlButton(Icons.Outlined.KeyboardArrowUp, "向上移动封面图片", !saving) { moveImage(0f, -1f) }
                                CropControlButton(Icons.Outlined.KeyboardArrowDown, "向下移动封面图片", !saving) { moveImage(0f, 1f) }
                                CropControlButton(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "向右移动封面图片", !saving) { moveImage(1f, 0f) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CropControlButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .padding(4.dp)
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = if (enabled) 0.72f else 0.24f)),
                RoundedCornerShape(10.dp),
            ),
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.38f),
        )
    }
}

internal fun cropCover(bitmap: Bitmap, frameSize: IntSize, zoom: Float, pan: Offset): Bitmap {
    require(frameSize.width > 0 && frameSize.height > 0)
    val scale = max(
        frameSize.width.toFloat() / bitmap.width.toFloat(),
        frameSize.height.toFloat() / bitmap.height.toFloat(),
    ) * zoom
    val sourceWidth = (frameSize.width / scale).coerceAtMost(bitmap.width.toFloat())
    val sourceHeight = (frameSize.height / scale).coerceAtMost(bitmap.height.toFloat())
    val sourceCenterX = bitmap.width / 2f - pan.x / scale
    val sourceCenterY = bitmap.height / 2f - pan.y / scale
    val left = (sourceCenterX - sourceWidth / 2f).coerceIn(0f, bitmap.width - sourceWidth)
    val top = (sourceCenterY - sourceHeight / 2f).coerceIn(0f, bitmap.height - sourceHeight)
    val cropped = Bitmap.createBitmap(
        bitmap,
        left.toInt(),
        top.toInt(),
        sourceWidth.toInt().coerceAtLeast(1).coerceAtMost(bitmap.width - left.toInt()),
        sourceHeight.toInt().coerceAtLeast(1).coerceAtMost(bitmap.height - top.toInt()),
    )
    return cropped.scale(800, 1_200, true).also { if (it !== cropped) cropped.recycle() }
}
