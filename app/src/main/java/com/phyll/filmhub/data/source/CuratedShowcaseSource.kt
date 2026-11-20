package com.phyll.filmhub.data.source

import com.phyll.filmhub.data.BundledSubtitles
import com.phyll.filmhub.model.ContentType
import com.phyll.filmhub.model.CountryCategory
import com.phyll.filmhub.model.Episode
import com.phyll.filmhub.model.MovieItem
import com.phyll.filmhub.model.ServerGroup

/**
 * Built-in verified HD & Classic Cinema Showcase across countries (TH, CN, US, UK, KR, Global)
 * so the user can hit Play immediately upon opening the app and test Native Thai Subtitles,
 * Dual Subtitles (Thai + Vietnamese/English), Hardsub Mask, and AI Auto-Translation to Thai.
 */
object CuratedShowcaseSource {

    fun getFeaturedMovies(): List<MovieItem> = listOf(
        MovieItem(
            id = "curated_tears_of_steel",
            title = "Tears of Steel — Chiến Binh Ký Ức",
            originalTitle = "Tears of Steel (ไทย + Việt + Eng Sub)",
            year = "2012",
            country = CountryCategory.THAILAND,
            genre = "Khoa học viễn tưởng • Hành động",
            duration = "12 phút",
            quality = "Full HD • Sub ไทย",
            posterUrl = "https://mango.blender.org/wp-content/uploads/2012/09/01_thom_celia_bridge.jpg",
            backdropUrl = "https://mango.blender.org/wp-content/uploads/2012/09/01_thom_celia_bridge.jpg",
            description = "Phim hành động viễn tưởng Live-Action chất lượng Full HD tích hợp sẵn Phụ đề Tiếng Thái (ภาษาไทย) chuẩn Cinema + Tiếng Việt + Tiếng Anh để trải nghiệm ngay Sub Thái mặc định & Sub Kép trên TV360 Box.",
            contentType = ContentType.MOVIE,
            providerId = "curated",
            providerName = "Phyll Showcase",
            hasNativeThaiSub = true,
            servers = listOf(
                ServerGroup(
                    serverName = "HLS Adaptive (Sub Thái + Việt + Anh)",
                    episodes = listOf(
                        Episode(
                            id = "tos_ep1",
                            name = "Bản Full HD",
                            streamUrl = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
                            subtitles = BundledSubtitles.tearsOfSteelSubs()
                        )
                    )
                )
            )
        ),
        MovieItem(
            id = "curated_ai_thai_demo",
            title = "Big Buck Bunny — Thử Nghiệm AI Tự Dịch Sub Thái",
            originalTitle = "AI Auto-Translate English -> ภาษาไทย Demo",
            year = "2008",
            country = CountryCategory.USA,
            genre = "Hoạt hình • Phiêu lưu",
            duration = "10 phút",
            quality = "1080p • AI ไทย",
            posterUrl = "https://peach.blender.org/wp-content/uploads/title_anouncement.jpg",
            backdropUrl = "https://peach.blender.org/wp-content/uploads/title_anouncement.jpg",
            description = "Phim gốc chỉ có phụ đề Tiếng Anh — hệ thống AI của Phyll Film Hub sẽ tự động dịch tức thời sang Tiếng Thái (ภาษาไทย) ở dòng chính và hiển thị song ngữ ở dòng phụ.",
            contentType = ContentType.MOVIE,
            providerId = "curated",
            providerName = "Phyll Showcase",
            hasNativeThaiSub = false,
            servers = listOf(
                ServerGroup(
                    serverName = "Mux Global CDN (1080p HLS)",
                    episodes = listOf(
                        Episode(
                            id = "bbb_ep1",
                            name = "Full Movie (AI Thai Sub)",
                            streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                            subtitles = BundledSubtitles.aiAutoTranslateDemoSubs()
                        )
                    )
                )
            )
        ),
        MovieItem(
            id = "archive_street_angel",
            title = "Thiên Thần Đường Phố (Street Angel)",
            originalTitle = "馬路天使 • Điện ảnh Kinh điển Thượng Hải",
            year = "1937",
            country = CountryCategory.CHINA,
            genre = "Tâm lý • Kinh điển Trung Hoa",
            duration = "91 phút",
            quality = "HD Restored",
            posterUrl = "https://archive.org/services/img/street_angel",
            backdropUrl = "https://archive.org/services/img/street_angel",
            description = "Tác phẩm kinh điển nổi tiếng của điện ảnh Thượng Hải (Trung Quốc) với sự tham gia của Châu Tuyền. Tự động trích xuất phụ đề .SRT và AI dịch sang Tiếng Thái.",
            contentType = ContentType.MOVIE,
            providerId = "archive",
            providerName = "Internet Archive Cinema",
            hasNativeThaiSub = false,
            detailLookupId = "street_angel"
        ),
        MovieItem(
            id = "archive_night_living_dead_1080p",
            title = "Đêm Của Những Xác Sống (Night of the Living Dead)",
            originalTitle = "Night of the Living Dead (1080p Remastered)",
            year = "1968",
            country = CountryCategory.USA,
            genre = "Kinh dị • Giật gân • Kinh điển Mỹ",
            duration = "96 phút",
            quality = "1080p • AI ไทย",
            posterUrl = "https://archive.org/services/img/Night.Of.The.Living.Dead_1080p",
            backdropUrl = "https://archive.org/services/img/Night.Of.The.Living.Dead_1080p",
            description = "Kiệt tác kinh dị viễn tưởng kinh điển của đạo diễn George A. Romero bản phục chế 1080p sắc nét, kèm phụ đề tự động dịch sang Tiếng Thái.",
            contentType = ContentType.MOVIE,
            providerId = "archive",
            providerName = "Internet Archive Cinema",
            hasNativeThaiSub = false,
            detailLookupId = "Night.Of.The.Living.Dead_1080p"
        ),
        MovieItem(
            id = "archive_princess_iron_fan",
            title = "Thiết Phiến Công Chúa (Tây Du Ký Cổ Điển)",
            originalTitle = "鐵扇公主 • Princess Iron Fan",
            year = "1941",
            country = CountryCategory.CHINA,
            genre = "Thần thoại • Phiêu lưu • Trung Quốc",
            duration = "73 phút",
            quality = "Classic",
            posterUrl = "https://archive.org/services/img/princess_iron_fan",
            backdropUrl = "https://archive.org/services/img/princess_iron_fan",
            description = "Bộ phim hoạt hình trường thiên đầu tiên của châu Á lấy cảm hứng từ Tây Du Ký — Tôn Ngộ Không đại chiến Thiết Phiến Công Chúa và Ngưu Ma Vương.",
            contentType = ContentType.MOVIE,
            providerId = "archive",
            providerName = "Internet Archive Cinema",
            hasNativeThaiSub = false,
            detailLookupId = "princess_iron_fan"
        ),
        MovieItem(
            id = "archive_case_frightened_lady",
            title = "Vụ Án Quý Bà Hoảng Loạn",
            originalTitle = "The Case of the Frightened Lady (British Mystery)",
            year = "1940",
            country = CountryCategory.UK,
            genre = "Trinh thám • Bí ẩn • Điện ảnh Anh",
            duration = "81 phút",
            quality = "HD Classic",
            posterUrl = "https://archive.org/services/img/Case_of_the_Frightened_Lady_1940",
            backdropUrl = "https://archive.org/services/img/Case_of_the_Frightened_Lady_1940",
            description = "Phim trinh thám hình sự cổ điển Anh Quốc chuyển thể từ tiểu thuyết của Edgar Wallace tại lâu đài Marks Priory.",
            contentType = ContentType.MOVIE,
            providerId = "archive",
            providerName = "Internet Archive Cinema",
            hasNativeThaiSub = false,
            detailLookupId = "Case_of_the_Frightened_Lady_1940"
        )
    )
}
