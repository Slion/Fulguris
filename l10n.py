import re
import sys
from pathlib import Path

# Fix encoding for Windows console
if sys.platform == 'win32':
    try:
        import io
        sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
    except:
        pass

def show_help():
    """Display help information about available commands."""
    print("=" * 80)
    print("LOCALIZATION (L10N) CHECK TOOL")
    print("=" * 80)
    print("\nSupported Commands:")
    print("\n  python l10n.py")
    print("    Check all languages (shows first 20 issues per language)")
    print("\n  python l10n.py <language_code>")
    print("    Check specific language and show ALL issues")
    print("    Examples:")
    print("      python l10n.py ru-rRU     # Check Russian")
    print("      python l10n.py uk-rUA     # Check Ukrainian")
    print("      python l10n.py fr-rFR     # Check French")
    print("      python l10n.py de-rDE     # Check German")
    print("\n  python l10n.py --list")
    print("    List all available language codes")
    print("\n  python l10n.py --help, -h")
    print("    Show this help message")
    print("\n  python l10n.py --summary")
    print("    Show only summary statistics for all languages")
    print("\n  python l10n.py --set <lang> <string_id> <value>")
    print("    Set a string value for a specific language")
    print("    Example:")
    print("      python l10n.py --set ru-rRU locale_app_name \"Веб-браузер Fulguris\"")
    print("\n  python l10n.py --get <lang> <string_id>")
    print("    Get a string value from a specific language")
    print("    Example:")
    print("      python l10n.py --get ru-rRU locale_app_name")
    print("\nOutput Information:")
    print("  • Untranslated strings that match English")
    print("  • Placeholder mismatches (e.g., missing %s, %1$s)")
    print("  • Technical terms (Android, iOS, Linux, etc.) are excluded")
    print("=" * 80)
    sys.exit(0)

def get_string_value(language, string_id):
    """Get a string value from a specific language file."""
    file_path = Path(f'app/src/main/res/values-{language}/strings.xml')

    if not file_path.exists():
        print(f"Error: Language file not found: {file_path}")
        print(f"Run 'python l10n.py --list' to see available languages")
        sys.exit(1)

    # Read the file content
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
    except Exception as e:
        print(f"Error reading file: {e}")
        sys.exit(1)

    # Escape special regex characters in the string ID
    escaped_id = re.escape(string_id)

    # Pattern to match the string entry
    pattern = f'<string name="{escaped_id}">([^<]*)</string>'

    # Search for the string
    match = re.search(pattern, content)
    if not match:
        print(f"Error: String ID '{string_id}' not found in {file_path}")
        sys.exit(1)

    value = match.group(1)
    print(f"{language}:{string_id}")
    print(f"  {value}")
    sys.exit(0)

def set_string_value(language, string_id, new_value):
    """Set a string value in a specific language file."""
    file_path = Path(f'app/src/main/res/values-{language}/strings.xml')

    if not file_path.exists():
        print(f"Error: Language file not found: {file_path}")
        print(f"Run 'python l10n.py --list' to see available languages")
        sys.exit(1)

    # Read the file content
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
    except Exception as e:
        print(f"Error reading file: {e}")
        sys.exit(1)

    # Escape special regex characters in the string ID
    escaped_id = re.escape(string_id)

    # Pattern to match the string entry
    pattern = f'(<string name="{escaped_id}">)([^<]*)(</string>)'

    # Check if the string exists
    if not re.search(pattern, content):
        print(f"Error: String ID '{string_id}' not found in {file_path}")
        sys.exit(1)

    # Replace the string value
    content = re.sub(pattern, f'\\1{new_value}\\3', content)

    # Write back to file with UTF-8 encoding (no BOM)
    try:
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"✓ Successfully updated {language}:{string_id}")
        print(f"  New value: {new_value}")
    except Exception as e:
        print(f"Error writing file: {e}")
        sys.exit(1)

    sys.exit(0)

