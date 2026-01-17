# GitHub Copilot Instructions for Fulguris L10N

## Project Context

This is **Fulguris Web Browser**, an Android application with localization support for 40 languages and Google Play metadata for 33 languages.

## Quick Reference

### Common Commands

```powershell
# Check translation status
python subs\l10n\android\strings.py --check th-rTH

# Get a string in one language
python subs\l10n\android\strings.py --get th-rTH app_name

# Get a string across ALL languages (with language names)
python subs\l10n\android\strings.py --get all app_name
# or
python subs\l10n\android\strings.py --get-all app_name

# Translate ONE string across MULTIPLE languages (RECOMMENDED for same string)
python subs\l10n\android\strings.py --set app_name de-rDE 'Fulguris' fr-rFR 'Fulguris' es-rES 'Fulguris'

# Translate MULTIPLE strings in ONE language (RECOMMENDED for bulk translation)
python subs\l10n\android\strings.py --set-batch th-rTH string1 'คำแปล1' string2 'คำแปล2' string3 'คำแปล3'

# Compile changelogs
python subs\l10n\android\changelogs.py 254
```

### Important Reminders

- **⚠️ ALWAYS check English source text FIRST** - Use `python strings.py --get source <string_id>` to see actual content
- **NEVER translate string ID names** - IDs are technical identifiers (e.g., `download_status_orphaned` ≠ "Orphaned", it's "File not found")
- **Always use PowerShell syntax** on Windows
- **Work in batches** - 10-20 strings at a time for efficiency
- **Check command output** for `[ERROR]` vs `[OK]` messages
- **CRITICAL: Placeholder mismatches cause app crashes!** - Fix `[CRITICAL]` issues immediately
- **Run commands in existing terminal** - Don't spawn new windows
- **Strings must exist in English source** before translation

## Complete Documentation

📖 **For ALL documentation, see:**

1. **[L10N.md](../L10N.md)** - Fulguris-specific status and quick start
2. **[subs/l10n/docs/android/L10N.md](../subs/l10n/docs/android/L10N.md)** - Complete tool documentation including:
   - Prerequisites (UTF-8 terminal setup)
   - Translation workflows
   - Android XML escaping rules (CRITICAL: use `\'` not `&apos;`)
   - PowerShell syntax and quoting
   - Validation rules and error handling
   - Plural forms handling
   - Special cases (XML tags, near-matches, multi-line strings)

3. **[subs/l10n/docs/android/copilot-instructions.md](../subs/l10n/docs/android/copilot-instructions.md)** - Generic AI assistant instructions for Android L10N

## Workflow

When the user asks to work on translations:

1. **Check current status** - `python subs\l10n\android\strings.py --check <lang-code>`
2. **Identify untranslated strings** from the output
3. **Provide translations** for untranslated strings
4. **Apply translations** using `--set` command
5. **CHECK FOR ERRORS** in output - look for `[ERROR]` or `[OK]`
6. **Fix any errors** before continuing
7. **Verify success** and continue with next batch

### Publishing to Google Play

```bash
python subs\l10n\android\publish_google_play.py "S:\Dev\Keys\fulguris-play-store-85a67838990f.json" net.slions.fulguris.full.playstore
```

**Don't stop translation work until complete**, even if it takes many batches. Continue systematically through all untranslated strings.

## PowerShell Quick Guide

**Always use single quotes** - No escaping needed for double quotes or `$` placeholders:

```powershell
# Simple strings
python subs\l10n\android\strings.py --set string_id th-rTH 'คำแปล'

# With placeholders - $ is literal in single quotes
python subs\l10n\android\strings.py --set string_id th-rTH 'ดาวน์โหลด %1$s'

# With double quotes - NO escaping needed!
python subs\l10n\android\strings.py --set string_id th-rTH 'สลับไปที่ "%s"'

# With apostrophes - double them up
python subs\l10n\android\strings.py --set action_dont_allow th-rTH 'Don''t allow'

# Complex XML - single quotes, no escaping!
python subs\l10n\android\strings.py --raw --set string_id th-rTH 'ข้อความ <xliff:g id="example">%1$s</xliff:g>'
```

## Google Play Metadata

### Languages
**33 Google Play listings:** en-US, en-GB, ar, cs-CZ, da-DK, de-DE, el-GR, es-ES, fi-FI, fr-FR, hi-IN, hr, hu-HU, id, it-IT, ja-JP, ko-KR, lt, nl-NL, no-NO, pl-PL, pt-BR, pt-PT, ro, ru-RU, sr, sv-SE, th, tr-TR, uk, vi, zh-CN, zh-TW

### Adding Metadata for New Language

```powershell
# 1. Create directory structure
mkdir fastlane\metadata\android\{google-play-code}
mkdir fastlane\metadata\android\{google-play-code}\changelogs

# 2. Create metadata files
echo "App Name" > fastlane\metadata\android\{google-play-code}\title.txt
echo "Short description" > fastlane\metadata\android\{google-play-code}\short_description.txt
copy fastlane\metadata\android\en-US\full_description.txt fastlane\metadata\android\{google-play-code}\full_description.txt

# 3. Update changelogs.py - add language code to languages list

# 4. Create changelogs
copy fastlane\metadata\android\en-US\changelogs\*.txt fastlane\metadata\android\{google-play-code}\changelogs\
```

## Release Notes

When creating changelogs for new versions:

```powershell
# 1. Find last release tag
git tag --sort=-creatordate | Select-Object -First 5

# 2. Get commits since last tag
git log TAG_NAME..HEAD --pretty=format:"%s" --no-merges | Out-String

# 3. Create changelog in English (fastlane/metadata/android/en-US/changelogs/{version}.txt)
#    Format: Emoji prefix + brief description (🗺️ 🌐 🍪 🚫 📋 ⚙️ 🐛)
#    Example:
#      🧩 Add support for extensions
#      🪲 Fix dark mode override
#      🐛 Various improvements

# 4. Translate to all 33 Play Store languages

# 5. Compile for Play Store
python subs\l10n\android\changelogs.py {version}
```

**Last Updated:** December 22, 2025
