# PowerShell script to remove string entries from all strings.xml files in the Fulguris project

param(
    [Parameter(Mandatory=$true, Position=0, HelpMessage="Name of the string resource to remove (e.g., 'hphosts_ad_server_list')")]
    [string]$StringName
)

Write-Host "Starting removal of '$StringName' entries..." -ForegroundColor Green

# Find all strings.xml files in the app/src/main/res directory
$stringsFiles = Get-ChildItem -Path "app\src\main\res" -Recurse -Filter "strings.xml" -File

$filesModified = 0
$linesRemoved = 0

# UTF-8 without BOM encoding
$utf8NoBom = New-Object System.Text.UTF8Encoding $false

foreach ($file in $stringsFiles) {
    Write-Host "Processing: $($file.FullName)" -ForegroundColor Cyan

    # Read the raw file content with proper UTF-8 encoding
    $rawContent = [System.IO.File]::ReadAllText($file.FullName, $utf8NoBom)

    # Detect line ending style (CRLF vs LF)
    $lineEnding = if ($rawContent -match "`r`n") { "`r`n" } else { "`n" }

    # Split content into lines
    $lines = $rawContent -split "(`r`n|`n)"

    # Check if the file contains the string to remove
    $hasTarget = $false
    foreach ($line in $lines) {
        if ($line -match [regex]::Escape($StringName)) {
            $hasTarget = $true
            break
        }
    }

    if ($hasTarget) {
        # Filter out lines containing the target string AND empty lines that resulted from removal
        $newLines = @()
        for ($i = 0; $i -lt $lines.Length; $i++) {
            $line = $lines[$i]
            # Skip the line if it contains the target string
            if ($line -notmatch [regex]::Escape($StringName)) {
                $newLines += $line
            } else {
                # If we removed a content line, also skip the following line ending
                if ($i + 1 -lt $lines.Length -and $lines[$i + 1] -match "^(`r`n|`n)$") {
                    $i++
                }
            }
        }

        # Join the lines back together
        $content = $newLines -join ""

        # Write the modified content back to the file using UTF-8 without BOM
        [System.IO.File]::WriteAllText($file.FullName, $content, $utf8NoBom)

        $filesModified++
        $linesRemoved++
        Write-Host "  Successfully removed '$StringName' from $($file.Name)" -ForegroundColor Yellow
    } else {
        Write-Host "  - No '$StringName' found in $($file.Name)" -ForegroundColor Gray
    }
}

Write-Host ""
Write-Host "Summary:" -ForegroundColor Green
Write-Host "  Files modified: $filesModified" -ForegroundColor White
Write-Host "  Lines removed: $linesRemoved" -ForegroundColor White
Write-Host ""
Write-Host "Done!" -ForegroundColor Green
