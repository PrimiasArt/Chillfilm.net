package com.phyll.filmhub.data

import com.phyll.filmhub.model.ExternalSubtitleRef

/**
 * Authentic multi-language subtitle tracks (Thai, Vietnamese, English) bundled for
 * instant zero-latency testing of Thai-First typography, Dual Subtitles, Hardsub Mask,
 * and Sync Offset on TV360 Box and Android Mobile.
 */
object BundledSubtitles {

    val TEARS_OF_STEEL_THAI_SRT = """
1
00:00:01,200 --> 00:00:05,500
ยินดีต้อนรับสู่ Phyll Film Hub — โรงภาพยนตร์ส่วนตัวบนกล่อง TV360

2
00:00:05,800 --> 00:00:10,500
ระบบคำบรรยายภาษาไทยอัตโนมัติ แสดงสระและวรรณยุกต์ครบถ้วนชัดเจน

3
00:00:11,000 --> 00:00:15,200
เธอบอกว่าอยากเป็นนักบินอวกาศ และออกไปสำรวจดวงดาวบนท้องฟ้า

4
00:00:15,600 --> 00:00:20,400
แต่ผมแค่อยากอยู่ตรงนี้ ในเมืองอัมสเตอร์ดัมที่เงียบสงบ

5
00:00:20,900 --> 00:00:26,000
คุณสามารถปรับขนาดฟอนต์ สีขอบตัวอักษร และเปิดซับสองภาษาได้ทันที

6
00:00:26,500 --> 00:00:32,000
ความทรงจำในวันนั้นยังคงชัดเจน แม้เวลาจะผ่านไปสี่สิบปีแล้วก็ตาม

7
00:00:32,500 --> 00:00:38,000
ทุกสัญญาณพร้อมทำงานแล้ว เตรียมเปิดระบบเชื่อมต่อความทรงจำเดี๋ยวนี้!

8
00:00:38,500 --> 00:00:45,000
ไม่ว่าจะเกิดอะไรขึ้น เราต้องปกป้องโลกใบนี้เอาไว้ให้ได้
    """.trimIndent()

    val TEARS_OF_STEEL_VI_SRT = """
1
00:00:01,200 --> 00:00:05,500
Chào mừng đến với Phyll Film Hub — Rạp phim tại gia trên TV360 Box

2
00:00:05,800 --> 00:00:10,500
Hệ thống phụ đề Tiếng Thái mặc định, hiển thị trọn vẹn mọi dấu thanh sắc nét

3
00:00:11,000 --> 00:00:15,200
Cô ấy nói rằng mình muốn trở thành phi hành gia và khám phá những vì sao

4
00:00:15,600 --> 00:00:20,400
Nhưng tôi chỉ muốn ở lại đây, giữa thành phố Amsterdam yên bình này

5
00:00:20,900 --> 00:00:26,000
Bạn có thể chỉnh cỡ chữ, màu viền và bật Sub Kép ngay khi đang xem

6
00:00:26,500 --> 00:00:32,000
Ký ức ngày hôm ấy vẫn vẹn nguyên dù bốn mươi năm đã trôi qua

7
00:00:32,500 --> 00:00:38,000
Mọi tín hiệu đã sẵn sàng, kích hoạt hệ thống kết nối ký ức ngay bây giờ!

8
00:00:38,500 --> 00:00:45,000
Dù chuyện gì xảy ra, chúng ta cũng phải bảo vệ thế giới này
    """.trimIndent()

    val TEARS_OF_STEEL_EN_SRT = """
1
00:00:01,200 --> 00:00:05,500
Welcome to Phyll Film Hub — Your Personal Cinema on TV360 Box

2
00:00:05,800 --> 00:00:10,500
Thai-first automatic subtitle engine with crisp tone mark typography

3
00:00:11,000 --> 00:00:15,200
She said she wanted to be an astronaut and explore the stars above

4
00:00:15,600 --> 00:00:20,400
But I just wanted to stay right here in quiet Amsterdam

5
00:00:20,900 --> 00:00:26,000
Customize font size, outline colors, and Dual Subtitles live while watching

6
00:00:26,500 --> 00:00:32,000
That day's memory remains crystal clear even forty years later

7
00:00:32,500 --> 00:00:38,000
All signals are ready — activate the memory bridge right now!

8
00:00:38,500 --> 00:00:45,000
No matter what happens, we must protect this world together
    """.trimIndent()

    // English-only subtitle track to showcase real-time AI Auto-Translation into Thai!
    val AI_DEMO_ENGLISH_ONLY_SRT = """
1
00:00:01,000 --> 00:00:05,200
Morning breaks over the green forest valley.

2
00:00:05,600 --> 00:00:10,200
Notice how this English subtitle is automatically translated into Thai!

3
00:00:10,600 --> 00:00:15,500
You can see both the Thai main subtitle and Vietnamese or English below.

4
00:00:16,000 --> 00:00:21,500
Press the Subtitle button on your TV360 remote to customize fonts and colors.

5
00:00:22,000 --> 00:00:28,000
Adventure awaits around every corner when courage leads the way.
    """.trimIndent()

    fun tearsOfSteelSubs(): List<ExternalSubtitleRef> = listOf(
        ExternalSubtitleRef(
            id = "tos_th",
            label = "ภาษาไทย (Sub Thái Chuẩn)",
            languageCode = "th",
            urlOrContent = TEARS_OF_STEEL_THAI_SRT,
            isRawContent = true
        ),
        ExternalSubtitleRef(
            id = "tos_vi",
            label = "Tiếng Việt",
            languageCode = "vi",
            urlOrContent = TEARS_OF_STEEL_VI_SRT,
            isRawContent = true
        ),
        ExternalSubtitleRef(
            id = "tos_en",
            label = "English",
            languageCode = "en",
            urlOrContent = TEARS_OF_STEEL_EN_SRT,
            isRawContent = true
        )
    )

    fun aiAutoTranslateDemoSubs(): List<ExternalSubtitleRef> = listOf(
        ExternalSubtitleRef(
            id = "bbb_en_ai",
            label = "English (Test AI Tự Dịch Sang Thái)",
            languageCode = "en",
            urlOrContent = AI_DEMO_ENGLISH_ONLY_SRT,
            isRawContent = true
        )
    )
}
