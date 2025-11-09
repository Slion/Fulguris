param(
    [Parameter(Mandatory=$true)]
    [string]$Language,

    [Parameter(Mandatory=$true)]
    [string]$StringId,

    [Parameter(Mandatory=$true)]
    [string]$NewValue
)

$filePath = "app\src\main\res\values-$Language\strings.xml"

if (-not (Test-Path $filePath)) {
    Write-Host "File not found: $filePath" -ForegroundColor Red
    exit 1
}

# Read the file content
$content = Get-Content $filePath -Raw -Encoding UTF8

# Escape special regex characters in the string ID
$escapedId = [regex]::Escape($StringId)

# Pattern to match the string entry
$pattern = "(<string name=`"$escapedId`">)([^<]*)(</string>)"

# Check if the string exists
if ($content -match $pattern) {
    # Replace the string value
    $content = $content -replace $pattern, "`$1$NewValue`$3"

    # Write back to file with UTF-8 encoding (no BOM)
    $utf8NoBom = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($filePath, $content, $utf8NoBom)

    Write-Host "Updated $Language : $StringId" -ForegroundColor Green
} else {
    Write-Host "String ID not found: $StringId" -ForegroundColor Red
    exit 1
}

