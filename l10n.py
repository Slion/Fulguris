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

def escape_xml_value(value):
    """Escape special XML characters in string values.

    Android XML string resources require:
    - Apostrophes (') to be escaped as \'
    - Quotes (") to be escaped as \"
    - Ampersands (&) to be escaped as &amp; (only if not part of entity)
    - Less than (<) to be escaped as &lt; (only if not part of XML tag)
    - Greater than (>) to be escaped as &gt; (only if not part of XML tag)
    - Newlines (\n) should be preserved as literal \n

    Special handling: If the value contains XML tags like <xliff:g>, we preserve them.

    IMPORTANT: This function detects and fixes common mistakes:
    - &apos; is WRONG for Android XML (causes build errors)
    - \' is CORRECT for Android XML
    """
    # CRITICAL FIX: Detect and warn about incorrect &apos; usage
    if '&apos;' in value:
        print("  [WARNING] Detected &apos; entity - this is INCORRECT for Android XML!")
        print("            Converting &apos; to \\' (proper Android XML escape)")
        print(f"            Original value: {value}")
        # Replace &apos; with unescaped apostrophe, which will be properly escaped below
        value = value.replace('&apos;', "'")
        print(f"            Fixed value: {value}")

    # Also check for &quot; which should be \"
    if '&quot;' in value:
        print("  [WARNING] Detected &quot; entity - converting to \\\" for Android XML")
        value = value.replace('&quot;', '"')

    # Don't escape if already contains XML entities or escape sequences
    if '&amp;' in value or '&lt;' in value or '&gt;' in value:
        # Already has XML entities, assume it's properly formatted
        return value

    # Check if value contains XML tags (like <xliff:g>)
    # If it does, we should NOT escape < and > as they're part of the XML structure
    has_xml_tags = '<xliff:' in value or '</xliff:' in value or '<b>' in value or '</b>' in value

    # Escape XML special characters
    if not has_xml_tags:
        # Only escape < and > if there are no XML tags in the content
        value = value.replace('&', '&amp;')  # Must be first
        value = value.replace('<', '&lt;')
        value = value.replace('>', '&gt;')
    else:
        # If there are XML tags, only escape & that are not part of entities
        # This is more complex, so for now we skip & escaping for XML tag content
        pass

    # For Android XML, apostrophes need to be escaped with backslash
    # Check if there are any unescaped apostrophes
    # Look for ' that is not preceded by \
    result = []
    i = 0
    while i < len(value):
        if value[i] == "'" and (i == 0 or value[i-1] != '\\'):
            result.append("\\'")
        elif value[i] == '"' and (i == 0 or value[i-1] != '\\'):
            result.append('\\"')
        else:
            result.append(value[i])
        i += 1

    return ''.join(result)

def _extract_plural_items(plurals_content):
    """Extract plural items from a plurals block content.

    Args:
        plurals_content: The content between <plurals> tags

    Returns:
        dict: Dictionary of {quantity: value} pairs
    """
    # Use .*? to match content including XML tags (like <xliff:g>)
    # DOTALL flag allows matching across newlines
    item_pattern = r'<item quantity="([^"]+)">(.*?)</item>'
    items = re.findall(item_pattern, plurals_content, re.DOTALL)
    return dict(items)

def _char_difference(str1, str2):
    """Calculate the minimum number of character differences between two strings.

    Uses a simple character-level comparison. Returns the count of characters
    that differ between the strings.

    Args:
        str1: First string
        str2: Second string

    Returns:
        int: Number of differing characters
    """
    # If lengths are very different, not a near match
    len_diff = abs(len(str1) - len(str2))

    # For strings of similar length, count character differences
    min_len = min(len(str1), len(str2))
    max_len = max(len(str1), len(str2))

    # Count differences in overlapping portion
    differences = sum(c1 != c2 for c1, c2 in zip(str1, str2))

    # Add length difference as additional differences
    differences += len_diff

    return differences

