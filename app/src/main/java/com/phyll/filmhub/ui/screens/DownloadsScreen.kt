package com.phyll.filmhub.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.phyll.filmhub.offline.OfflineDownloadStatus
import com.phyll.filmhub.offline.OfflineEpisodeRecord
import com.phyll.filmhub.ui.theme.AmbientCinemaBackdropBrush
import com.phyll.filmhub.ui.theme.CinemaGlass
import com.phyll.filmhub.ui.theme.CinemaGlassBorder
import com.phyll.filmhub.ui.theme.CinemaObsidian
import com.phyll.filmhub.ui.theme.CinemaSurface
import com.phyll.filmhub.ui.theme.CinemaSurfaceElevated
import com.phyll.filmhub.ui.theme.EmeraldNeon
import com.phyll.filmhub.ui.theme.EmeraldPrimary
import com.phyll.filmhub.ui.theme.TextPrimary
import com.phyll.filmhub.ui.theme.TextSecondary
import com.phyll.filmhub.ui.theme.VioletSoft
import com.phyll.filmhub.ui.theme.tvFocusableClickable

@Composable
fun DownloadsScreen(
    downloads: List<OfflineEpisodeRecord>,
    onPlayOffline: (OfflineEpisodeRecord) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onExportSrt: (OfflineEpisodeRecord) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit
) {
    val totalBytes = downloads.sumOf { it.bytesDownloaded.coerceAtLeast(0L) }
    val completedCount = downloads.count { it.status == OfflineDownloadStatus.COMPLETED }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AmbientCinemaBackdropBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .tvFocusableClickable(
                            shape = RoundedCornerShape(99.dp),
                            focusedBorderColor = EmeraldNeon,
                            unfocusedBorderColor = CinemaGlassBorder,
                            onClick = onBack
                        )
                        .background(CinemaGlass),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text(
                        text = "📥 Phim & Phụ Đề Offline",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "$completedCount/${downloads.size} tập sẵn sàng • ${formatBytes(totalBytes)} đã lưu (xem không cần mạng)",
                        color = TextSecondary,
                        fontSize = 11.5.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Downloading,
                        contentDescription = null,
                        tint = EmeraldNeon.copy(alpha = 0.6f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Chưa có tập phim nào tải về máy",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Mở bất kỳ bộ phim nào → bấm nút \"⬇️ Tải Offline\" để tải ngầm HLS + Phụ đề AI xem trên máy bay, xe khách không cần Wi-Fi/4G.",
                        color = TextSecondary,
                        fontSize = 12.5.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(downloads, key = { it.id }) { item ->
                    OfflineDownloadItemCard(
                        item = item,
                        onPlay = { onPlayOffline(item) },
                        onPause = { onPause(item.id) },
                        onResume = { onResume(item.id) },
                        onExportSrt = { onExportSrt(item) },
                        onDelete = { onDelete(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OfflineDownloadItemCard(
    item: OfflineEpisodeRecord,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onExportSrt: () -> Unit,
    onDelete: () -> Unit
) {
    val isDone = item.status == OfflineDownloadStatus.COMPLETED
    val isActive = item.status == OfflineDownloadStatus.DOWNLOADING || item.status == OfflineDownloadStatus.QUEUED
    val isPaused = item.status == OfflineDownloadStatus.PAUSED
    val isFailed = item.status == OfflineDownloadStatus.FAILED

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CinemaSurface)
            .border(
                1.dp,
                if (isDone) EmeraldPrimary.copy(alpha = 0.45f) else CinemaGlassBorder,
                RoundedCornerShape(14.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Poster
        Box(
            modifier = Modifier
                .width(72.dp)
                .height(102.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CinemaSurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Movie, null, tint = TextSecondary.copy(alpha = 0.4f), modifier = Modifier.size(28.dp))
            if (item.posterUrl.isNotBlank()) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.movieTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (isDone) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(EmeraldPrimary)
                        .padding(3.dp)
                ) {
                    Icon(Icons.Default.DownloadDone, null, tint = CinemaObsidian, modifier = Modifier.size(12.dp))
                }
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = item.movieTitle,
                color = TextPrimary,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${item.episodeName} • ${formatBytes(item.bytesDownloaded)} • " + when (item.status) {
                    OfflineDownloadStatus.COMPLETED -> "✅ Sẵn sàng xem Offline"
                    OfflineDownloadStatus.DOWNLOADING -> "⬇️ Đang tải ${item.progressPercent}%"
                    OfflineDownloadStatus.QUEUED -> "⏳ Đang chờ tải..."
                    OfflineDownloadStatus.PAUSED -> "⏸ Đã tạm dừng (${item.progressPercent}%)"
                    OfflineDownloadStatus.FAILED -> "⚠️ Lỗi mạng — bấm Tiếp tục"
                },
                color = if (isDone) EmeraldNeon else if (isFailed) Color(0xFFFCA5A5) else TextSecondary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Subtitle attachment status
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(
                    Icons.Default.ClosedCaption,
                    null,
                    tint = if (item.subtitleCueCount > 0) VioletSoft else TextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = if (item.subtitleCueCount > 0) {
                        "Phụ đề ${item.subtitleLangCode.uppercase()} đã kèm theo (${item.subtitleCueCount} câu)"
                    } else {
                        "Video Offline (dịch sub trước khi tải để xem kèm sub offline)"
                    },
                    color = if (item.subtitleCueCount > 0) VioletSoft else TextSecondary.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!isDone) {
                LinearProgressIndicator(
                    progress = { (item.progressPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(99.dp)),
                    color = if (isFailed) Color(0xFFEF4444) else EmeraldNeon,
                    trackColor = CinemaSurfaceElevated
                )
            }

            // Action strip
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isDone) {
                    SmallDownloadBtn(
                        label = "▶ Xem Offline",
                        bgColor = EmeraldPrimary,
                        textColor = CinemaObsidian,
                        onClick = onPlay
                    )
                } else if (isActive) {
                    SmallDownloadBtn(
                        label = "⏸ Tạm dừng",
                        bgColor = CinemaSurfaceElevated,
                        textColor = TextPrimary,
                        onClick = onPause
                    )
                } else if (isPaused || isFailed) {
                    SmallDownloadBtn(
                        label = "▶ Tiếp tục",
                        bgColor = EmeraldPrimary.copy(alpha = 0.22f),
                        textColor = EmeraldNeon,
                        onClick = onResume
                    )
                }

                if (item.subtitleCueCount > 0) {
                    SmallDownloadBtn(
                        label = "📄 .SRT",
                        bgColor = Color(0x288B5CF6),
                        textColor = VioletSoft,
                        onClick = onExportSrt
                    )
                }

                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .tvFocusableClickable(
                            shape = RoundedCornerShape(8.dp),
                            focusedBorderColor = Color(0xFFEF4444),
                            unfocusedBorderColor = Color(0x33EF4444),
                            onClick = onDelete
                        )
                        .background(Color(0x1FEF4444)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Xóa", tint = Color(0xFFFCA5A5), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SmallDownloadBtn(
    label: String,
    bgColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .tvFocusableClickable(
                shape = RoundedCornerShape(8.dp),
                focusedBorderColor = EmeraldNeon,
                unfocusedBorderColor = Color.Transparent,
                onClick = onClick
            )
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) String.format("%.2f GB", mb / 1024.0) else String.format("%.1f MB", mb)
}
