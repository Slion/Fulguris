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
    print("\n  python l10n.py --check [language_code]")
    print("    Check translations for all languages or a specific language")
    print("    Examples:")
    print("      python l10n.py --check          # Check all languages (first 20 issues per lang)")
    print("      python l10n.py --check ru-rRU   # Check Russian (show ALL issues)")
    print("      python l10n.py --check uk-rUA   # Check Ukrainian")
    print("      python l10n.py --check fr-rFR   # Check French")
    print("\n  python l10n.py --list")
    print("    List all available language codes")
    print("\n  python l10n.py --help, -h")
    print("    Show this help message")
    print("\n  python l10n.py --summary")
    print("    Show only summary statistics for all languages")
    print("\n  python l10n.py --set <lang> <string_id> <value> [<string_id> <value> ...]")
    print("    Set one or more string values for a specific language")
    print("    Examples:")
    print("      python l10n.py --set ru-rRU locale_app_name \"Веб-браузер Fulguris\"")
    print("      python l10n.py --set ko-rKR enable \"사용\" disable \"사용 안 함\" show \"표시\"")
    print("\n  python l10n.py --get <lang> <string_id>")
    print("    Get a string value from a specific language")
    print("    Example:")
    print("      python l10n.py --get ru-rRU locale_app_name")
    print("\n  python l10n.py --add <string_id> <value>")
    print("    Add a string to all language files (uses English value)")
    print("    Example:")
    print("      python l10n.py --add new_feature_name \"New Feature\"")
    print("\n  python l10n.py --remove <string_id>")
    print("    Remove a string from all language files")
    print("    Example:")
    print("      python l10n.py --remove obsolete_string")
    print("\nOutput Information:")
    print("  - Untranslated strings that match English")
    print("  - Placeholder mismatches (e.g., missing %s, %1$s)")
    print("  - Technical terms (Android, iOS, Linux, etc.) are excluded")
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
        print(f"[OK] Successfully updated {language}:{string_id}")
        print(f"  New value: {new_value}")
    except Exception as e:
        print(f"Error writing file: {e}")
        sys.exit(1)

    sys.exit(0)

def set_string_values_batch(language, string_pairs):
    """Set multiple string values in a specific language file at once.

    Args:
        language: Language code (e.g., 'ko-rKR')
        string_pairs: List of tuples [(string_id, value), ...]
    """
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

    print("=" * 80)
    print(f"BATCH UPDATE: {language}")
    print("=" * 80)
    print(f"Updating {len(string_pairs)} strings...\n")

    success_count = 0
    error_count = 0
    not_found = []

    # Process all string replacements
    for string_id, new_value in string_pairs:
        # Escape special regex characters in the string ID
        escaped_id = re.escape(string_id)

        # Pattern to match the string entry
        pattern = f'(<string name="{escaped_id}">)([^<]*)(</string>)'

        # Check if the string exists
        if not re.search(pattern, content):
            not_found.append(string_id)
            error_count += 1
            continue

        # Replace the string value
        content = re.sub(pattern, f'\\1{new_value}\\3', content)
        print(f"[OK] {string_id}")
        success_count += 1

    # Write back to file with UTF-8 encoding (no BOM)
    if success_count > 0:
        try:
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(content)
        except Exception as e:
            print(f"\n[ERROR] Failed to write file: {e}")
            sys.exit(1)

    # Print summary
    print("\n" + "=" * 80)
    print("SUMMARY")
    print("=" * 80)
    print(f"Successfully updated: {success_count}")
    print(f"Not found: {error_count}")

    if not_found:
        print(f"\nStrings not found in {file_path}:")
        for string_id in not_found:
            print(f"  - {string_id}")

    print("=" * 80)
    sys.exit(0 if error_count == 0 else 1)

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

def add_string_to_all(string_id, value):
    """Add a string to all language files."""
    res_dir = Path('app/src/main/res')

    # Get all language directories including English
    all_dirs = [d for d in res_dir.glob('values*')
                if d.is_dir() and 'night' not in d.name and 'v27' not in d.name and 'v30' not in d.name]

    success_count = 0
    skip_count = 0
    error_count = 0

    print("=" * 80)
    print(f"ADDING STRING: {string_id}")
    print("=" * 80)
    print(f"Value: {value}\n")

    for lang_dir in sorted(all_dirs):
        strings_file = lang_dir / 'strings.xml'
        lang_name = lang_dir.name

        if not strings_file.exists():
            print(f"[SKIP] {lang_name}: strings.xml not found")
            skip_count += 1
            continue

        try:
            # Read the file
            with open(strings_file, 'r', encoding='utf-8') as f:
                content = f.read()

            # Check if string already exists
            escaped_id = re.escape(string_id)
            if re.search(f'<string name="{escaped_id}">', content):
                print(f"[SKIP] {lang_name}: string already exists")
                skip_count += 1
                continue

            # Find the position to insert (before </resources>)
            if '</resources>' not in content:
                print(f"[ERROR] {lang_name}: Invalid XML structure")
                error_count += 1
                continue

            # Insert the new string before </resources>
            new_string_line = f'    <string name="{string_id}">{value}</string>\n'
            content = content.replace('</resources>', f'{new_string_line}</resources>')

            # Write back
            with open(strings_file, 'w', encoding='utf-8') as f:
                f.write(content)

            print(f"[OK] Added to {lang_name}")
            success_count += 1

        except Exception as e:
            print(f"[ERROR] {lang_name}: {e}")
            error_count += 1

    print("\n" + "=" * 80)
    print("SUMMARY")
    print("=" * 80)
    print(f"Successfully added: {success_count}")
    print(f"Skipped: {skip_count}")
    print(f"Errors: {error_count}")
    print("=" * 80)
    sys.exit(0 if error_count == 0 else 1)

