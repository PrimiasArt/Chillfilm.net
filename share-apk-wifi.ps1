$ErrorActionPreference = "Continue"
$port = 8090
$apkPath = Join-Path $PSScriptRoot "dist\PhyllFilmHub-universal.apk"

if (-not (Test-Path $apkPath)) {
    Write-Host "Khong tim thay file APK: $apkPath" -ForegroundColor Red
    Read-Host "Nhan Enter de dong"
    exit 1
}

$ip = (Get-NetIPAddress -AddressFamily IPv4 |
    Where-Object { $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.254.*" } |
    Select-Object -First 1 -ExpandProperty IPAddress)
if (-not $ip) { $ip = "localhost" }

$apkBytes = [System.IO.File]::ReadAllBytes($apkPath)
$listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Any, $port)
$listener.Start()

Write-Host "==============================================================" -ForegroundColor Yellow
Write-Host "  MAY CHU TAI APK NOI BO QUA WI-FI DA SAN SANG!" -ForegroundColor Green
Write-Host "==============================================================" -ForegroundColor Yellow
Write-Host ""
Write-Host "  -> Tren Dien thoai Android (Chrome) hoac App Downloader tren TV360,"
Write-Host "     hay nhap dia chi sau:"
Write-Host ""
Write-Host "     Trang tai nhanh :  http://$($ip):$port" -ForegroundColor Cyan
Write-Host "     Link APK truc tiep: http://$($ip):$port/tv.apk" -ForegroundColor Cyan
Write-Host ""
Write-Host "  (Nhan Ctrl + C hoac dong cua so nay sau khi tai xong)" -ForegroundColor Gray
Write-Host ""

$html = @"
<!DOCTYPE html>
<html lang="vi">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Tải Phyll Film Hub</title>
<style>
  body { background:#0B0E14; color:#FFF; font-family:sans-serif; text-align:center; padding:40px 18px; }
  .box { max-width:440px; margin:0 auto; background:#151A24; border:2px solid #FFB800; border-radius:16px; padding:28px 20px; }
  h1 { color:#FFB800; font-size:22px; margin:0 0 10px; }
  p { color:#9CA3AF; font-size:14px; line-height:1.5; }
  a.btn { display:inline-block; margin-top:16px; padding:14px 28px; background:#FFB800; color:#0B0E14; font-weight:800; font-size:16px; text-decoration:none; border-radius:10px; }
</style>
</head>
<body>
  <div class="box">
    <h1>🎬 Phyll Film Hub (13.5 MB)</h1>
    <p>Bản Universal cài đặt cho cả <b>TV360 Box</b> & <b>Điện thoại Android</b> (Mặc định Sub Thái + AI Dịch Sub + Sub Kép).</p>
    <a class="btn" href="/tv.apk">⬇ TẢI FILE APK NGAY</a>
  </div>
</body>
</html>
"@
$htmlBytes = [System.Text.Encoding]::UTF8.GetBytes($html)

try {
    while ($true) {
        $client = $listener.AcceptTcpClient()
        try {
            $stream = $client.GetStream()
            $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::ASCII, $false, 1024, $true)
            $reqLine = $reader.ReadLine()
            while ($true) {
                $h = $reader.ReadLine()
                if ([string]::IsNullOrEmpty($h)) { break }
            }
            Write-Host "[Ket noi] $reqLine" -ForegroundColor DarkCyan
            if ($reqLine -match "GET\s+/tv\.apk") {
                $header = "HTTP/1.1 200 OK`r`nContent-Type: application/vnd.android.package-archive`r`nContent-Disposition: attachment; filename=`"PhyllFilmHub-universal.apk`"`r`nContent-Length: $($apkBytes.Length)`r`nConnection: close`r`n`r`n"
                $hBytes = [System.Text.Encoding]::ASCII.GetBytes($header)
                $stream.Write($hBytes, 0, $hBytes.Length)
                $stream.Write($apkBytes, 0, $apkBytes.Length)
            } else {
                $header = "HTTP/1.1 200 OK`r`nContent-Type: text/html; charset=utf-8`r`nContent-Length: $($htmlBytes.Length)`r`nConnection: close`r`n`r`n"
                $hBytes = [System.Text.Encoding]::ASCII.GetBytes($header)
                $stream.Write($hBytes, 0, $hBytes.Length)
                $stream.Write($htmlBytes, 0, $htmlBytes.Length)
            }
            $stream.Flush()
        } catch {
        } finally {
            $client.Close()
        }
    }
} finally {
    $listener.Stop()
}
