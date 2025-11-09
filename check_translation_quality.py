import re
import sys
from pathlib import Path

# Check for command-line argument
show_all_for_lang = None
if len(sys.argv) > 1:
    show_all_for_lang = sys.argv[1]
    print(f"Will show ALL issues for language: {show_all_for_lang}\n")

# Parse main English strings.xml
main_strings_file = Path('app/src/main/res/values/strings.xml')
with open(main_strings_file, 'r', encoding='utf-8') as f:
    content = f.read()

# Build a dictionary of English strings
english_strings = {}
for line in content.split('\n'):
    match = re.search(r'<string name="([^"]+)">(.+?)</string>', line)
    if match:
        string_name = match.group(1)
        string_value = match.group(2)
        english_strings[string_name] = string_value

print(f"Loaded {len(english_strings)} English strings")

# Get all language directories
res_dir = Path('app/src/main/res')
lang_dirs = [d for d in res_dir.glob('values-*')
             if d.is_dir()
             and 'night' not in d.name
             and 'v27' not in d.name
             and 'v30' not in d.name
             and 'en-' not in d.name]  # Exclude English variants

print(f"Checking {len(lang_dirs)} non-English languages for translation quality\n")
print("="*80)
print("TRANSLATION QUALITY CHECK")
print("="*80)

issues_found = {}

for lang_dir in sorted(lang_dirs):
    strings_file = lang_dir / 'strings.xml'

    if not strings_file.exists():
        continue

    lang_name = lang_dir.name.replace('values-', '')
    lang_issues = []

    with open(strings_file, 'r', encoding='utf-8') as f:
        trans_content = f.read()

    # Check each translated string
    for line in trans_content.split('\n'):
        match = re.search(r'<string name="([^"]+)">(.+?)</string>', line)
        if match:
            string_name = match.group(1)
            translated_value = match.group(2)

            # Skip if not in English reference (might be language-specific)
            if string_name not in english_strings:
                continue

            english_value = english_strings[string_name]

            # Check 1: Exact match with English (likely untranslated)
            # But exclude short strings, proper nouns, and international technical terms
            international_terms = {
                'WebView', 'Android', 'iOS', 'Linux', 'macOS', 'Windows', 'Desktop', 'Mobile',
                'JavaScript', 'Cookies', 'Session', 'Sessions', 'Port:', 'Info', 'Verbose',
                'Assert', 'Debug', 'LeakCanary', 'Normal', 'Options', 'Incognito',
                'Configuration', 'Portrait', 'Landscape', 'Introduction', 'Image',
                'System', 'Default', 'Local', 'Error', 'Parent'
            }

            if (translated_value == english_value and
                len(english_value) > 3 and
                english_value not in international_terms and
                not string_name.startswith('accent_') and
                not string_name.startswith('pref_category') and  # Category refs
                not string_name.startswith('settings_title_portrait') and
                not string_name.startswith('settings_title_landscape') and
                not '@string/' in translated_value and  # String references
                string_name not in ['android_open_source_project', 'jsoup', 'infinity', 'search_action']):
                lang_issues.append(f"  Untranslated: {string_name} = '{english_value}'")

            # Check 2: Placeholder consistency
            english_placeholders = re.findall(r'%[sd\d]|\{[^}]+\}|<xliff:g[^>]*>.*?</xliff:g>', english_value)
            trans_placeholders = re.findall(r'%[sd\d]|\{[^}]+\}|<xliff:g[^>]*>.*?</xliff:g>', translated_value)

            if len(english_placeholders) != len(trans_placeholders):
                lang_issues.append(f"  Placeholder mismatch: {string_name}")
                lang_issues.append(f"    English: {english_placeholders}")
                lang_issues.append(f"    Translation: {trans_placeholders}")

    if lang_issues:
        issues_found[lang_name] = lang_issues

# Report findings
if issues_found:
    print("\nPotential translation issues found:\n")

    for lang_name in sorted(issues_found.keys()):
        issues = issues_found[lang_name]
        print(f"\n{lang_name} ({len(issues)} issues):")

        # Show all issues if this is the requested language, otherwise show first 20
        if show_all_for_lang and lang_name == show_all_for_lang:
            for issue in issues:
                print(issue)
            print(f"\n*** Showing ALL {len(issues)} issues for {lang_name} ***")
        else:
            for issue in issues[:20]:  # Show first 20
                print(issue)
            if len(issues) > 20:
                print(f"  ... and {len(issues) - 20} more issues")
else:
    print("\nNo translation quality issues detected!")
    print("All translations appear to be properly localized.")

print("\n" + "="*80)
print("SUMMARY")
print("="*80)
print(f"Languages checked: {len(lang_dirs)}")
print(f"Languages with potential issues: {len(issues_found)}")
print(f"Languages with clean translations: {len(lang_dirs) - len(issues_found)}")
print("\nNote: Some 'untranslated' strings may be intentional (proper nouns, etc.)")
print("\nUsage: python check_translation_quality.py [language_code]")
print("  Example: python check_translation_quality.py uk-rUA")
print("  (Shows ALL issues for specified language instead of just first 20)")
print("="*80)

