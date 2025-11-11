# GitHub Copilot Instructions for Fulguris L10N

## Project Context

This is **Fulguris Web Browser**, an Android application with localization support for 37 languages. You are assisting with the **Thai (th-rTH) translation** project.

## Your Role

When the user asks to "work on Thai translation" or similar requests, you should:

1. **Check current status** using `python l10n.py --check th-rTH`
2. **Identify untranslated strings** from the check output
3. **Provide Thai translations** for the untranslated strings
4. **Apply translations** using the l10n.py tool (PowerShell syntax)
5. **CHECK FOR ERRORS** in command output - look for `[ERROR]` or `[OK]` messages
6. **Fix any errors** before continuing
7. **Verify success** and continue with next batch

**Important:** 
- Run all commands in the existing terminal session. Do not open new terminal windows.
- If you encounter "string does not exist" errors, **STOP and report the issue** to the user. Do not attempt to add strings during translation work.
- **Do not stop translation work until complete**, even if it takes a long time and many batches. Continue working through all untranslated strings systematically.

## Complete Documentation

📖 **For ALL translation instructions, see [L10N.md](../L10N.md)**

The L10N.md file contains everything you need:
- **Current translation status** for all 37 languages including Thai
- **Complete l10n.py tool documentation** with all commands
- **Translation workflow** (check → translate → apply → verify)
- **Android XML escaping rules** (CRITICAL: use `\'` not `&apos;`)
- **PowerShell syntax and quoting** (multiple options explained below)
- **Validation rules** and error handling
- **Plural forms** handling
- **Special cases** (XML tags, near-matches, multi-line strings)

## Critical Reminders

⚠️ **Always use PowerShell syntax** - This is a Windows project!

⚠️ **Work in batches** - Translate 10-20 strings at a time for efficiency

⚠️ **Check command output for errors** - Look for `[ERROR]` vs `[OK]` messages

⚠️ **Don't spawn new terminal windows** - Run commands in the existing terminal session

⚠️ **Never add strings during translation** - If a string doesn't exist, STOP and report the issue to the user

⚠️ **Read L10N.md first** - All technical details are documented there

## PowerShell String Quoting Guide

### Simple Rule: Always Use Single Quotes

**Use single quotes for outer string container** and escape inner double quotes with backslash `\"`:

```powershell
# Simple strings
python l10n.py --set th-rTH string_id 'คำแปล'

# Strings with placeholders (%1$s, %d, etc.) - NO escaping needed!
python l10n.py --set th-rTH string_id 'ดาวน์โหลด %1$s'

# Strings with double quotes inside - escape with backslash
python l10n.py --set th-rTH string_id 'สลับไปที่ \"%s\"'

# Strings with BOTH quotes and placeholders
python l10n.py --set th-rTH string_id 'ดาวน์โหลด \"%1$s\"'

# Strings with newlines (\n) - works perfectly in single quotes
python l10n.py --set th-rTH string_id 'บรรทัด1\n\nบรรทัด2'

# Complex XML - same rule, single quotes with \"
python l10n.py --raw --set th-rTH string_id 'ข้อความ <xliff:g id=\"example\">%1$s</xliff:g>'
```

**Why single quotes work perfectly:**
- PowerShell single quotes treat everything literally (no variable expansion)
- Dollar signs `$` in placeholders like `%1$s` work perfectly - no escaping needed
- Backslash `\"` escapes inner double quotes - PowerShell passes `\` to Python, which receives actual quotes
- Newlines `\n` work correctly - PowerShell passes `\` and `n` as separate characters, Python writes `\n` to XML
- Only inner double quotes need escaping with backslash `\"`

**Important:** PowerShell passes the backslashes literally to Python. The Python script receives them and writes them correctly to the Android XML format.

## Quick Start Examples

```powershell
# Check what needs translation
python l10n.py --check th-rTH

# Simple strings (batch) - use single quotes
python l10n.py --set th-rTH string_id1 'คำแปล1' string_id2 'คำแปล2'

# Strings with placeholders - single quotes, no escaping needed for $
python l10n.py --set th-rTH dialog_download 'คุณต้องการดาวน์โหลดไฟล์นี้หรือไม่? (%1$s)'

# Strings with quotes - single quotes, escape inner quotes with backslash
python l10n.py --set th-rTH session_switched 'สลับไปที่ \"%s\"'

# Strings with BOTH quotes and placeholders - single quotes with \"
python l10n.py --set th-rTH string_id 'ดาวน์โหลด \"%1$s\"'

# Complex XML - single quotes with backslash for quotes
python l10n.py --raw --set th-rTH string_id 'ข้อความ <xliff:g id=\"example\">%s</xliff:g>'

# Complex XML with placeholders - still single quotes!
python l10n.py --raw --set th-rTH match_x_of_n 'ตรงกัน <xliff:g id=\"current_match\" example=\"1\">%1$d</xliff:g> จาก <xliff:g id=\"match_count\" example=\"10\">%2$d</xliff:g>'
```

## Error Checking - IMPORTANT!

**Always examine command output** for success/failure indicators:

✅ **Success looks like:**
```
[OK] string_id1
[OK] string_id2
Successfully updated: 2
```

❌ **Error looks like:**
```
[ERROR] XML Validation Failed for 'string_id'!
  Attribute value not quoted: id=example
[ERROR] String 'string_id' does not exist
```

**Common Errors & Fixes:**

| Error | Cause | Fix |
|-------|-------|-----|
| `Attribute value not quoted` | PowerShell stripped quotes from XML | Use here-string with `--raw` |
| `Entity '&apos;' detected` | Wrong entity for Android | Use `\'` instead |
| `Placeholder mismatch` | Missing or wrong placeholders | Match English placeholders exactly |
| `String does not exist` | String not in language file | **STOP** - Report to user, don't add! |

**If you see "String does not exist" error:**
1. **STOP translation work immediately**
2. **Report the issue to the user** - the string may be missing from English source or file is out of sync
3. **DO NOT attempt to add the string** - adding strings should only be done deliberately via `--add` command
4. **Wait for user guidance** before continuing

**For other errors:**
1. **Stop immediately** - don't continue with more translations
2. **Read the error message** carefully
3. **Fix the issue** (usually quoting or escaping)
4. **Retry the failed string**
5. **Only continue** after seeing `[OK]`

## Thai Language Guidelines

- **Formality:** Use polite/formal register (ภาษาเป็นทางการ) for software UI
- **Technical terms:** Keep widely-understood English terms (JavaScript, Cookies, WebView)
- **Consistency:** Use same Thai words for same English concepts throughout
- **Natural phrasing:** Translate meaning, not word-by-word

**Common translations:**
Settings → การตั้งค่า | Enable → เปิดใช้งาน | Disable → ปิดใช้งาน | Delete → ลบ | Cancel → ยกเลิก | Save → บันทึก | OK → ตกลง

---

**For complete instructions, commands, examples, and troubleshooting: See [L10N.md](../L10N.md)**

**Last Updated:** November 11, 2025  
**Maintained by:** Fulguris Development Team

