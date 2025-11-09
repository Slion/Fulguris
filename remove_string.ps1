# PowerShell script to remove string entries from all strings.xml files in the Fulguris project

param(
    [Parameter(Mandatory=$false, Position=0, HelpMessage="Name of the string resource to remove (e.g., 'hphosts_ad_server_list'), or array of names")]
    [string[]]$StringNames
)

# If no parameters provided, show usage
if ($StringNames.Count -eq 0) {
    Write-Host "Usage: .\remove_string.ps1 <StringName> [<StringName2> ...]" -ForegroundColor Yellow
    Write-Host "   or: .\remove_string.ps1 -StringNames @('string1', 'string2', ...)" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Example: .\remove_string.ps1 'http_proxy' 'manual_proxy'" -ForegroundColor Cyan
    exit 1
}

Write-Host "Starting removal of $($StringNames.Count) string(s)..." -ForegroundColor Green
Write-Host "Strings to remove: $($StringNames -join ', ')" -ForegroundColor Cyan
Write-Host ""

# Find all strings.xml files in the app/src/main/res directory
$stringsFiles = Get-ChildItem -Path "app\src\main\res" -Recurse -Filter "strings.xml" -File

$totalFilesModified = 0
$totalLinesRemoved = 0

# UTF-8 without BOM encoding
$utf8NoBom = New-Object System.Text.UTF8Encoding $false

# Process each string name
foreach ($StringName in $StringNames) {
    Write-Host "Processing string: '$StringName'" -ForegroundColor Magenta

    $filesModified = 0
    $linesRemoved = 0

    foreach ($file in $stringsFiles) {
        # Read the raw file content with proper UTF-8 encoding
        $rawContent = [System.IO.File]::ReadAllText($file.FullName, $utf8NoBom)

        # Detect line ending style (CRLF vs LF)
        $lineEnding = if ($rawContent -match "`r`n") { "`r`n" } else { "`n" }

        # Split content into lines
        $lines = $rawContent -split "(`r`n|`n)"

        # Check if the file contains the string to remove (exact match in name attribute)
        $hasTarget = $false
        $pattern = '<string\s+name="' + [regex]::Escape($StringName) + '"'
        foreach ($line in $lines) {
            if ($line -match $pattern) {
                $hasTarget = $true
                break
            }
        }

        if ($hasTarget) {
            # Filter out lines containing the target string (exact name match only)
            $newLines = @()
            for ($i = 0; $i -lt $lines.Length; $i++) {
                $line = $lines[$i]
                # Skip the line if it matches the exact string name
                if ($line -notmatch $pattern) {
                    $newLines += $line
                } else {
                    # If we removed a content line, also skip the following line ending
                    if ($i + 1 -lt $lines.Length -and $lines[$i + 1] -match "^(`r`n|`n)$") {
                        $i++
                    }
                    $linesRemoved++
                }
            }

            # Join the lines back together
            $content = $newLines -join ""

            # Write the modified content back to the file using UTF-8 without BOM
            [System.IO.File]::WriteAllText($file.FullName, $content, $utf8NoBom)

            $filesModified++
        }
    }

    Write-Host "  '$StringName': $filesModified files modified, $linesRemoved lines removed" -ForegroundColor Yellow
    $totalFilesModified += $filesModified
    $totalLinesRemoved += $linesRemoved
}

Write-Host ""
Write-Host "Summary:" -ForegroundColor Green
Write-Host "  Total unique files modified: $totalFilesModified" -ForegroundColor White
Write-Host "  Total lines removed: $totalLinesRemoved" -ForegroundColor White
Write-Host ""
Write-Host "Done!" -ForegroundColor Green
