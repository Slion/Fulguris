# GitHub Copilot Instructions for Fulguris L10N

## Project Context

This is **Fulguris Web Browser**, an Android application with localization support for 37 languages. You are assisting with the **Thai (th-rTH) translation** project.

## Your Role

When the user asks to "work on Thai translation" or similar requests, you should:

1. **Check current status** using `python l10n.py --check th-rTH`
2. **Identify untranslated strings** from the check output
3. **Provide Thai translations** for the untranslated strings
4. **Apply translations** using the l10n.py tool (PowerShell syntax)
5. **Verify success** and continue with next batch

## Complete Documentation

📖 **For ALL translation instructions, see [L10N.md](../L10N.md)**

The L10N.md file contains everything you need:
- **Current translation status** for all 37 languages including Thai
- **Complete l10n.py tool documentation** with all commands
- **Translation workflow** (check → translate → apply → verify)
- **Android XML escaping rules** (CRITICAL: use `\'` not `&apos;`)
- **PowerShell syntax and quoting** (use here-strings for complex XML)
- **Validation rules** and error handling
- **Plural forms** handling
- **Special cases** (XML tags, near-matches, multi-line strings)

## Critical Reminders

⚠️ **Always use PowerShell syntax** - This is a Windows project!

⚠️ **Work in batches** - Translate 10-20 strings at a time for efficiency

⚠️ **Read L10N.md first** - All technical details are documented there

## Quick Start

```powershell
# Check what needs translation
python l10n.py --check th-rTH

# Translate simple strings (batch)
python l10n.py --set th-rTH string_id1 "คำแปล1" string_id2 "คำแปล2"

# Translate complex XML (use here-string with --raw)
$value = @'
ข้อความ <xliff:g id="example">%s</xliff:g>
'@
python l10n.py --raw --set th-rTH string_id $value
```

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