def remove_string_from_all(string_id):
    """Remove a string from all language files."""
    res_dir = Path('app/src/main/res')

    # Get all language directories including English
    all_dirs = [d for d in res_dir.glob('values*')
                if d.is_dir() and 'night' not in d.name and 'v27' not in d.name and 'v30' not in d.name]

    success_count = 0
    skip_count = 0
    error_count = 0

    print("=" * 80)
    print(f"REMOVING STRING: {string_id}")
    print("=" * 80)
    print()

    for lang_dir in sorted(all_dirs):
        strings_file = lang_dir / 'strings.xml'
        lang_name = lang_dir.name

        if not strings_file.exists():
            print(f"[SKIP] {lang_name}: strings.xml not found")
            skip_count += 1
            continue

        try:
            # Read the file
            with open(strings_file, 'r', encoding='utf-8') as f:
                content = f.read()

            # Check if string exists
            escaped_id = re.escape(string_id)
            pattern = f'\\s*<string name="{escaped_id}">.*?</string>\\n?'

            if not re.search(pattern, content):
                print(f"[SKIP] {lang_name}: string not found")
                skip_count += 1
                continue

            # Remove the string
            new_content = re.sub(pattern, '', content)

            # Ensure </resources> is on its own line
            new_content = re.sub(r'([^\n])(</resources>)', r'\1\n\2', new_content)
            # Remove any duplicate newlines before </resources>
            new_content = re.sub(r'\n{3,}(</resources>)', r'\n\1', new_content)

            # Write back
            with open(strings_file, 'w', encoding='utf-8') as f:
                f.write(new_content)

            print(f"[OK] Removed from {lang_name}")
            success_count += 1

        except Exception as e:
            print(f"[ERROR] {lang_name}: {e}")
            error_count += 1

    print("\n" + "=" * 80)
    print("SUMMARY")
    print("=" * 80)
    print(f"Successfully removed: {success_count}")
    print(f"Skipped: {skip_count}")
    print(f"Errors: {error_count}")
    print("=" * 80)
    sys.exit(0 if error_count == 0 else 1)

# Check for command-line argument
show_all_for_lang = None
summary_only = False

# Show help if no arguments provided
if len(sys.argv) == 1:
    show_help()

if len(sys.argv) > 1:
    arg = sys.argv[1]

    # Handle help commands
    if arg in ['--help', '-h', 'help']:
        show_help()
    # Handle list command
    elif arg == '--list':
        list_languages()
    # Handle get command
    elif arg == '--get':
        if len(sys.argv) < 4:
            print("Error: --get requires 2 arguments: <language> <string_id>")
            print("Example: python l10n.py --get ru-rRU locale_app_name")
            sys.exit(1)
        language = sys.argv[2]
        string_id = sys.argv[3]
        get_string_value(language, string_id)
    # Handle set command
    elif arg == '--set':
        if len(sys.argv) < 5:
            print("Error: --set requires at least 3 arguments: <language> <string_id> <value> [<string_id> <value> ...]")
            print("Examples:")
            print("  python l10n.py --set ru-rRU locale_app_name \"Веб-браузер Fulguris\"")
            print("  python l10n.py --set ko-rKR enable \"사용\" disable \"사용 안 함\"")
            sys.exit(1)
        language = sys.argv[2]

        # Parse pairs of string_id and value
        string_pairs = []
        i = 3
        while i < len(sys.argv):
            if i + 1 >= len(sys.argv):
                print(f"Error: Missing value for string_id '{sys.argv[i]}'")
                sys.exit(1)
            string_id = sys.argv[i]
            value = sys.argv[i + 1]
            string_pairs.append((string_id, value))
            i += 2

        # If only one pair, use single-string output format
        if len(string_pairs) == 1:
            set_string_value(language, string_pairs[0][0], string_pairs[0][1])
        else:
            set_string_values_batch(language, string_pairs)
    # Handle add command
    elif arg == '--add':
        if len(sys.argv) < 4:
            print("Error: --add requires 2 arguments: <string_id> <value>")
            print("Example: python l10n.py --add new_feature_name \"New Feature\"")
            sys.exit(1)
        string_id = sys.argv[2]
        value = sys.argv[3]
        add_string_to_all(string_id, value)
    # Handle remove command
    elif arg == '--remove':
        if len(sys.argv) < 3:
            print("Error: --remove requires 1 argument: <string_id>")
            print("Example: python l10n.py --remove obsolete_string")
            sys.exit(1)
        string_id = sys.argv[2]
        remove_string_from_all(string_id)
    # Handle check command
    elif arg == '--check':
        if len(sys.argv) > 2 and not sys.argv[2].startswith('--'):
            # Check specific language
            show_all_for_lang = sys.argv[2]
            print(f"Will show ALL issues for language: {show_all_for_lang}\n")
        # else: check all languages (default behavior)
    # Handle summary command
    elif arg == '--summary':
        summary_only = True
    # Unknown command
    else:
        print(f"Error: Unknown command '{arg}'")
        print("Run 'python l10n.py --help' for usage information")
        sys.exit(1)

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
print("  python l10n.py --check                   # Check all languages")
print("  python l10n.py --check <lang>            # Check specific language (e.g., ru-rRU)")
print("  python l10n.py --list                    # List all available languages")
print("  python l10n.py --help                    # Show detailed help")
print("="*80)

