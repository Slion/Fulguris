# Capture screenshots for Google Play TV listing
# Usage: powershell -ExecutionPolicy Bypass -File scripts/tools/capture_tv_screenshots.ps1

$device = "192.168.178.37:5555"
$pkg = "net.slions.fulguris.full.agent.debug"
$outDir = "D:\Dev\GitHub\Slion\Fulguris\fastlane\metadata\android\en-US\images\tvScreenshots"

if (!(Test-Path $outDir)) {
    New-Item -ItemType Directory -Path $outDir -Force | Out-Null
}

$urls = @(
    @("pluto.tv", "01_pluto_tv"),
    @("tubi.tv", "02_tubi"),
    @("peacocktv.com", "03_peacock"),
    @("youtube.com", "04_youtube")
)

foreach ($item in $urls) {
    $url = $item[0]
    $name = $item[1]
    Write-Host "`n=== Navigating to $url ==="

    # Navigate to URL via intent
    adb -s $device shell am start -a android.intent.action.VIEW -d "https://$url" $pkg
    Start-Sleep -Seconds 8

    # Capture screenshot
    adb -s $device exec-out screencap -p | Set-Content "$outDir\$name.png" -Encoding Byte
    Write-Host "Saved: $name.png"

    # Wait between captures
    Start-Sleep -Seconds 3
}

Write-Host "`nDone! Screenshots saved to $outDir"