def validate_android_string(value):
    """Validate Android string resource content.

    Checks for issues that will break Android resource compilation:
    - Invalid XML structure (malformed tags, unquoted attributes)
    - Invalid entity references (&pos;, &quot; when should use \\", etc.)
    - PowerShell backtick escapes passed through
    - Unescaped quotes/apostrophes in plain text
    - Invalid placeholders

    Args:
        value: The string value to validate

    Returns:
        tuple: (is_valid, error_message)
               is_valid: bool indicating if string is valid
               error_message: str describing the error, None if valid
    """
    # Check for PowerShell backtick escapes - these are wrong!
    if '`"' in value or '"`' in value or "`'" in value or "'`" in value:
        return False, r'PowerShell backtick escapes detected (`" or `\')! These will break compilation. Use here-strings instead.'

    # Check for invalid entity references that break Android compilation
    # &pos; is a common typo and WILL break
    if '&pos;' in value:
        return False, "Invalid entity '&pos;' detected! This will break compilation. Use \\' for apostrophes in Android XML."

    # Check for &quot; entity - should use \" instead for Android
    if '&quot;' in value:
        return False, "Entity '&quot;' detected! For Android XML, use \\\" (backslash-escaped quote) instead of XML entities."

    # Check for &apos; entity - this is WRONG for Android XML
    if '&apos;' in value:
        return False, "Entity '&apos;' detected! This BREAKS Android compilation. Use \\' (backslash-escaped apostrophe) instead."

    # Check for unescaped quotes in plain text (not in XML tags)
    # This is tricky - we need to check quotes that are NOT part of XML attributes
    if '<' in value and '>' in value:
        # Has XML tags - need to validate XML structure

        # Check for XML attributes without quotes
        # Pattern: word= followed by non-quote, non-space (common error)
        unquoted_attr = re.search(r'\s+\w+=([^\s"\'>][^\s>]*)', value)
        if unquoted_attr:
            return False, f"Attribute value not quoted: {unquoted_attr.group(0).strip()}. XML attributes must have quoted values."

        # Check for basic XML tag matching
        tags = re.findall(r'<(/?)(\w+(?::\w+)?)[^>]*>', value)
        tag_stack = []
        for closing, tag_name in tags:
            if not closing:
                # Opening tag
                tag_stack.append(tag_name)
            else:
                # Closing tag
                if not tag_stack:
                    return False, f"Closing tag </{tag_name}> without matching opening tag"
                opening = tag_stack.pop()
                if opening != tag_name:
                    return False, f"Mismatched tags: <{opening}> closed with </{tag_name}>"

        if tag_stack:
            return False, f"Unclosed tag(s): {', '.join('<' + t + '>' for t in tag_stack)}"

    # Check for other invalid entity references (must be &amp; &lt; &gt; only for plain text)
    # Allow &#... numeric entities and common valid entities
    invalid_entities = re.findall(r'&(?!amp;|lt;|gt;|#\d+;|#x[0-9a-fA-F]+;)\w+;', value)
    if invalid_entities:
        return False, f"Invalid entity reference(s): {', '.join(set(invalid_entities))}. Only &amp; &lt; &gt; and numeric entities are valid in Android XML."

    # Check for unterminated entity references
    unterminated = re.search(r'&\w+(?![;])', value)
    if unterminated:
        return False, f"Unterminated entity reference: '{unterminated.group(0)}'. Entities must end with semicolon."

    return True, None

def show_help():
    """Display help information about available commands."""
    print("=" * 80)
    print("LOCALIZATION (L10N) CHECK TOOL")
    print("=" * 80)
    print("\nSupported Commands:")
    print("\n  python l10n.py --check [language_code] [--near N]")
    print("    Check translations for all languages or a specific language")
    print("    --near N: Also flag strings differing by N or fewer characters from English")
    print("              (default: 0, exact match only)")
    print("    Examples:")
    print("      python l10n.py --check          # Check all languages (first 20 issues per lang)")
    print("      python l10n.py --check ru-rRU   # Check Russian (show ALL issues)")
    print("      python l10n.py --check --near 1 # Flag strings differing by 1 char (e.g. punctuation)")
    print("      python l10n.py --check ko-rKR --near 2  # Check Korean, flag ≤2 char differences")
    print("\n  python l10n.py --list")
    print("    List all available language codes")
    print("\n  python l10n.py --help, -h")
    print("    Show this help message")
    print("\n  python l10n.py --summary")
    print("    Show only summary statistics for all languages")
    print("\n  python l10n.py [--raw] --set <lang> <string_id> <value> [<string_id> <value> ...]")
    print("    Set one or more string values for a specific language")
    print("    By default, values are XML-escaped (quotes, apostrophes).")
    print("    Use --raw flag for complex XML content (no escaping).")
    print("    Will ERROR if string doesn't exist - use --add command to add new strings.")
    print("    Examples:")
    print("      python l10n.py --set ru-rRU locale_app_name \"Веб-браузер Fulguris\"")
    print("      python l10n.py --set ko-rKR enable \"사용\" disable \"사용 안 함\" show \"표시\"")
    print("      python l10n.py --raw --set ko-rKR test '<xliff:g id=\"x\">%1$d</xliff:g>개'")
    print("    ")
    print("    IMPORTANT - Only updates existing strings!")
    print("      --set will error if string doesn't exist (prevents accidental additions).")
    print("      Use --add command to add new strings to ALL language files (keeps files in sync).")
    print("    ")
    print("    IMPORTANT - PowerShell quoting:")
    print("      Use SINGLE quotes for strings with placeholders (%, $, etc.):")
    print("        python l10n.py --set ko-rKR dialog_title '%1$s 열기'")
    print("      Escape inner double quotes with backslash:")
    print("        python l10n.py --set ko-rKR string_id 'Text with \"quotes\"'")
    print("      See L10N.md for complete PowerShell quoting guide.")
    print("\n  python l10n.py --get <lang> <string_id>")
    print("    Get a string value from a specific language")
    print("    Example:")
    print("      python l10n.py --get ru-rRU locale_app_name")
    print("\n  python l10n.py --get-plurals <lang> <plurals_name>")
    print("    Get all plural items for a plurals resource")
    print("    Example:")
    print("      python l10n.py --get-plurals ko-rKR notification_incognito_running_title")
    print("\n  python l10n.py [--raw] --set-plurals <lang> <plurals_name> <quantity> <value> [<quantity> <value> ...]")
    print("    Set plural items for a plurals resource")
    print("    Quantities: zero, one, two, few, many, other")
    print("    Use --raw flag for complex XML content (no escaping).")
    print("    Example:")
    print("      python l10n.py --set-plurals ko-rKR notification_title other '%1$d tabs open'")
    print("      python l10n.py --raw --set-plurals ko-rKR cookies other '<xliff:g>%d</xliff:g> cookies'")
    print("\n  python l10n.py --add <string_id> <value>")
    print("    Add a NEW string to ALL language files")
    print("    This adds the string with the given English value to ALL language files.")
    print("    Use this when adding a new string resource to keep all files in sync.")
    print("    Example:")
    print("      python l10n.py --add new_feature_name \"New Feature\"")
    print("    After adding, translate the new string in each language using --set.")
    print("\n  python l10n.py --remove <string_id>")
    print("    Remove a string from ALL language files")
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