def list_languages():
    """List all available language codes."""
    res_dir = Path('app/src/main/res')
    lang_dirs = sorted([d.name.replace('values-', '') for d in res_dir.glob('values-*')
                       if d.is_dir() and 'night' not in d.name and 'v27' not in d.name
                       and 'v30' not in d.name and 'en-' not in d.name])

    print("=" * 80)
    print(f"AVAILABLE LANGUAGES ({len(lang_dirs)})")
    print("=" * 80)

    # Print in columns
    for i in range(0, len(lang_dirs), 4):
        row = lang_dirs[i:i+4]
        print("  " + "  ".join(f"{lang:12}" for lang in row))

    print("=" * 80)
    sys.exit(0)

# Check for command-line argument
show_all_for_lang = None
summary_only = False

if len(sys.argv) > 1:
    arg = sys.argv[1]

    # Handle help commands
    if arg in ['--help', '-h', 'help']:
        show_help()

    # Handle list command
    if arg == '--list':
        list_languages()

    # Handle get command
    if arg == '--get':
        if len(sys.argv) < 4:
            print("Error: --get requires 2 arguments: <language> <string_id>")
            print("Example: python l10n.py --get ru-rRU locale_app_name")
            sys.exit(1)
        language = sys.argv[2]
        string_id = sys.argv[3]
        get_string_value(language, string_id)

    # Handle set command
    if arg == '--set':
        if len(sys.argv) < 5:
            print("Error: --set requires 3 arguments: <language> <string_id> <value>")
            print("Example: python l10n.py --set ru-rRU locale_app_name \"Веб-браузер Fulguris\"")
            sys.exit(1)
        language = sys.argv[2]
        string_id = sys.argv[3]
        new_value = sys.argv[4]
        set_string_value(language, string_id, new_value)

    # Handle summary command
    if arg == '--summary':
        summary_only = True
    else:
        show_all_for_lang = arg
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

# Filter to only the requested language if specified
if show_all_for_lang:
    lang_dirs = [d for d in lang_dirs if d.name == f'values-{show_all_for_lang}']
    if not lang_dirs:
        print(f"Error: Language '{show_all_for_lang}' not found!")
        print(f"Available languages: {', '.join([d.name.replace('values-', '') for d in sorted(res_dir.glob('values-*')) if d.is_dir() and 'night' not in d.name and 'v27' not in d.name and 'v30' not in d.name and 'en-' not in d.name])}")
        sys.exit(1)

print(f"Checking {len(lang_dirs)} {'language' if len(lang_dirs) == 1 else 'non-English languages'} for translation quality\n")
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
    if not summary_only:
        print("\nPotential translation issues found:\n")

        for lang_name in sorted(issues_found.keys()):
            issues = issues_found[lang_name]
            print(f"\n{lang_name} ({len(issues)} issues):")

            # If specific language requested, show all issues; otherwise show first 20
            if show_all_for_lang:
                for issue in issues:
                    print(issue)
                print(f"\n*** Showing ALL {len(issues)} issues for {lang_name} ***")
            else:
                for issue in issues[:20]:  # Show first 20
                    print(issue)
                if len(issues) > 20:
                    print(f"  ... and {len(issues) - 20} more issues")
else:
    if not summary_only:
        print("\nNo translation quality issues detected!")
        print("All translations appear to be properly localized.")

print("\n" + "="*80)
print("SUMMARY")
print("="*80)
print(f"Languages checked: {len(lang_dirs)}")
print(f"Languages with potential issues: {len(issues_found)}")
print(f"Languages with clean translations: {len(lang_dirs) - len(issues_found)}")
print("\nNote: Some 'untranslated' strings may be intentional (proper nouns, etc.)")
print("\nUsage:")
print("  python l10n.py                           # Check all languages")
print("  python l10n.py <lang>                    # Check specific language (e.g., ru-rRU)")
print("  python l10n.py --list                    # List all available languages")
print("  python l10n.py --help                    # Show detailed help")
print("="*80)

