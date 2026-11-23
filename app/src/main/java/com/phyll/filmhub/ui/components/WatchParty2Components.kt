package com.phyll.filmhub.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.phyll.filmhub.cloud.WatchPartyEpisodeMeta
import com.phyll.filmhub.cloud.WatchPartyLobbyRoom
import com.phyll.filmhub.cloud.WatchPartyMember
import com.phyll.filmhub.cloud.WatchPartyMessage
import com.phyll.filmhub.cloud.WatchPartyMovieMeta
import com.phyll.filmhub.cloud.WatchPartyQueueItem
import com.phyll.filmhub.cloud.WatchPartyReaction
import com.phyll.filmhub.companion.QrCodeGenerator
import com.phyll.filmhub.model.Episode
import com.phyll.filmhub.model.MovieItem
import com.phyll.filmhub.ui.theme.CinemaGlass
import com.phyll.filmhub.ui.theme.CinemaGlassBorder
import com.phyll.filmhub.ui.theme.CinemaObsidian
import com.phyll.filmhub.ui.theme.CinemaSurface
import com.phyll.filmhub.ui.theme.CinemaSurfaceElevated
import com.phyll.filmhub.ui.theme.EmeraldNeon
import com.phyll.filmhub.ui.theme.EmeraldPrimary
import com.phyll.filmhub.ui.theme.TextPrimary
import com.phyll.filmhub.ui.theme.TextSecondary
import com.phyll.filmhub.ui.theme.ThaiGold
import com.phyll.filmhub.ui.theme.VioletGlow
import com.phyll.filmhub.ui.theme.VioletSoft
import com.phyll.filmhub.ui.theme.tvFocusableClickable
import kotlinx.coroutines.delay

data class WatchPartySyncCommand(
    val id: Long = System.currentTimeMillis(),
    val targetPositionMs: Long,
    val shouldPlay: Boolean,
    val playbackRate: Float = 1.0f,
    val forceHardSeek: Boolean = false,
    val countdownStartAtLocalMs: Long = 0L
)

data class WatchPartyUiState(
    val showHubDialog: Boolean = false,
    val hubInitialTab: Int = 0, // 0=Lobby, 1=Join, 2=Create
    val prefillJoinCode: String = "",
    val needPasscodePrompt: Boolean = false,
    val createPickedMovie: MovieItem? = null,
    val createPickedEpisode: Episode? = null,
    val lobbyRooms: List<WatchPartyLobbyRoom> = emptyList(),
    val lobbyLoading: Boolean = false,
    val isBusy: Boolean = false,
    val errorText: String = "",
    // Active room state
    val roomCode: String = "",
    val roomTitle: String = "",
    val visibility: String = "PRIVATE",
    val controlMode: String = "HOST_ONLY",
    val hasPasscode: Boolean = false,
    val hostToken: String = "",
    val hostName: String = "",
    val hostInactive: Boolean = false,
    val memberId: String = "",
    val nickname: String = "ChillFan",
    val isHost: Boolean = false,
    val panelVisible: Boolean = true,
    val syncStatusText: String = "● Sync OK",
    val actionPillText: String = "",
    val actionPillTs: Long = 0L,
    val countdownTargetLocalMs: Long = 0L,
    val members: List<WatchPartyMember> = emptyList(),
    val queue: List<WatchPartyQueueItem> = emptyList(),
    val messages: List<WatchPartyMessage> = emptyList(),
    val floatingReactions: List<WatchPartyReaction> = emptyList(),
    val quickSearchResults: List<MovieItem> = emptyList(),
    val quickSearching: Boolean = false
) {
    val isInRoom: Boolean get() = roomCode.isNotBlank()
    val canControl: Boolean get() = isHost || controlMode == "EVERYONE"
}

private val PARTY_EMOJIS = listOf("😂", "🔥", "😱", "❤️", "👏", "😭", "💀", "🍿")

