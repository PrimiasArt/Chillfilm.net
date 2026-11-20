package com.phyll.filmhub.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phyll.filmhub.companion.QrCodeGenerator
import com.phyll.filmhub.ui.theme.CinemaObsidian
import com.phyll.filmhub.ui.theme.CinemaSurface
import com.phyll.filmhub.ui.theme.CinemaSurfaceElevated
import com.phyll.filmhub.ui.theme.ElectricCyan
import com.phyll.filmhub.ui.theme.LiveRed
import com.phyll.filmhub.ui.theme.TextPrimary
import com.phyll.filmhub.ui.theme.TextSecondary
import com.phyll.filmhub.ui.theme.ThaiGold
import com.phyll.filmhub.ui.theme.tvFocusableClickable

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompanionQrDialog(
    lanUrl: String?,
    hasSessionGeminiKey: Boolean,
    activeGeminiModel: String,
    promptQrOnStartup: Boolean,
    onTogglePromptOnStartup: (Boolean) -> Unit,
    onWipeSessionKey: () -> Unit,
    onDismiss: () -> Unit
) {
    val closeFocus = remember { FocusRequester() }
    val qrBitmap = remember(lanUrl) {
        lanUrl?.let { QrCodeGenerator.generateQrBitmap(it, 480) }
    }

    LaunchedEffect(Unit) {
        runCatching { closeFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(1.5.dp, ThaiGold.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                .background(CinemaSurface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "📱 Stream App TV Về Điện Thoại & Điều Khiển Cảm Ứng",
                color = ThaiGold,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (hasSessionGeminiKey) Color(0xFF064E3B) else Color(0xFF1E293B)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (hasSessionGeminiKey) {
                        "🔐 ĐÃ NẠP GEMINI KEY TRÊN RAM ($activeGeminiModel) • Thoát App tự xóa"
                    } else {
                        "📺 STREAM MÀN HÌNH TV • 📱 GIAO DIỆN APP ĐT • 🔐 NẠP GEMINI KEY RAM"
                    },
                    color = if (hasSessionGeminiKey) Color(0xFF6EE7B7) else ElectricCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Quét mã QR bằng Camera điện thoại để:\n• 📺 Stream trực tiếp màn hình TV về ĐT & chạm/vuốt ngay trên ĐT\n• 📱 Dùng giao diện App Điện Thoại để chọn tập, tua phim, 1 chạm khớp Sub & xem luôn trên ĐT\n• 🔐 Nạp Gemini API Key tạm thời vào RAM (0 byte lưu ổ cứng)",
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (lanUrl != null && qrBitmap != null) {
                Box(
                    modifier = Modifier
                        .size(196.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = lanUrl,
                        modifier = Modifier.size(176.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CinemaSurfaceElevated)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Mở trên trình duyệt ĐT:", color = TextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = lanUrl,
                        color = ElectricCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            } else {
                Text(
                    text = "Chưa lấy được IP mạng nội bộ. Hãy kiểm tra kết nối Wi-Fi hoặc dây LAN trên TV.",
                    color = Color(0xFFEF4444),
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .tvFocusableClickable(
                            shape = RoundedCornerShape(10.dp),
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = if (promptQrOnStartup) ElectricCyan.copy(alpha = 0.6f) else Color(0xFF374151),
                            onClick = { onTogglePromptOnStartup(!promptQrOnStartup) }
                        )
                        .background(CinemaSurfaceElevated)
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = if (promptQrOnStartup) "Hiện QR khi mở App: BẬT" else "Hiện QR khi mở App: TẮT",
                        color = if (promptQrOnStartup) ElectricCyan else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hasSessionGeminiKey) {
                        Box(
                            modifier = Modifier
                                .tvFocusableClickable(
                                    shape = RoundedCornerShape(10.dp),
                                    focusedBorderColor = LiveRed,
                                    unfocusedBorderColor = LiveRed.copy(alpha = 0.6f),
                                    onClick = onWipeSessionKey
                                )
                                .background(LiveRed.copy(alpha = 0.18f))
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            Text(
                                text = "🧹 Xóa Key RAM",
                                color = Color(0xFFFCA5A5),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .focusRequester(closeFocus)
                            .tvFocusableClickable(
                                shape = RoundedCornerShape(10.dp),
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = ThaiGold,
                                onClick = onDismiss
                            )
                            .background(ThaiGold)
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = if (hasSessionGeminiKey) "Đóng" else "Bỏ Qua / Xem Ngay",
                            color = CinemaObsidian,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}