def get_plurals_value(language, plurals_name):
    """Get all plural items from a specific language file."""
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

    # Escape special regex characters in the plurals name
    escaped_name = re.escape(plurals_name)

    # Pattern to match the plurals block
    pattern = f'<plurals name="{escaped_name}">(.*?)</plurals>'

    # Search for the plurals block
    match = re.search(pattern, content, re.DOTALL)
    if not match:
        print(f"Error: Plurals '{plurals_name}' not found in {file_path}")
        sys.exit(1)

    plurals_content = match.group(1)

    # Extract all items using common function
    items_dict = _extract_plural_items(plurals_content)

    if not items_dict:
        print(f"Warning: No items found in plurals '{plurals_name}'")

    print(f"{language}:{plurals_name}")
    for quantity, value in items_dict.items():
        print(f"  {quantity}: {value}")

    sys.exit(0)

def set_plurals_value(language, plurals_name, quantity_value_pairs, skip_escape=False):
    """Set plural items for a plurals resource.

    Args:
        language: Language code (e.g., 'ko-rKR')
        plurals_name: The plurals resource name
        quantity_value_pairs: List of tuples [(quantity, value), ...] where quantity is 'one', 'other', etc.
        skip_escape: If True, skip XML escaping (for --raw flag)
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

    # Escape special regex characters in the plurals name
    escaped_name = re.escape(plurals_name)

    # Pattern to match the plurals block
    pattern = f'(<plurals name="{escaped_name}">)(.*?)(</plurals>)'

    # Check if the plurals exists
    if not re.search(pattern, content, re.DOTALL):
        print(f"Error: Plurals '{plurals_name}' not found in {file_path}")
        sys.exit(1)

    # Build new plurals content
    print(f"Updating plurals: {plurals_name}")
    print(f"Setting {len(quantity_value_pairs)} quantities...\n")

    # Validate all values first before making any changes
    for quantity, value in quantity_value_pairs:
        is_valid, error_msg = validate_android_string(value)
        if not is_valid:
            print(f"[ERROR] XML Validation Failed for quantity '{quantity}'!")
            print(f"  {error_msg}")
            if skip_escape:
                print(f"\nFor PowerShell with --raw, use here-string to preserve quotes.")
            sys.exit(1)

    # For each quantity-value pair, update or add the item
    def replacer(match):
        plurals_content = match.group(2)

        # For each quantity, update or add
        for quantity, value in quantity_value_pairs:
            # Escape XML special characters in the value (unless skip_escape is True)
            if skip_escape:
                escaped_value = value
            else:
                escaped_value = escape_xml_value(value)

            item_pattern = f'<item quantity="{re.escape(quantity)}">.*?</item>'
            new_item = f'<item quantity="{quantity}">{escaped_value}</item>'

            if re.search(item_pattern, plurals_content):
                # Update existing
                plurals_content = re.sub(item_pattern, new_item, plurals_content)
                print(f"[OK] Updated quantity '{quantity}'")
            else:
                # Add new item (before closing tag, with proper indentation)
                # Find the last item to maintain formatting
                lines = plurals_content.split('\n')
                indent = '        '  # Default indentation
                insert_pos = len(lines) - 1

                # Add the new item
                plurals_content = plurals_content.rstrip() + f'\n{indent}{new_item}\n    '
                print(f"[OK] Added quantity '{quantity}'")

        return match.group(1) + plurals_content + match.group(3)

    new_content = re.sub(pattern, replacer, content, flags=re.DOTALL)

    # Write back to file
    try:
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(new_content)
        print(f"\n[OK] Successfully updated {language}:{plurals_name}")
    except Exception as e:
        print(f"Error writing file: {e}")
        sys.exit(1)

    sys.exit(0)

def _replace_string_in_content(content, string_id, new_value, skip_escape=False, allow_add=False):
    """Internal function to replace a string value in XML content, or add if allowed.

    Args:
        content: The XML file content as string
        string_id: The string resource ID
        new_value: The new value to set
        skip_escape: If True, skip XML escaping (for complex XML content)
        allow_add: If True, add string if it doesn't exist; if False, return error

    Returns:
        tuple: (success, new_content, error_message, was_added)
               success: bool indicating if replacement/addition succeeded
               new_content: the modified content (or original if failed)
               error_message: error description if failed, None if succeeded
               was_added: bool indicating if string was added (True) vs updated (False)
    """
    # Escape special regex characters in the string ID
    escaped_id = re.escape(string_id)

    # Pattern to match the string entry - use .*? to match any content including XML tags
    # The ? makes it non-greedy so it stops at the first </string>
    pattern = f'(<string name="{escaped_id}">)(.*?)(</string>)'

    # Escape XML special characters in the new value (unless skip_escape is True)
    if skip_escape:
        escaped_value = new_value
    else:
        escaped_value = escape_xml_value(new_value)

    # Check if the string exists - need DOTALL flag to match across newlines
    if not re.search(pattern, content, re.DOTALL):
        # String doesn't exist
        if not allow_add:
            # Not allowed to add - return error
            return False, content, f"String ID '{string_id}' not found. Use --add flag to add new strings.", False

        # ADD IT (only if allow_add is True)
        # Find the closing </resources> tag and add before it
        closing_tag_pattern = r'(\s*)(</resources>)'
        closing_match = re.search(closing_tag_pattern, content)

        if not closing_match:
            return False, content, "Could not find </resources> tag in XML file", False

        # Detect line ending style from existing content (default to Windows CRLF)
        line_ending = '\r\n' if '\r\n' in content else '\n'

        # Create new string entry with proper indentation and line ending
        indent = '    '  # Standard 4-space indent
        new_entry = f'{indent}<string name="{string_id}">{escaped_value}</string>{line_ending}'

        # Insert before closing tag
        new_content = content[:closing_match.start()] + new_entry + closing_match.group(0)
        return True, new_content, None, True  # was_added = True

    # String exists - UPDATE IT
    # ...existing code...
    def replacer(match):
        return match.group(1) + escaped_value + match.group(3)

    new_content = re.sub(pattern, replacer, content, flags=re.DOTALL)
    return True, new_content, None, False  # was_added = False

def set_string_value(language, string_id, new_value, skip_escape=False):
    """Set a string value in a specific language file.

    Args:
        language: Language code (e.g., 'ko-rKR')
        string_id: The string resource ID
        new_value: The new value to set
        skip_escape: If True, skip XML escaping (for --raw)
    """
    # Always validate XML content before processing
    is_valid, error_msg = validate_android_string(new_value)
    if not is_valid:
        print(f"[ERROR] XML Validation Failed!")
        print(f"  {error_msg}")
        if skip_escape:
            print(f"\nFor PowerShell with --raw, use here-string to preserve quotes:")
            print(f"  $value = @'\n{new_value}\n'@")
            print(f"  python l10n.py --raw --set {language} {string_id} $value")
        sys.exit(1)

    file_path = Path(f'app/src/main/res/values-{language}/strings.xml')

    if not file_path.exists():
        print(f"Error: Language file not found: {file_path}")
        print(f"Run 'python l10n.py --list' to see available languages")
        sys.exit(1)

    # Read the file content, preserving line endings
    try:
        with open(file_path, 'r', encoding='utf-8', newline='') as f:
            content = f.read()
    except Exception as e:
        print(f"Error reading file: {e}")
        sys.exit(1)

    # Replace the string using common function - never allow adding
    success, new_content, error_msg, was_added = _replace_string_in_content(content, string_id, new_value, skip_escape, allow_add=False)

    if not success:
        print(f"[ERROR] {error_msg}")
        print(f"  String '{string_id}' does not exist in {file_path}")
        print(f"  Use --add command to add new strings to all languages:")
        print(f"  python l10n.py --add {string_id} \"value\"")
        sys.exit(1)

    # Write back to file with UTF-8 encoding (no BOM), preserving line endings
    try:
        with open(file_path, 'w', encoding='utf-8', newline='') as f:
            f.write(new_content)
        print(f"[OK] Successfully updated {language}:{string_id}")
        print(f"  New value: {new_value}")
    except Exception as e:
        print(f"Error writing file: {e}")
        sys.exit(1)

    sys.exit(0)

def set_string_values_batch(language, string_pairs, skip_escape=False):
    """Set multiple string values in a specific language file at once.

    Args:
        language: Language code (e.g., 'ko-rKR')
        string_pairs: List of tuples [(string_id, value), ...]
        skip_escape: If True, skip XML escaping (for --set-raw)
    """
    file_path = Path(f'app/src/main/res/values-{language}/strings.xml')

    if not file_path.exists():
        print(f"Error: Language file not found: {file_path}")
        print(f"Run 'python l10n.py --list' to see available languages")
        sys.exit(1)

    # Read the file content, preserving line endings
    try:
        with open(file_path, 'r', encoding='utf-8', newline='') as f:
            content = f.read()
    except Exception as e:
        print(f"Error reading file: {e}")
        sys.exit(1)

    print("=" * 80)
    print(f"BATCH UPDATE: {language}")
    print("=" * 80)
    print(f"Updating {len(string_pairs)} strings...\n")

    # Validate all values first before making any changes
    for string_id, new_value in string_pairs:
        is_valid, error_msg = validate_android_string(new_value)
        if not is_valid:
            print(f"[ERROR] XML Validation Failed for '{string_id}'!")
            print(f"  {error_msg}")
            if skip_escape:
                print(f"\nFor PowerShell with --raw, use here-string to preserve quotes.")
            sys.exit(1)

    success_count = 0
    error_count = 0
    not_found = []

    # Process all string replacements
    for string_id, new_value in string_pairs:
        # Use common replacement function - never allow adding
        success, content, error_msg, was_added = _replace_string_in_content(content, string_id, new_value, skip_escape, allow_add=False)

        if not success:
            not_found.append(string_id)
            error_count += 1
            continue

        print(f"[OK] {string_id}")
        success_count += 1

    # Write back to file with UTF-8 encoding (no BOM), preserving line endings
    if success_count > 0:
        try:
            with open(file_path, 'w', encoding='utf-8', newline='') as f:
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
        print(f"\nStrings not found: {', '.join(not_found)}")
        print(f"Use --add command to add new strings to all languages:")
        print(f"  python l10n.py --add <string_id> \"value\"")
    print("=" * 80)

    if not_found:
        print(f"\nStrings that could not be processed:")
        for string_id in not_found:
            print(f"  - {string_id}")

    print("=" * 80)
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

            # Detect line ending style from existing content (default to Windows CRLF)
            line_ending = '\r\n' if '\r\n' in content else '\n'

            # Insert the new string before </resources>
            new_string_line = f'    <string name="{string_id}">{value}</string>{line_ending}'
            content = content.replace('</resources>', f'{new_string_line}</resources>')

            # Write back
            with open(strings_file, 'w', encoding='utf-8', newline='') as f:
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
    sys.exit(0)

def add_plural_to_all(plurals_name, quantity_value_pairs):
    """Add a plural to all language files.

    Args:
        plurals_name: The name attribute for the plurals element
        quantity_value_pairs: List of (quantity, value) tuples, e.g., [('one', '1 item'), ('other', '%d items')]
    """
    res_dir = Path('app/src/main/res')

    # Get all language directories including English
    all_dirs = [d for d in res_dir.glob('values*')
                if d.is_dir() and 'night' not in d.name and 'v27' not in d.name and 'v30' not in d.name]

    success_count = 0
    skip_count = 0
    error_count = 0

    print("=" * 80)
    print(f"ADDING PLURAL: {plurals_name}")
    print("=" * 80)
    for qty, val in quantity_value_pairs:
        print(f"  {qty}: {val}")
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

            # Check if plural already exists
            escaped_name = re.escape(plurals_name)
            if re.search(f'<plurals name="{escaped_name}">', content):
                print(f"[SKIP] {lang_name}: plural already exists")
                skip_count += 1
                continue

            # Find the position to insert (before </resources>)
            if '</resources>' not in content:
                print(f"[ERROR] {lang_name}: Invalid XML structure")
                error_count += 1
                continue

            # Detect line ending style from existing content (default to Windows CRLF)
            line_ending = '\r\n' if '\r\n' in content else '\n'

            # Build the plurals block
            plural_lines = [f'    <plurals name="{plurals_name}">']
            for quantity, value in quantity_value_pairs:
                plural_lines.append(f'        <item quantity="{quantity}">{value}</item>')
            plural_lines.append(f'    </plurals>')

            new_plural_block = line_ending.join(plural_lines) + line_ending

            # Insert the new plural before </resources>
            content = content.replace('</resources>', f'{new_plural_block}</resources>')

            # Write back
            with open(strings_file, 'w', encoding='utf-8', newline='') as f:
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
    sys.exit(0)

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
            # Pattern that matches the entire line including leading whitespace and trailing newline
            # This preserves the structure by removing just the line itself
            pattern = f'^[ \\t]*<string name="{escaped_id}">.*?</string>[ \\t]*\\r?\\n'

            if not re.search(pattern, content, re.MULTILINE):
                print(f"[SKIP] {lang_name}: string not found")
                skip_count += 1
                continue

            # Remove the string line completely (including its newline)
            new_content = re.sub(pattern, '', content, flags=re.MULTILINE)

            # Ensure </resources> is on its own line
            new_content = re.sub(r'([^\n])(</resources>)', r'\1\n\2', new_content)
            # Clean up any excessive blank lines (more than 2 consecutive)
            new_content = re.sub(r'\n{4,}', r'\n\n\n', new_content)

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
    sys.exit(0)

# Check for command-line argument
show_all_for_lang = None
summary_only = False
near_threshold = 1  # Default: only one char difference
skip_escape = False  # Default: escape XML (use --raw to skip)

# Show help if no arguments provided
if len(sys.argv) == 1:
    show_help()

if len(sys.argv) > 1:
    # Check for --raw flag
    arg_start = 1
    skip_escape = False

    # Process flags that can appear before commands
    while arg_start < len(sys.argv) and sys.argv[arg_start].startswith('--'):
        if sys.argv[arg_start] == '--raw':
            skip_escape = True
            arg_start += 1
        else:
            # Not a pre-command flag, might be a command
            break

    if arg_start >= len(sys.argv):
        print("Error: Flags must be followed by a command")
        print("Example: python l10n.py --raw --set th-rTH string_id \"value\"")
        sys.exit(1)

    arg = sys.argv[arg_start]

    # Handle help commands
    if arg in ['--help', '-h', 'help']:
        show_help()
    # Handle list command
    elif arg == '--list':
        list_languages()
    # Handle get command
    elif arg == '--get':
        if len(sys.argv) < arg_start + 3:
            print("Error: --get requires 2 arguments: <language> <string_id>")
            print("Example: python l10n.py --get ru-rRU locale_app_name")
            sys.exit(1)
        language = sys.argv[arg_start + 1]
        string_id = sys.argv[arg_start + 2]
        get_string_value(language, string_id)
    # Handle get-plurals command
    elif arg == '--get-plurals':
        if len(sys.argv) < arg_start + 3:
            print("Error: --get-plurals requires 2 arguments: <language> <plurals_name>")
            print("Example: python l10n.py --get-plurals ko-rKR notification_incognito_running_title")
            sys.exit(1)
        language = sys.argv[arg_start + 1]
        plurals_name = sys.argv[arg_start + 2]
        get_plurals_value(language, plurals_name)
    # Handle set command
    elif arg == '--set':
        if len(sys.argv) < arg_start + 4:
            print("Error: --set requires at least 3 arguments: <language> <string_id> <value> [<string_id> <value> ...]")
            print("Examples:")
            print("  python l10n.py --set ru-rRU locale_app_name \"Веб-браузер Fulguris\"")
            print("  python l10n.py --set ko-rKR enable \"사용\" disable \"사용 안 함\"")
            print("  python l10n.py --raw --set ko-rKR test '<xliff:g>%s</xliff:g>'")
            print("\nNote: String must exist in the language file. Use --add to add new strings to all languages.")
            sys.exit(1)
        language = sys.argv[arg_start + 1]

        # Parse pairs of string_id and value
        string_pairs = []
        i = arg_start + 2
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
            set_string_value(language, string_pairs[0][0], string_pairs[0][1], skip_escape=skip_escape)
        else:
            set_string_values_batch(language, string_pairs, skip_escape=skip_escape)
    # Handle set-plurals command
    elif arg == '--set-plurals':
        if len(sys.argv) < arg_start + 5:
            print("Error: --set-plurals requires at least 4 arguments: <language> <plurals_name> <quantity> <value> [<quantity> <value> ...]")
            print("Examples:")
            print("  python l10n.py --set-plurals ko-rKR notification_incognito_running_title other '%1$d 시크릿 탭 열림'")
            print("  python l10n.py --set-plurals ko-rKR tabs_count one '1 tab' other '%1$d tabs'")
            print("  python l10n.py --raw --set-plurals ko-rKR cookies other '<xliff:g>%d</xliff:g> cookies'")
            sys.exit(1)
        language = sys.argv[arg_start + 1]
        plurals_name = sys.argv[arg_start + 2]

        # Parse pairs of quantity and value
        quantity_value_pairs = []
        i = arg_start + 3
        while i < len(sys.argv):
            if i + 1 >= len(sys.argv):
                print(f"Error: Missing value for quantity '{sys.argv[i]}'")
                sys.exit(1)
            quantity = sys.argv[i]
            value = sys.argv[i + 1]
            quantity_value_pairs.append((quantity, value))
            i += 2

        set_plurals_value(language, plurals_name, quantity_value_pairs, skip_escape=skip_escape)
    # Handle add command
    elif arg == '--add':
        if len(sys.argv) < arg_start + 3:
            print("Error: --add requires 2 arguments: <string_id> <value>")
            print("Example: python l10n.py --add new_feature_name \"New Feature\"")
            sys.exit(1)
        string_id = sys.argv[arg_start + 1]
        value = sys.argv[arg_start + 2]
        add_string_to_all(string_id, value)
    # Handle add-plural command
    elif arg == '--add-plural':
        if len(sys.argv) < arg_start + 4:
            print("Error: --add-plural requires at least 3 arguments: <plurals_name> <quantity> <value> [<quantity> <value> ...]")
            print("Example: python l10n.py --add-plural item_count one '1 item' other '%d items'")
            sys.exit(1)
        plurals_name = sys.argv[arg_start + 1]

        # Parse pairs of quantity and value
        quantity_value_pairs = []
        i = arg_start + 2
        while i < len(sys.argv):
            if i + 1 >= len(sys.argv):
                print(f"Error: Missing value for quantity '{sys.argv[i]}'")
                sys.exit(1)
            quantity = sys.argv[i]
            value = sys.argv[i + 1]
            quantity_value_pairs.append((quantity, value))
            i += 2

        add_plural_to_all(plurals_name, quantity_value_pairs)
    # Handle remove command
    elif arg == '--remove':
        if len(sys.argv) < arg_start + 2:
            print("Error: --remove requires 1 argument: <string_id>")
            print("Example: python l10n.py --remove obsolete_string")
            sys.exit(1)
        string_id = sys.argv[arg_start + 1]
        remove_string_from_all(string_id)
    # Handle check command
    elif arg == '--check':
        # Parse arguments for check command
        i = arg_start + 1
        while i < len(sys.argv):
            if sys.argv[i] == '--near':
                if i + 1 >= len(sys.argv):
                    print("Error: --near requires a number argument")
                    sys.exit(1)
                try:
                    near_threshold = int(sys.argv[i + 1])
                    if near_threshold < 0:
                        print("Error: --near threshold must be >= 0")
                        sys.exit(1)
                    i += 2
                except ValueError:
                    print(f"Error: --near requires a number, got '{sys.argv[i + 1]}'")
                    sys.exit(1)
            elif not sys.argv[i].startswith('--'):
                # Language code
                show_all_for_lang = sys.argv[i]
                print(f"Will show ALL issues for language: {show_all_for_lang}")
                if near_threshold > 0:
                    print(f"Including near matches (≤{near_threshold} char difference)\n")
                else:
                    print()
                i += 1
            else:
                print(f"Error: Unknown option '{sys.argv[i]}' for --check")
                sys.exit(1)

        if near_threshold > 0 and not show_all_for_lang:
            print(f"Checking with near matches (≤{near_threshold} char difference)\n")
        # Continue to check logic below
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

# Build a dictionary of English plurals
english_plurals = {}
plurals_pattern = r'<plurals name="([^"]+)">(.*?)</plurals>'
for match in re.finditer(plurals_pattern, content, re.DOTALL):
    plurals_name = match.group(1)
    plurals_content = match.group(2)
    # Use common function to extract quantity items
    items_dict = _extract_plural_items(plurals_content)
    if items_dict:
        english_plurals[plurals_name] = items_dict

print(f"Loaded {len(english_strings)} English strings and {len(english_plurals)} plurals")

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

# International terms that don't need translation
international_terms = {
    'WebView', 'Android', 'iOS', 'Linux', 'macOS', 'Windows', 'Desktop', 'Mobile',
    'JavaScript', 'Cookies', 'Port:', 'LeakCanary'
}

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
            # Also check near matches if near_threshold > 0
            # But exclude short strings, proper nouns, and international technical terms
            is_exact_match = translated_value == english_value
            char_diff = _char_difference(translated_value, english_value) if near_threshold > 0 else 999
            is_near_match = char_diff <= near_threshold and char_diff > 0

            if ((is_exact_match or is_near_match) and
                len(english_value) > 3 and
                english_value not in international_terms and
                not string_name.startswith('agent_') and
                not string_name.startswith('log_level_') and
                not '@string/' in translated_value and  # String references
                string_name not in ['android_open_source_project', 'jsoup', 'infinity', 'search_action']):
                if is_exact_match:
                    lang_issues.append(f"  Untranslated: {string_name} = '{english_value}'")
                else:
                    lang_issues.append(f"  Near match ({char_diff} chars): {string_name}")
                    lang_issues.append(f"    EN: '{english_value}'")
                    lang_issues.append(f"    TR: '{translated_value}'")

            # Check 2: Placeholder consistency - CRITICAL!
            # Match patterns: %s, %d, %1$s, %2$d, {placeholders}, <xliff:g>...</xliff:g>
            # Placeholder mismatches can cause app crashes at runtime
            english_placeholders = re.findall(r'%\d*\$?[sdif]|\{[^}]+\}|<xliff:g[^>]*>.*?</xliff:g>', english_value)
            trans_placeholders = re.findall(r'%\d*\$?[sdif]|\{[^}]+\}|<xliff:g[^>]*>.*?</xliff:g>', translated_value)

            if len(english_placeholders) != len(trans_placeholders):
                lang_issues.append(f"  [CRITICAL] Placeholder mismatch: {string_name}")
                lang_issues.append(f"    English: {english_placeholders}")
                lang_issues.append(f"    Translation: {trans_placeholders}")
                lang_issues.append(f"    WARNING: This WILL cause app crashes!")

    # Check plurals
    for plurals_name, english_quantities in english_plurals.items():
        # Find the plurals block in translated content
        plurals_pattern = f'<plurals name="{re.escape(plurals_name)}">(.*?)</plurals>'
        plurals_match = re.search(plurals_pattern, trans_content, re.DOTALL)

        if not plurals_match:
            # Plurals resource missing entirely
            lang_issues.append(f"  Missing plurals: {plurals_name}")
            continue

        trans_plurals_content = plurals_match.group(1)
        trans_quantities = _extract_plural_items(trans_plurals_content)

        # Only check if existing quantities are untranslated
        # Don't complain about missing quantities - different languages have different plural rules
        untranslated_quantities = []

        for quantity, trans_value in trans_quantities.items():
            # Check if this quantity exists in English and has the same value (untranslated)
            if quantity in english_quantities:
                english_value = english_quantities[quantity]
                if trans_value == english_value and len(english_value) > 3:
                    untranslated_quantities.append(f"{quantity}: '{english_value}'")

        if untranslated_quantities:
            lang_issues.append(f"  Plurals '{plurals_name}' untranslated:")
            for untrans in untranslated_quantities:
                lang_issues.append(f"    {untrans}")

    if lang_issues:
        issues_found[lang_name] = lang_issues

# Report findings
if issues_found:
    if not summary_only:
        print("\nPotential translation issues found:\n")

        for lang_name in sorted(issues_found.keys()):
            issues = issues_found[lang_name]
            # Count critical (placeholder) issues
            critical_count = sum(1 for issue in issues if 'CRITICAL' in issue)

            if critical_count > 0:
                print(f"\n{lang_name} ({len(issues)} issues, {critical_count} CRITICAL):")
            else:
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

# Count total critical issues across all languages
total_critical = 0
for issues in issues_found.values():
    total_critical += sum(1 for issue in issues if 'CRITICAL' in issue)

print("\n" + "="*80)
print("SUMMARY")
print("="*80)
print(f"Languages checked: {len(lang_dirs)}")
print(f"Languages with potential issues: {len(issues_found)}")
print(f"Languages with clean translations: {len(lang_dirs) - len(issues_found)}")
if total_critical > 0:
    print(f"\n*** CRITICAL ISSUES FOUND: {total_critical} placeholder mismatches that WILL cause crashes! ***")
print("\nNote: Some 'untranslated' strings may be intentional (proper nouns, etc.)")
print("\nUsage:")
print("  python l10n.py --check                   # Check all languages")
print("  python l10n.py --check <lang>            # Check specific language (e.g., ru-rRU)")
print("  python l10n.py --list                    # List all available languages")
print("  python l10n.py --help                    # Show detailed help")
print("="*80)