@Composable
fun WatchPartyHubDialog(
    state: WatchPartyUiState,
    defaultNickname: String,
    onDismiss: () -> Unit,
    onRefreshLobby: () -> Unit,
    onJoinRoom: (code: String, nickname: String, passcode: String) -> Unit,
    onCreateRoom: (roomTitle: String, nickname: String, visibility: String, controlMode: String, passcode: String, movie: MovieItem?, ep: Episode?) -> Unit,
    onQuickSearchMovies: (String) -> Unit,
    onPickMovieForCreate: (MovieItem) -> Unit
) {
    var activeTab by remember(state.hubInitialTab, state.needPasscodePrompt) {
        mutableIntStateOf(if (state.needPasscodePrompt) 1 else state.hubInitialTab)
    }
    var joinCode by remember(state.prefillJoinCode) { mutableStateOf(state.prefillJoinCode) }
    var joinNick by remember(defaultNickname) { mutableStateOf(defaultNickname.ifBlank { "ChillFan" }) }
    var joinPass by remember { mutableStateOf("") }

    var createTitle by remember(state.createPickedMovie?.id) {
        mutableStateOf(state.createPickedMovie?.title?.let { "Rạp của $defaultNickname • $it" } ?: "")
    }
    var createVis by remember { mutableStateOf("PRIVATE") }
    var createCtrl by remember { mutableStateOf("HOST_ONLY") }
    var createPass by remember { mutableStateOf("") }
    var createSearch by remember { mutableStateOf("") }

    LaunchedEffect(activeTab) {
        if (activeTab == 0) onRefreshLobby()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 580.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF0E131A))
                .border(1.dp, EmeraldPrimary.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🍿 Watch Party 2.0 — Xem Chung",
                    color = EmeraldNeon,
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.Black
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .tvFocusableClickable(
                            shape = CircleShape,
                            focusedBorderColor = EmeraldNeon,
                            unfocusedBorderColor = CinemaGlassBorder,
                            onClick = onDismiss
                        )
                        .background(CinemaGlass),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Đóng", tint = TextPrimary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3 Hub Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("🌐 Sảnh Công Khai", "🔑 Vào bằng Mã", "✨ Tạo Phòng").forEachIndexed { idx, label ->
                    val selected = activeTab == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .tvFocusableClickable(
                                shape = RoundedCornerShape(10.dp),
                                focusedBorderColor = EmeraldNeon,
                                unfocusedBorderColor = if (selected) EmeraldNeon else CinemaGlassBorder,
                                onClick = { activeTab = idx }
                            )
                            .background(if (selected) EmeraldPrimary.copy(alpha = 0.24f) else CinemaSurface)
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (selected) EmeraldNeon else TextSecondary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (state.errorText.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "⚠️ ${state.errorText}",
                    color = Color(0xFFFCA5A5),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (activeTab) {
                0 -> {
                    // PUBLIC LIVE LOBBY
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Phòng công khai đang phát (${state.lobbyRooms.size})",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .tvFocusableClickable(
                                    shape = RoundedCornerShape(8.dp),
                                    focusedBorderColor = EmeraldNeon,
                                    unfocusedBorderColor = CinemaGlassBorder,
                                    onClick = onRefreshLobby
                                )
                                .background(CinemaSurface)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Refresh, null, tint = EmeraldNeon, modifier = Modifier.size(14.dp))
                                Text("Làm mới", color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (state.lobbyLoading) {
                        Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = EmeraldNeon, modifier = Modifier.size(28.dp))
                        }
                    } else if (state.lobbyRooms.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CinemaSurface)
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Chưa có phòng Công khai nào đang mở.\nChuyển sang tab \"✨ Tạo Phòng\" để mở rạp đầu tiên!",
                                color = TextSecondary,
                                fontSize = 12.5.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().height(260.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.lobbyRooms, key = { it.roomCode }) { room ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .tvFocusableClickable(
                                            shape = RoundedCornerShape(12.dp),
                                            focusedBorderColor = EmeraldNeon,
                                            unfocusedBorderColor = CinemaGlassBorder,
                                            onClick = {
                                                if (room.hasPasscode) {
                                                    joinCode = room.roomCode
                                                    activeTab = 1
                                                } else {
                                                    onJoinRoom(room.roomCode, joinNick, "")
                                                }
                                            }
                                        )
                                        .background(CinemaSurface)
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = room.movie.poster,
                                        contentDescription = room.movie.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .width(46.dp)
                                            .height(64.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(CinemaSurfaceElevated)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = room.roomTitle.ifBlank { room.movie.name },
                                            color = TextPrimary,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "🎬 ${room.movie.name} • ${room.episodeName}",
                                            color = EmeraldNeon,
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "👑 ${room.hostName} • 👥 ${room.viewerCount}" +
                                                (if (room.controlMode == "EVERYONE") " • 🤝 Co-Control" else "") +
                                                (if (room.hasPasscode) " • 🔒 Mật khẩu" else ""),
                                            color = TextSecondary,
                                            fontSize = 10.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(EmeraldPrimary)
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = if (room.hasPasscode) "🔒 Vào" else "▶ Vào",
                                            color = CinemaObsidian,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // JOIN BY ROOM CODE + OPTIONAL PASSCODE
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PartyTextField(
                            value = joinCode,
                            onValueChange = { joinCode = it.uppercase().take(8) },
                            label = "Mã phòng 6 ký tự (VD: ABC123)"
                        )
                        PartyTextField(
                            value = joinNick,
                            onValueChange = { joinNick = it.take(26) },
                            label = "Tên hiển thị của bạn"
                        )
                        PartyTextField(
                            value = joinPass,
                            onValueChange = { joinPass = it.take(24) },
                            label = if (state.needPasscodePrompt) "🔒 Nhập mật khẩu phòng (Bắt buộc)" else "Mật khẩu phòng (nếu có)"
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvFocusableClickable(
                                    shape = RoundedCornerShape(10.dp),
                                    focusedBorderColor = ThaiGold,
                                    unfocusedBorderColor = Color.Transparent,
                                    onClick = {
                                        if (!state.isBusy && joinCode.trim().length >= 4) {
                                            onJoinRoom(joinCode.trim(), joinNick.trim().ifBlank { "Khách" }, joinPass.trim())
                                        }
                                    }
                                )
                                .background(EmeraldPrimary)
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (state.isBusy) "⏳ Đang vào phòng..." else "🍿 Vào phòng ngay",
                                color = CinemaObsidian,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                else -> {
                    // CREATE NEW ROOM
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        val picked = state.createPickedMovie
                        val pickedEp = state.createPickedEpisode ?: picked?.firstPlayableEpisode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(CinemaSurface)
                                .border(1.dp, CinemaGlassBorder, RoundedCornerShape(10.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (picked != null) {
                                AsyncImage(
                                    model = picked.posterUrl,
                                    contentDescription = picked.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.width(42.dp).height(58.dp).clip(RoundedCornerShape(6.dp))
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(picked.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        text = pickedEp?.name ?: "Đang tải luồng phim...",
                                        color = EmeraldNeon,
                                        fontSize = 11.5.sp
                                    )
                                }
                            } else {
                                Text(
                                    text = "Chưa chọn phim — tìm nhanh bên dưới hoặc bấm 🍿 Xem chung từ trang phim!",
                                    color = TextSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                        }

                        // Inline quick search to pick a movie right in Create Room tab
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.weight(1f)) {
                                PartyTextField(
                                    value = createSearch,
                                    onValueChange = { createSearch = it },
                                    label = "Tìm phim để mở rạp..."
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .tvFocusableClickable(
                                        shape = RoundedCornerShape(10.dp),
                                        focusedBorderColor = EmeraldNeon,
                                        unfocusedBorderColor = CinemaGlassBorder,
                                        onClick = { onQuickSearchMovies(createSearch) }
                                    )
                                    .background(EmeraldPrimary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Tìm", tint = EmeraldNeon)
                            }
                        }

                        if (state.quickSearchResults.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                state.quickSearchResults.take(4).forEach { m ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .tvFocusableClickable(
                                                shape = RoundedCornerShape(8.dp),
                                                focusedBorderColor = EmeraldNeon,
                                                unfocusedBorderColor = CinemaGlassBorder,
                                                onClick = {
                                                    onPickMovieForCreate(m)
                                                    createSearch = ""
                                                }
                                            )
                                            .background(CinemaSurfaceElevated)
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(m.title, color = TextPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        Text("Chọn", color = EmeraldNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        PartyTextField(value = createTitle, onValueChange = { createTitle = it.take(70) }, label = "Tên phòng (tùy chọn)")

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ToggleChip(
                                modifier = Modifier.weight(1f),
                                active = createVis == "PRIVATE",
                                label = if (createVis == "PUBLIC") "🌐 Công khai" else "🔒 Riêng tư",
                                onClick = { createVis = if (createVis == "PUBLIC") "PRIVATE" else "PUBLIC" }
                            )
                            ToggleChip(
                                modifier = Modifier.weight(1f),
                                active = createCtrl == "EVERYONE",
                                label = if (createCtrl == "EVERYONE") "🤝 Ai cũng điều khiển" else "👑 Chỉ Chủ phòng",
                                onClick = { createCtrl = if (createCtrl == "EVERYONE") "HOST_ONLY" else "EVERYONE" }
                            )
                        }

                        PartyTextField(value = createPass, onValueChange = { createPass = it.take(24) }, label = "Mật khẩu phòng (để trống = không khóa)")

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvFocusableClickable(
                                    shape = RoundedCornerShape(10.dp),
                                    focusedBorderColor = ThaiGold,
                                    unfocusedBorderColor = Color.Transparent,
                                    onClick = {
                                        if (!state.isBusy) {
                                            onCreateRoom(
                                                createTitle.trim(),
                                                joinNick.trim().ifBlank { "Host" },
                                                createVis,
                                                createCtrl,
                                                createPass.trim(),
                                                picked,
                                                pickedEp
                                            )
                                        }
                                    }
                                )
                                .background(EmeraldPrimary)
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (state.isBusy) "⏳ Đang mở phòng..." else "✨ Mở Phòng Xem Chung",
                                color = CinemaObsidian,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * In-Player Watch Party 2.0 Overlay:
 * - Floating Emoji Reactions rising over the video
 * - Centered 3-2-1 Synchronized Countdown
 * - Subtle top Action Pill
 * - Collapsible 3-Tab Side Panel (Chat / Up Next Queue + Voting / Room Info + QR Invite + Host Controls)
 */
@Composable
fun WatchPartyPlayerOverlay(
    state: WatchPartyUiState,
    onTogglePanel: () -> Unit,
    onForceResync: () -> Unit,
    onSendChat: (String) -> Unit,
    onSendReaction: (String) -> Unit,
    onStartCountdown: () -> Unit,
    onToggleControlMode: () -> Unit,
    onToggleVisibility: () -> Unit,
    onClaimHost: () -> Unit,
    onQueueSearch: (String) -> Unit,
    onQueueAddMovie: (MovieItem) -> Unit,
    onQueueVote: (String) -> Unit,
    onQueuePlay: (String) -> Unit,
    onQueueRemove: (String) -> Unit,
    onLeaveParty: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.isInRoom) return
    val clipboard = LocalClipboardManager.current

    // Live 3-2-1 Countdown ticker
    var countdownSec by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.countdownTargetLocalMs) {
        if (state.countdownTargetLocalMs > System.currentTimeMillis()) {
            while (true) {
                val rem = state.countdownTargetLocalMs - System.currentTimeMillis()
                if (rem <= 0L) {
                    countdownSec = 0
                    break
                }
                countdownSec = ((rem + 999L) / 1000L).toInt().coerceIn(1, 5)
                delay(150L)
            }
        } else {
            countdownSec = 0
        }
    }

    // Auto-hide action pill after 3.8s
    var showActionPill by remember { mutableStateOf(false) }
    LaunchedEffect(state.actionPillTs, state.actionPillText) {
        if (state.actionPillText.isNotBlank()) {
            showActionPill = true
            delay(3800L)
            showActionPill = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Top-Center Action Notification Pill
        AnimatedVisibility(
            visible = showActionPill && state.actionPillText.isNotBlank(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0xD80A1219))
                    .border(1.dp, EmeraldNeon.copy(alpha = 0.55f), RoundedCornerShape(99.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = state.actionPillText,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 2. Centered 3-2-1 Synchronized Countdown Overlay
        if (countdownSec > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC060B10))
                    .border(3.dp, EmeraldNeon, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = countdownSec.toString(),
                    color = EmeraldNeon,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // 3. Floating Emoji Reactions (bottom-right rising animation)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = if (state.panelVisible) 320.dp else 22.dp, bottom = 78.dp)
        ) {
            state.floatingReactions.takeLast(8).forEachIndexed { idx, r ->
                FloatingReactionBubble(
                    reaction = r,
                    laneOffsetX = ((idx % 3) - 1) * 28
                )
            }
        }

        // 4. Collapsible 3-Tab Side Panel
        if (state.panelVisible) {
            var sideTab by remember { mutableIntStateOf(0) } // 0=Chat, 1=Queue, 2=Room
            var chatInput by remember { mutableStateOf("") }
            var queueQuery by remember { mutableStateOf("") }
            val chatListState = rememberLazyListState()

            LaunchedEffect(state.messages.size) {
                if (state.messages.isNotEmpty()) {
                    chatListState.animateScrollToItem(state.messages.lastIndex)
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(305.dp)
                    .background(Color(0xE80A0F16))
                    .border(1.dp, EmeraldPrimary.copy(alpha = 0.3f))
                    .padding(10.dp)
            ) {
                // Panel header: Room Code + Sync badge + Resync + Collapse
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🍿 ${state.roomCode} • ${state.members.size.coerceAtLeast(1)} xem",
                            color = EmeraldNeon,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1
                        )
                        Text(
                            text = state.syncStatusText,
                            color = TextSecondary,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        SmallPanelIconBtn("🔄", onForceResync)
                        SmallPanelIconBtn("✕", onTogglePanel)
                    }
                }

                Spacer(modifier = Modifier.height(7.dp))

                // 3 Tabs: Chat / Queue / Room
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        "💬 Chat",
                        "🎬 Chờ (${state.queue.size})",
                        "👥 Phòng"
                    ).forEachIndexed { i, title ->
                        val active = sideTab == i
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .tvFocusableClickable(
                                    shape = RoundedCornerShape(8.dp),
                                    focusedBorderColor = EmeraldNeon,
                                    unfocusedBorderColor = if (active) EmeraldNeon else CinemaGlassBorder,
                                    onClick = { sideTab = i }
                                )
                                .background(if (active) EmeraldPrimary.copy(alpha = 0.24f) else CinemaSurface)
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(title, color = if (active) EmeraldNeon else TextSecondary, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                when (sideTab) {
                    0 -> {
                        // CHAT TAB + EMOJI BAR
                        LazyColumn(
                            state = chatListState,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(state.messages, key = { "${it.id}_${it.createdAt}" }) { m ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CinemaSurface)
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = if (m.isHost) "👑 ${m.senderName}" else m.senderName,
                                        color = if (m.isHost) ThaiGold else EmeraldNeon,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(text = m.text, color = TextPrimary, fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Quick floating emoji reaction bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            PARTY_EMOJIS.forEach { emoji ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .tvFocusableClickable(
                                            shape = CircleShape,
                                            focusedBorderColor = EmeraldNeon,
                                            unfocusedBorderColor = CinemaGlassBorder,
                                            onClick = { onSendReaction(emoji) }
                                        )
                                        .background(CinemaSurface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(emoji, fontSize = 15.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Chat composer
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.weight(1f)) {
                                PartyTextField(
                                    value = chatInput,
                                    onValueChange = { chatInput = it.take(200) },
                                    label = "Nhắn tin..."
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .tvFocusableClickable(
                                        shape = RoundedCornerShape(10.dp),
                                        focusedBorderColor = EmeraldNeon,
                                        unfocusedBorderColor = Color.Transparent,
                                        onClick = {
                                            val clean = chatInput.trim()
                                            if (clean.isNotEmpty()) {
                                                onSendChat(clean)
                                                chatInput = ""
                                            }
                                        }
                                    )
                                    .background(EmeraldPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Gửi", tint = CinemaObsidian, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    1 -> {
                        // UP NEXT QUEUE + INLINE SEARCH + VOTING
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.weight(1f)) {
                                PartyTextField(
                                    value = queueQuery,
                                    onValueChange = { queueQuery = it },
                                    label = "Thêm phim vào hàng chờ..."
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .tvFocusableClickable(
                                        shape = RoundedCornerShape(10.dp),
                                        focusedBorderColor = EmeraldNeon,
                                        unfocusedBorderColor = CinemaGlassBorder,
                                        onClick = { onQueueSearch(queueQuery) }
                                    )
                                    .background(EmeraldPrimary.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Tìm", tint = EmeraldNeon, modifier = Modifier.size(18.dp))
                            }
                        }

                        if (state.quickSearchResults.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                state.quickSearchResults.take(3).forEach { m ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .tvFocusableClickable(
                                                shape = RoundedCornerShape(8.dp),
                                                focusedBorderColor = EmeraldNeon,
                                                unfocusedBorderColor = CinemaGlassBorder,
                                                onClick = {
                                                    onQueueAddMovie(m)
                                                    queueQuery = ""
                                                }
                                            )
                                            .background(CinemaSurfaceElevated)
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(m.title, color = TextPrimary, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        Text("+ Thêm", color = EmeraldNeon, fontSize = 11.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            if (state.queue.isEmpty()) {
                                item {
                                    Text(
                                        text = "Hàng chờ đang trống. Tìm phim ở trên để thêm & bình chọn phát tiếp theo!",
                                        color = TextSecondary,
                                        fontSize = 11.5.sp
                                    )
                                }
                            } else {
                                items(state.queue, key = { it.id }) { q ->
                                    val votedByMe = q.votes.contains(state.memberId)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(CinemaSurface)
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(q.movie.name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${q.episode.name} • +${q.addedBy}", color = TextSecondary, fontSize = 10.5.sp, maxLines = 1)
                                        }
                                        // Vote button
                                        Box(
                                            modifier = Modifier
                                                .tvFocusableClickable(
                                                    shape = RoundedCornerShape(7.dp),
                                                    focusedBorderColor = EmeraldNeon,
                                                    unfocusedBorderColor = if (votedByMe) EmeraldNeon else CinemaGlassBorder,
                                                    onClick = { onQueueVote(q.id) }
                                                )
                                                .background(if (votedByMe) EmeraldPrimary.copy(alpha = 0.25f) else CinemaSurfaceElevated)
                                                .padding(horizontal = 7.dp, vertical = 5.dp)
                                        ) {
                                            Text("👍 ${q.votes.size}", color = if (votedByMe) EmeraldNeon else TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        if (state.canControl) {
                                            SmallPanelIconBtn("▶") { onQueuePlay(q.id) }
                                        }
                                        if (state.canControl || q.addedById == state.memberId) {
                                            SmallPanelIconBtn("✕") { onQueueRemove(q.id) }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        // ROOM INFO + QR INVITE + HOST CONTROLS + VIEWERS LIST
                        val shareUrl = "https://chillfilm.vercel.app/?party=${state.roomCode}"
                        val qrBitmap = remember(state.roomCode) {
                            QrCodeGenerator.generateQrBitmap(shareUrl, 220)
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // QR Invite Card
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CinemaSurface)
                                    .padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (qrBitmap != null) {
                                    Image(
                                        bitmap = qrBitmap.asImageBitmap(),
                                        contentDescription = "QR Mời phòng",
                                        modifier = Modifier.size(118.dp).clip(RoundedCornerShape(8.dp))
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                                Text(
                                    text = "Mã phòng: ${state.roomCode}",
                                    color = EmeraldNeon,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    SmallPanelChip("📋 Copy link") {
                                        clipboard.setText(AnnotatedString(shareUrl))
                                    }
                                    if (state.canControl) {
                                        SmallPanelChip("⏱️ 3-2-1") { onStartCountdown() }
                                    }
                                }
                            }

                            if (state.isHost) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ToggleChip(
                                        modifier = Modifier.weight(1f),
                                        active = state.controlMode == "EVERYONE",
                                        label = if (state.controlMode == "EVERYONE") "🤝 Ai cũng ĐK" else "👑 Chỉ Host",
                                        onClick = onToggleControlMode
                                    )
                                    ToggleChip(
                                        modifier = Modifier.weight(1f),
                                        active = state.visibility == "PUBLIC",
                                        label = if (state.visibility == "PUBLIC") "🌐 Công khai" else "🔒 Riêng tư",
                                        onClick = onToggleVisibility
                                    )
                                }
                            } else if (state.hostInactive) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .tvFocusableClickable(
                                            shape = RoundedCornerShape(9.dp),
                                            focusedBorderColor = ThaiGold,
                                            unfocusedBorderColor = ThaiGold.copy(alpha = 0.6f),
                                            onClick = onClaimHost
                                        )
                                        .background(ThaiGold.copy(alpha = 0.2f))
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("👑 Tiếp quản quyền Chủ phòng", color = ThaiGold, fontSize = 12.sp, fontWeight = FontWeight.Black)
                                }
                            }

                            Text(
                                text = "Người đang xem (${state.members.size})",
                                color = TextSecondary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            state.members.forEach { m ->
                                val stIcon = when (m.status) {
                                    "paused" -> "⏸"
                                    "buffering" -> "⏳"
                                    else -> "▶"
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CinemaSurface)
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = (if (m.isHost) "👑 " else "👤 ") + m.name,
                                        color = if (m.isHost) ThaiGold else TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "$stIcon ${m.posSec / 60}:${"%02d".format(m.posSec % 60)}",
                                        color = EmeraldNeon,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvFocusableClickable(
                                        shape = RoundedCornerShape(9.dp),
                                        focusedBorderColor = Color(0xFFEF4444),
                                        unfocusedBorderColor = Color(0x55EF4444),
                                        onClick = onLeaveParty
                                    )
                                    .background(Color(0x28EF4444))
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🚪 Rời phòng Xem chung", color = Color(0xFFFCA5A5), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingReactionBubble(reaction: WatchPartyReaction, laneOffsetX: Int) {
    val progress = remember(reaction.id) { Animatable(0f) }
    LaunchedEffect(reaction.id) {
        progress.animateTo(1f, animationSpec = tween(durationMillis = 2300))
    }
    val p = progress.value
    if (p < 0.99f) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .offset { IntOffset(laneOffsetX, -(p * 220f).toInt()) }
                .alpha((1f - p * p).coerceIn(0f, 1f))
        ) {
            Text(text = reaction.emoji, fontSize = 28.sp)
            if (reaction.senderName.isNotBlank()) {
                Text(
                    text = reaction.senderName,
                    color = Color.White,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PartyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = { Text(label, color = TextSecondary, fontSize = 12.sp) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = EmeraldNeon,
            unfocusedBorderColor = CinemaGlassBorder,
            focusedContainerColor = CinemaSurface,
            unfocusedContainerColor = CinemaSurface
        )
    )
}

@Composable
private fun ToggleChip(
    modifier: Modifier = Modifier,
    active: Boolean,
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .tvFocusableClickable(
                shape = RoundedCornerShape(9.dp),
                focusedBorderColor = EmeraldNeon,
                unfocusedBorderColor = if (active) EmeraldNeon else CinemaGlassBorder,
                onClick = onClick
            )
            .background(if (active) EmeraldPrimary.copy(alpha = 0.22f) else CinemaSurface)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) EmeraldNeon else TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SmallPanelIconBtn(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .tvFocusableClickable(
                shape = RoundedCornerShape(7.dp),
                focusedBorderColor = EmeraldNeon,
                unfocusedBorderColor = CinemaGlassBorder,
                onClick = onClick
            )
            .background(CinemaSurface),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SmallPanelChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .tvFocusableClickable(
                shape = RoundedCornerShape(8.dp),
                focusedBorderColor = EmeraldNeon,
                unfocusedBorderColor = CinemaGlassBorder,
                onClick = onClick
            )
            .background(EmeraldPrimary.copy(alpha = 0.2f))
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(text, color = EmeraldNeon, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
}
