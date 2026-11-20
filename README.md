# 🎬 ChillFilm (2026 Edition) — Standalone Android & Smart TV Cinema Hub

[![Build ChillFilm Android APKs](https://github.com/PrimiasArt/Chillfilm.net/actions/workflows/build-apk.yml/badge.svg)](https://github.com/PrimiasArt/Chillfilm.net/actions/workflows/build-apk.yml)

Ứng dụng xem phim đa quốc gia hiện đại chuẩn 2026 (giao diện **Spatial Glassmorphism Xanh Lục × Tím**) tích hợp trình phát **Chill-Player Engine** (`ExoPlayer Media3` + `LoudnessEnhancer 200% Boost` + `Hardware Equalizer`), hỗ trợ **Sub Thái mặc định / AI Gemini tạo & dịch sub**, **Quản lý đa thiết bị TV từ điện thoại (Cài/Gỡ APK trực tiếp qua Wi-Fi ADB `:5555`)**, và **Tự động cập nhật trong Settings qua GitHub Releases** (chữ ký cố định giữ nguyên toàn bộ dữ liệu).

---

## ✨ Tính năng nổi bật (v3.0+)

1. **Giao diện 2026 Spatial Glassmorphism (Xanh Lục `#10B981` × Tím `#8B5CF6`)**
   - Nền tối sâu, thanh Glass Capsule nổi, hỗ trợ song song **Cảm ứng Điện thoại** và **Remote D-Pad Android TV / TV360 Box**.

2. **Trình phát nâng cấp từ [PrimiasArt/Chill-Player](https://github.com/PrimiasArt/Chill-Player)**
   - **Cử chỉ chạm thông minh**: Vuốt cạnh trái chỉnh độ sáng, vuốt cạnh phải chỉnh âm lượng & **Audio Boost lên tới 200%**, vuốt ngang tua nhanh, chạm đúp 2 bên ±10 giây.
   - **DSP Âm thanh phần cứng**: Bộ cân bằng âm thanh 5 băng tần (**Chuẩn / Tăng Bass / Giọng nói rõ / Điện ảnh**).
   - **Khóa cảm ứng (Screen Lock)**, **Picture-in-Picture (PiP)**, **Xoay ngang Full**, **Tốc độ phát (`0.5x–2.0x`)**, **Tỉ lệ khung hình (`Fit / Fill / Stretch / 16:9 / 4:3`)** và **Hẹn giờ tắt**.

3. **App Android Độc Lập Quản Lý TV & Chế Độ Kép**
   - **Quét Wi-Fi LAN (`:5555` & `:8899`)** phát hiện các Smart TV / TV Box trong nhà.
   - **Cài mới / Cập nhật / Gỡ bỏ App trên TV trực tiếp từ điện thoại Android** bằng giao thức ADB TCP thuần Kotlin (không cần máy tính).
   - **Lưu cố định API Key Gemini trên App Android**: Tự động đồng bộ Key & cấu hình phụ đề sang RAM của TV đang kết nối.
   - **Chế độ kép linh hoạt**: Xem trực tiếp trên điện thoại hoặc đẩy toàn bộ phim/luồng & điều khiển cảm ứng màn hình TV đang chọn.

4. **Tự động Build & Kiểm Tra Cập Nhật Ngay Trong Cài Đặt**
   - Mỗi lần push lên `main`, **GitHub Actions** tự chạy Unit Tests, tăng số build (`versionCode = 100 + RUN_NUM`, `versionName = 3.0.RUN_NUM`) và phát hành APK trên **GitHub Releases** với keystore cố định (`chillfilm.keystore`) giúp **cài đè không mất API Key hay dữ liệu cũ**.
   - Vào **Cài đặt -> 0. Phiên Bản ChillFilm & Kiểm Tra Cập Nhật** để tải và cập nhật 1 chạm cho cả Điện thoại và TV.

---

## 📦 Tải về & Cài đặt
- Truy cập mục **[Releases](https://github.com/PrimiasArt/Chillfilm.net/releases/tag/android-apk-latest)** để tải **`ChillFilm-latest.apk`** (cài được cho cả Điện thoại Android & TV Box).
