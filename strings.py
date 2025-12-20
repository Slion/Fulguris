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

def string_exists_in_english(string_id):
    """Check if a string exists in the English source file.

    Args:
        string_id: The string resource ID to check

    Returns:
        bool: True if the string exists in values/strings.xml
    """
    english_file = Path('app/src/main/res/values/strings.xml')

    if not english_file.exists():
        return False

    try:
        with open(english_file, 'r', encoding='utf-8') as f:
            content = f.read()

        escaped_id = re.escape(string_id)
        return bool(re.search(f'<string name="{escaped_id}">', content))
    except:
        return False

def show_help():
    """Display help information about available commands."""
    print("=" * 80)
    print("LOCALIZATION (L10N) CHECK TOOL")
    print("=" * 80)
    print("\nSupported Commands:")
    print("\n  python strings.py --check [language_code] [--full] [--near N]")
    print("    Check translations for all languages or a specific language")
    print("    ")
    print("    Two check modes:")
    print("      INCREMENTAL (default): Only flags strings MISSING from translated file")
    print("        - Strings present in translated file are assumed correct")
    print("        - Use for day-to-day translation work")
    print("      FULL (--full): Also flags strings that match English exactly")
    print("        - Catches strings copied but not actually translated")
    print("        - Use for quality audits or new language setup")
    print("    ")
    print("    --near N: Also flag strings differing by N or fewer characters from English")
    print("              (default: 0, exact match only)")
    print("    Examples:")
    print("      python strings.py --check          # Incremental check all languages")
    print("      python strings.py --check ru-rRU   # Incremental check Russian (show ALL issues)")
    print("      python strings.py --check --full   # Full audit of all languages")
    print("      python strings.py --check th-rTH --full  # Full audit of Thai")
    print("      python strings.py --check --near 1 # Flag strings differing by 1 char (punctuation)")
    print("\n  python strings.py --list")
    print("    List all available language codes")
    print("\n  python strings.py --help, -h")
    print("    Show this help message")
    print("\n  python strings.py --summary")
    print("    Show only summary statistics for all languages")
    print("\n  python strings.py [--raw] --set <lang> <string_id> <value> [<string_id> <value> ...]")
    print("    Set one or more string values for a specific language")
    print("    By default, values are XML-escaped (quotes, apostrophes).")
    print("    Use --raw flag for complex XML content (no escaping).")
    print("    Will ERROR if string doesn't exist in English source file.")
    print("    ")
    print("    Special language code 'source': Edit the English source file directly")
    print("      python strings.py --set source string_id 'Updated English text'")
    print("    ")
    print("    Examples:")
    print("      python strings.py --set ru-rRU locale_app_name \"Веб-браузер Fulguris\"")
    print("      python strings.py --set ko-rKR enable \"사용\" disable \"사용 안 함\" show \"표시\"")
    print("      python strings.py --raw --set ko-rKR test '<xliff:g id=\"x\">%1$d</xliff:g>개'")
    print("      python strings.py --set source settings 'Settings'  # Edit English source")
    print("    ")
    print("    IMPORTANT - Only updates existing strings!")
    print("      --set will error if string doesn't exist in English source file.")
    print("      Use --add to add new strings to English, then --set to translate them.")
    print("    ")
    print("    IMPORTANT - PowerShell quoting:")
    print("      Use SINGLE quotes for strings with placeholders (%, $, etc.):")
    print("        python strings.py --set ko-rKR dialog_title '%1$s 열기'")
    print("      Escape inner double quotes with backslash:")
    print("        python strings.py --set ko-rKR string_id 'Text with \"quotes\"'")
    print("      See L10N.md for complete PowerShell quoting guide.")
    print("\n  python strings.py --get <lang> <string_id>")
    print("    Get a string value from a specific language")
    print("    Use 'source' as language code to read from English source file")
    print("    Examples:")
    print("      python strings.py --get ru-rRU locale_app_name")
    print("      python strings.py --get source settings  # Read from English source")
    print("\n  python strings.py --get-plurals <lang> <plurals_name>")
    print("    Get all plural items for a plurals resource")
    print("    Example:")
    print("      python strings.py --get-plurals ko-rKR notification_incognito_running_title")
    print("\n  python strings.py [--raw] --set-plurals <lang> <plurals_name> <quantity> <value> [<quantity> <value> ...]")
    print("    Set plural items for a plurals resource")
    print("    Quantities: zero, one, two, few, many, other")
    print("    Use --raw flag for complex XML content (no escaping).")
    print("    Example:")
    print("      python strings.py --set-plurals ko-rKR notification_title other '%1$d tabs open'")
    print("      python strings.py --raw --set-plurals ko-rKR cookies other '<xliff:g>%d</xliff:g> cookies'")
    print("\n  python strings.py --add <string_id> <value>")
    print("    Add a NEW string to the English source file (values/strings.xml)")
    print("    This adds the string ONLY to English - language files should only contain")
    print("    translated strings to properly track translation progress.")
    print("    Example:")
    print("      python strings.py --add new_feature_name \"New Feature\"")
    print("    After adding, translate the new string in each language using --set.")
    print("\n  python strings.py --remove <string_id> [<string_id2> ...]")
    print("    Remove one or more strings from ALL language files")
    print("    Example:")
    print("      python strings.py --remove obsolete_string")
    print("      python strings.py --remove string1 string2 string3")
    print("\n  python strings.py --unused")
    print("    Find strings defined in English but not used in source code")
    print("    Searches .kt, .java, and .xml files for R.string.* and @string/* references")
    print("    Outputs a command to remove all unused strings")
    print("\n  python strings.py --changed")
    print("    Detect source strings that have changed and need translation updates")
    print("    Compares English source (values/strings.xml) with en-rUS translations.")
    print("    If source differs from en-rUS, the source string has been modified")
    print("    and ALL translations need to be reviewed/updated.")
    print("    ")
    print("    Workflow:")
    print("      1. Run --changed to find modified source strings")
    print("      2. Update en-rUS to match source (or update source to match en-rUS)")
    print("      3. Update all other language translations")
    print("\n  python strings.py --sort [language_code]")
    print("    Sort all strings alphabetically by string ID")
    print("    If language_code is provided, sorts only that language")
    print("    If no language_code, sorts ALL language files including source")
    print("    Preserves comments and XML structure in source file")
    print("    Examples:")
    print("      python strings.py --sort          # Sort all languages")
    print("      python strings.py --sort de-rDE   # Sort only German")
    print("      python strings.py --sort source   # Sort only English source")
    print("\nOutput Information:")
    print("  - Untranslated strings that match English")
    print("  - Placeholder mismatches (e.g., missing %s, %1$s)")
    print("  - Technical terms (Android, iOS, Linux, etc.) are excluded")
    print("=" * 80)
    sys.exit(0)

def get_string_value(language, string_id):
    """Get a string value from a specific language file.

    Special language codes:
        'source' or 'values' - Read from English source file (values/strings.xml)
    """
    # Handle special 'source' or 'values' language code for English source file
    if language in ('source', 'values'):
        file_path = Path('app/src/main/res/values/strings.xml')
    else:
        file_path = Path(f'app/src/main/res/values-{language}/strings.xml')

    if not file_path.exists():
        print(f"Error: Language file not found: {file_path}")
        print(f"Run 'python strings.py --list' to see available languages")
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
        print(f"Run 'python strings.py --list' to see available languages")
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
        print(f"Run 'python strings.py --list' to see available languages")
        sys.exit(1)

    # First, verify the plurals exists in English source
    source_path = Path('app/src/main/res/values/strings.xml')
    try:
        with open(source_path, 'r', encoding='utf-8') as f:
            source_content = f.read()
    except Exception as e:
        print(f"Error reading source file: {e}")
        sys.exit(1)

    escaped_name = re.escape(plurals_name)
    source_pattern = f'<plurals name="{escaped_name}">'
    if not re.search(source_pattern, source_content):
        print(f"Error: Plurals '{plurals_name}' does not exist in English source file")
        print(f"Available plurals must be defined in values/strings.xml first")
        sys.exit(1)

    # Read the target file content
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
    except Exception as e:
        print(f"Error reading file: {e}")
        sys.exit(1)

    # Detect line ending style
    line_ending = '\r\n' if '\r\n' in content else '\n'

    # Pattern to match the plurals block
    pattern = f'(<plurals name="{escaped_name}">)(.*?)(</plurals>)'

    # Check if the plurals exists in target language
    plurals_exists = bool(re.search(pattern, content, re.DOTALL))

    # Validate all values first before making any changes
    for quantity, value in quantity_value_pairs:
        is_valid, error_msg = validate_android_string(value)
        if not is_valid:
            print(f"[ERROR] XML Validation Failed for quantity '{quantity}'!")
            print(f"  {error_msg}")
            if skip_escape:
                print(f"\nFor PowerShell with --raw, use here-string to preserve quotes.")
            sys.exit(1)

    if plurals_exists:
        # Update existing plurals
        print(f"Updating plurals: {plurals_name}")
        print(f"Setting {len(quantity_value_pairs)} quantities...{line_ending}")

        def replacer(match):
            plurals_content = match.group(2)

            for quantity, value in quantity_value_pairs:
                if skip_escape:
                    escaped_value = value
                else:
                    escaped_value = escape_xml_value(value)

                item_pattern = f'<item quantity="{re.escape(quantity)}">.*?</item>'
                new_item = f'<item quantity="{quantity}">{escaped_value}</item>'

                if re.search(item_pattern, plurals_content):
                    plurals_content = re.sub(item_pattern, new_item, plurals_content)
                    print(f"[OK] Updated quantity '{quantity}'")
                else:
                    plurals_content = plurals_content.rstrip() + f'{line_ending}        {new_item}{line_ending}    '
                    print(f"[OK] Added quantity '{quantity}'")

            return match.group(1) + plurals_content + match.group(3)

        new_content = re.sub(pattern, replacer, content, flags=re.DOTALL)
    else:
        # Create new plurals block
        print(f"Adding new plurals: {plurals_name}")
        print(f"Setting {len(quantity_value_pairs)} quantities...{line_ending}")

        # Build the new plurals block
        items = []
        for quantity, value in quantity_value_pairs:
            if skip_escape:
                escaped_value = value
            else:
                escaped_value = escape_xml_value(value)
            items.append(f'        <item quantity="{quantity}">{escaped_value}</item>')
            print(f"[OK] Added quantity '{quantity}'")

        new_plurals = f'    <plurals name="{plurals_name}">{line_ending}'
        new_plurals += line_ending.join(items)
        new_plurals += f'{line_ending}    </plurals>'

        # Insert before closing </resources> tag
        if '</resources>' in content:
            new_content = content.replace('</resources>', f'{new_plurals}{line_ending}</resources>')
        else:
            print(f"Error: Could not find </resources> tag in {file_path}")
            sys.exit(1)

    # Write back to file
    try:
        with open(file_path, 'w', encoding='utf-8', newline='') as f:
            f.write(new_content)
        if plurals_exists:
            print(f"{line_ending}[OK] Successfully updated {language}:{plurals_name}")
        else:
            print(f"{line_ending}[OK] Successfully added {language}:{plurals_name}")
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
        closing_tag_pattern = r'(</resources>)'
        closing_match = re.search(closing_tag_pattern, content)

        if not closing_match:
            return False, content, "Could not find </resources> tag in XML file", False

        # Detect line ending style from existing content (default to Windows CRLF)
        line_ending = '\r\n' if '\r\n' in content else '\n'

        # Create new string entry with proper indentation
        indent = '    '  # Standard 4-space indent
        new_entry = f'{indent}<string name="{string_id}">{escaped_value}</string>{line_ending}'

        # Check if there's already a newline before </resources>
        pos = closing_match.start()
        has_newline_before = pos > 0 and content[pos-1] in '\r\n'

        # Insert before closing tag
        if has_newline_before:
            # Already has newline, just insert the entry
            new_content = content[:pos] + new_entry + closing_match.group(0)
        else:
            # Need to add newline before our entry
            new_content = content[:pos] + line_ending + new_entry + closing_match.group(0)

        return True, new_content, None, True  # was_added = True

    # String exists - UPDATE IT
    # ...existing code...
    def replacer(match):
        return match.group(1) + escaped_value + match.group(3)

    new_content = re.sub(pattern, replacer, content, flags=re.DOTALL)
    return True, new_content, None, False  # was_added = False

def set_string_value_quiet(language, string_id, new_value, skip_escape=False):
    """Set a string value in a specific language file (quiet mode for batch operations).

    Returns True on success, raises Exception on failure.
    """
    # Handle special 'source' or 'values' language code for English source file
    is_source = language in ('source', 'values')
    if is_source:
        file_path = Path('app/src/main/res/values/strings.xml')
    else:
        # Check if string exists in English source file (only for non-source languages)
        if not string_exists_in_english(string_id):
            raise Exception(f"String '{string_id}' does not exist in English source")
        file_path = Path(f'app/src/main/res/values-{language}/strings.xml')

    if not file_path.exists():
        raise Exception(f"Language file not found: {file_path}")

    # Read the file content
    with open(file_path, 'r', encoding='utf-8', newline='') as f:
        content = f.read()

    # Replace the string
    success, new_content, error_msg, was_added = _replace_string_in_content(
        content, string_id, new_value, skip_escape, allow_add=not is_source)

    if not success:
        raise Exception(error_msg)

    # Write back to file
    with open(file_path, 'w', encoding='utf-8', newline='') as f:
        f.write(new_content)

    return True

def set_string_value(language, string_id, new_value, skip_escape=False):
    """Set a string value in a specific language file.

    If the string exists in the language file, it will be updated.
    If it doesn't exist but exists in English, it will be added as a new translation.
    If it doesn't exist in English, an error is returned.

    Special language codes:
        'source' or 'values' - Edit the English source file (values/strings.xml)

    Args:
        language: Language code (e.g., 'ko-rKR') or 'source'/'values' for English
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
            print(f"  python strings.py --raw --set {language} {string_id} $value")
        sys.exit(1)

    # Handle special 'source' or 'values' language code for English source file
    is_source = language in ('source', 'values')
    if is_source:
        file_path = Path('app/src/main/res/values/strings.xml')
    else:
        # Check if string exists in English source file (only for non-source languages)
        if not string_exists_in_english(string_id):
            print(f"[ERROR] String '{string_id}' does not exist in English source file")
            print(f"  Cannot add translation for a string that doesn't exist in English.")
            print(f"  First add the string to English with:")
            print(f"  python strings.py --add {string_id} \"English value\"")
            sys.exit(1)
        file_path = Path(f'app/src/main/res/values-{language}/strings.xml')

    if not file_path.exists():
        print(f"Error: Language file not found: {file_path}")
        print(f"Run 'python strings.py --list' to see available languages")
        sys.exit(1)

    # Read the file content, preserving line endings
    try:
        with open(file_path, 'r', encoding='utf-8', newline='') as f:
            content = f.read()
    except Exception as e:
        print(f"Error reading file: {e}")
        sys.exit(1)

    # Replace the string using common function
    # For source file: don't allow adding (use --add for that)
    # For translations: allow adding since we verified English exists
    success, new_content, error_msg, was_added = _replace_string_in_content(
        content, string_id, new_value, skip_escape, allow_add=not is_source)

    if not success:
        print(f"[ERROR] {error_msg}")
        if is_source and "not found" in error_msg.lower():
            print(f"  Use --add to add new strings to English source file.")
        sys.exit(1)

    # Write back to file with UTF-8 encoding (no BOM), preserving line endings
    try:
        with open(file_path, 'w', encoding='utf-8', newline='') as f:
            f.write(new_content)
        display_lang = "source" if is_source else language
        if was_added:
            print(f"[OK] Added new translation {display_lang}:{string_id}")
        else:
            print(f"[OK] Successfully updated {display_lang}:{string_id}")
        print(f"  New value: {new_value}")
    except Exception as e:
        print(f"Error writing file: {e}")
        sys.exit(1)

    sys.exit(0)

def set_string_values_batch(language, string_pairs, skip_escape=False):
    """Set multiple string values in a specific language file at once.

    If a string exists in the language file, it will be updated.
    If it doesn't exist but exists in English, it will be added as a new translation.
    If it doesn't exist in English, it will be skipped with an error.

    Special language codes:
        'source' or 'values' - Edit the English source file (values/strings.xml)

    Args:
        language: Language code (e.g., 'ko-rKR') or 'source'/'values' for English
        string_pairs: List of tuples [(string_id, value), ...]
        skip_escape: If True, skip XML escaping (for --set-raw)
    """
    # Handle special 'source' or 'values' language code for English source file
    is_source = language in ('source', 'values')
    if is_source:
        file_path = Path('app/src/main/res/values/strings.xml')
        display_lang = "source"
    else:
        file_path = Path(f'app/src/main/res/values-{language}/strings.xml')
        display_lang = language

    if not file_path.exists():
        print(f"Error: Language file not found: {file_path}")
        print(f"Run 'python strings.py --list' to see available languages")
        sys.exit(1)

    # Read the file content, preserving line endings
    try:
        with open(file_path, 'r', encoding='utf-8', newline='') as f:
            content = f.read()
    except Exception as e:
        print(f"Error reading file: {e}")
        sys.exit(1)

    print("=" * 80)
    print(f"BATCH UPDATE: {display_lang}")
    print("=" * 80)
    print(f"Processing {len(string_pairs)} strings...\n")

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
    added_count = 0
    error_count = 0
    not_in_english = []

    # Process all string replacements
    for string_id, new_value in string_pairs:
        # For non-source languages, check if string exists in English first
        if not is_source and not string_exists_in_english(string_id):
            not_in_english.append(string_id)
            error_count += 1
            continue

        # Use common replacement function
        # For source file: don't allow adding (strings must exist)
        # For translations: allow adding since we verified English exists
        success, content, error_msg, was_added = _replace_string_in_content(
            content, string_id, new_value, skip_escape, allow_add=not is_source)

        if not success:
            print(f"[ERROR] {string_id}: {error_msg}")
            error_count += 1
            continue

        if was_added:
            print(f"[OK] {string_id} (added)")
            added_count += 1
        else:
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
    print(f"Successfully processed: {success_count}")
    if added_count > 0:
        print(f"  - Updated: {success_count - added_count}")
        print(f"  - Added new translations: {added_count}")
    print(f"Errors: {error_count}")
    if not_in_english:
        print(f"\nStrings not found in English source: {', '.join(not_in_english)}")
        print(f"These strings must be added to English first:")
        print(f"  python strings.py --add <string_id> \"English value\"")
    print("=" * 80)
    sys.exit(0)

def list_languages():
    """List all available language codes."""
    res_dir = Path('app/src/main/res')
    lang_dirs = sorted([d.name.replace('values-', '') for d in res_dir.glob('values-*')
                       if d.is_dir() and 'night' not in d.name and 'v27' not in d.name
                       and 'v30' not in d.name])

    print("=" * 80)
    print(f"AVAILABLE LANGUAGES ({len(lang_dirs)})")
    print("=" * 80)

    # Print in columns
    for i in range(0, len(lang_dirs), 4):
        row = lang_dirs[i:i+4]
        print("  " + "  ".join(f"{lang:12}" for lang in row))

    print("=" * 80)
    sys.exit(0)

def add_string_to_english(string_id, value):
    """Add a string to the English source file only.

    Language files should only contain translated strings to properly track
    translation progress. New strings are added to English, then translators
    use --set to add translations to their language files.
    """
    res_dir = Path('app/src/main/res')
    english_file = res_dir / 'values' / 'strings.xml'

    print("=" * 80)
    print(f"ADDING STRING TO ENGLISH: {string_id}")
    print("=" * 80)
    print(f"Value: {value}\n")

    if not english_file.exists():
        print(f"[ERROR] English source file not found: {english_file}")
        sys.exit(1)

    try:
        # Read the file
        with open(english_file, 'r', encoding='utf-8') as f:
            content = f.read()

        # Check if string already exists
        escaped_id = re.escape(string_id)
        if re.search(f'<string name="{escaped_id}">', content):
            print(f"[SKIP] String already exists in English source file")
            sys.exit(0)

        # Find the position to insert (before </resources>)
        if '</resources>' not in content:
            print(f"[ERROR] Invalid XML structure in English source file")
            sys.exit(1)

        # Detect line ending style from existing content (default to Windows CRLF)
        line_ending = '\r\n' if '\r\n' in content else '\n'

        # Insert the new string before </resources>
        new_string_line = f'    <string name="{string_id}">{value}</string>{line_ending}'
        content = content.replace('</resources>', f'{new_string_line}</resources>')

        # Write back
        with open(english_file, 'w', encoding='utf-8', newline='') as f:
            f.write(content)

        print(f"[OK] Added to English source file (values/strings.xml)")
        print(f"\nNext steps:")
        print(f"  1. Translators can now use --set to add translations:")
        print(f"     python strings.py --set <lang> {string_id} '<translated value>'")
        print(f"  2. Run --check <lang> to see which languages need this translation")

    except Exception as e:
        print(f"[ERROR] {e}")
        sys.exit(1)

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

def find_changed_strings():
    """Find source strings that have changed and need translation updates.

    Compares English source (values/strings.xml) with en-rUS (values-en-rUS/strings.xml).
    If a string exists in both but differs, the source has been modified and ALL
    translations need to be reviewed/updated.

    Workflow:
    1. When source string content is changed, en-rUS will no longer match
    2. This function detects those differences
    3. Developer reviews and updates en-rUS to match source
    4. Then updates all other language translations
    """
    source_file = Path('app/src/main/res/values/strings.xml')
    en_us_file = Path('app/src/main/res/values-en-rUS/strings.xml')

    if not source_file.exists():
        print("Error: English source file not found!")
        sys.exit(1)

    if not en_us_file.exists():
        print("Error: en-rUS file not found!")
        sys.exit(1)

    # Parse source strings
    with open(source_file, 'r', encoding='utf-8') as f:
        source_content = f.read()

    source_strings = {}
    for match in re.finditer(r'<string name="([^"]+)">(.+?)</string>', source_content):
        source_strings[match.group(1)] = match.group(2)

    # Parse en-rUS strings
    with open(en_us_file, 'r', encoding='utf-8') as f:
        en_us_content = f.read()

    en_us_strings = {}
    for match in re.finditer(r'<string name="([^"]+)">(.+?)</string>', en_us_content):
        en_us_strings[match.group(1)] = match.group(2)

    print("=" * 80)
    print("CHANGED SOURCE STRINGS DETECTION")
    print("=" * 80)
    print(f"\nComparing source ({len(source_strings)} strings) with en-rUS ({len(en_us_strings)} strings)")
    print()

    changed_strings = []
    missing_in_en_us = []
    extra_in_en_us = []

    # Find strings that differ
    for string_id, source_value in source_strings.items():
        if string_id in en_us_strings:
            en_us_value = en_us_strings[string_id]
            if source_value != en_us_value:
                changed_strings.append({
                    'id': string_id,
                    'source': source_value,
                    'en_us': en_us_value
                })
        else:
            missing_in_en_us.append({
                'id': string_id,
                'source': source_value
            })

    # Find strings only in en-rUS (might be obsolete)
    for string_id in en_us_strings:
        if string_id not in source_strings:
            extra_in_en_us.append({
                'id': string_id,
                'en_us': en_us_strings[string_id]
            })

    # Report findings
    if changed_strings:
        print(f"🔄 CHANGED STRINGS ({len(changed_strings)}):")
        print("   These source strings have changed. All translations need updating!\n")
        for item in changed_strings:
            print(f"   {item['id']}:")
            print(f"      Source: '{item['source']}'")
            print(f"      en-rUS: '{item['en_us']}'")
            print()

    if missing_in_en_us:
        print(f"\n📥 NEW STRINGS (in source but not in en-rUS): {len(missing_in_en_us)}")
        print("   These are new strings that need en-rUS translation:\n")
        for item in missing_in_en_us[:10]:  # Show first 10
            print(f"   {item['id']}: '{item['source']}'")
        if len(missing_in_en_us) > 10:
            print(f"   ... and {len(missing_in_en_us) - 10} more")

    if extra_in_en_us:
        print(f"\n📤 OBSOLETE STRINGS (in en-rUS but not in source): {len(extra_in_en_us)}")
        print("   These might need to be removed from en-rUS:\n")
        for item in extra_in_en_us[:10]:  # Show first 10
            print(f"   {item['id']}: '{item['en_us']}'")
        if len(extra_in_en_us) > 10:
            print(f"   ... and {len(extra_in_en_us) - 10} more")

    # Summary
    print("\n" + "=" * 80)
    print("SUMMARY")
    print("=" * 80)
    print(f"Source strings: {len(source_strings)}")
    print(f"en-rUS strings: {len(en_us_strings)}")
    print(f"Changed (need all translations updated): {len(changed_strings)}")
    print(f"New (source only): {len(missing_in_en_us)}")
    print(f"Obsolete (en-rUS only): {len(extra_in_en_us)}")

    if changed_strings:
        print("\n" + "-" * 80)
        print("RECOMMENDED ACTIONS:")
        print("-" * 80)
        print("1. Review each changed string above")
        print("2. Decide if source or en-rUS has the correct text")
        print("3. Update en-rUS to match source (or vice versa):")
        for item in changed_strings[:5]:
            print(f"   python strings.py --set en-rUS {item['id']} '<new_value>'")
        if len(changed_strings) > 5:
            print(f"   ... and {len(changed_strings) - 5} more")
        print("4. Then update all other language translations")

    if not changed_strings and not missing_in_en_us and not extra_in_en_us:
        print("\n✅ Source and en-rUS are in sync! No changes detected.")

    print("=" * 80)
    sys.exit(0)

def sort_strings_file(file_path, source_element_order):
    """Reorder strings in a translation file to match the order in the source file.

    This makes diffs easier to review and keeps related strings grouped together.

    Args:
        file_path: Path to the strings.xml file
        source_element_order: List of (type, id) tuples in source file order
                              where type is 'string', 'plurals', or 'string-array'

    Returns:
        tuple: (success: bool, message: str)
    """
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
    except Exception as e:
        return False, f"Error reading file: {e}"

    # Detect line ending style
    line_ending = '\r\n' if '\r\n' in content else '\n'

    # Extract the XML declaration and resources opening tag
    xml_decl_match = re.match(r'(<\?xml[^?]*\?>\s*)', content)
    xml_declaration = xml_decl_match.group(1) if xml_decl_match else '<?xml version="1.0" encoding="utf-8"?>\n'

    # Extract resources tag with attributes
    resources_match = re.search(r'(<resources[^>]*>)', content)
    if not resources_match:
        return False, "Invalid XML: No <resources> tag found"
    resources_tag = resources_match.group(1)

    # Extract all strings - use pattern that handles xliff tags inside strings
    strings = {}
    for match in re.finditer(r'(\s*<string name="([^"]+)">(.*?)</string>)', content, re.DOTALL):
        full_match = match.group(1)
        string_id = match.group(2)
        strings[string_id] = full_match.strip()

    # Extract all plurals
    plurals = {}
    for match in re.finditer(r'(\s*<plurals name="([^"]+)".*?</plurals>)', content, re.DOTALL):
        full_match = match.group(1)
        plurals_id = match.group(2)
        plurals[plurals_id] = full_match.strip()

    # Extract string-arrays
    arrays = {}
    for match in re.finditer(r'(\s*<string-array name="([^"]+)".*?</string-array>)', content, re.DOTALL):
        full_match = match.group(1)
        array_id = match.group(2)
        arrays[array_id] = full_match.strip()

    # Rebuild file with elements in source order
    output_lines = [xml_declaration.rstrip(), resources_tag]

    for elem_type, elem_id in source_element_order:
        if elem_type == 'string' and elem_id in strings:
            output_lines.append(f"    {strings[elem_id]}")
        elif elem_type == 'plurals' and elem_id in plurals:
            plurals_content = plurals[elem_id]
            # Normalize indentation
            plurals_lines = plurals_content.split('\n')
            indented_lines = []
            for pline in plurals_lines:
                stripped = pline.strip()
                if stripped.startswith('<plurals'):
                    indented_lines.append(f"    {stripped}")
                elif stripped.startswith('</plurals'):
                    indented_lines.append(f"    {stripped}")
                elif stripped.startswith('<item'):
                    indented_lines.append(f"        {stripped}")
                else:
                    indented_lines.append(f"    {stripped}")
            output_lines.append(line_ending.join(indented_lines))
        elif elem_type == 'string-array' and elem_id in arrays:
            array_content = arrays[elem_id]
            array_lines = array_content.split('\n')
            indented_lines = []
            for aline in array_lines:
                stripped = aline.strip()
                if stripped.startswith('<string-array'):
                    indented_lines.append(f"    {stripped}")
                elif stripped.startswith('</string-array'):
                    indented_lines.append(f"    {stripped}")
                elif stripped.startswith('<item'):
                    indented_lines.append(f"        {stripped}")
                else:
                    indented_lines.append(f"    {stripped}")
            output_lines.append(line_ending.join(indented_lines))

    output_lines.append('</resources>')
    output_lines.append('')

    new_content = line_ending.join(output_lines)

    # Write back
    try:
        with open(file_path, 'w', encoding='utf-8', newline='') as f:
            f.write(new_content)
        return True, "Sorted successfully"
    except Exception as e:
        return False, f"Error writing file: {e}"


def get_source_order():
    """Get the order of all elements from the source file.

    Returns:
        list: List of (type, id) tuples in source file order
              where type is 'string', 'plurals', or 'string-array'
    """
    source_file = Path('app/src/main/res/values/strings.xml')
    with open(source_file, 'r', encoding='utf-8') as f:
        content = f.read()

    # Find all elements with their positions
    elements = []

    # Find strings
    for match in re.finditer(r'<string name="([^"]+)"', content):
        elements.append((match.start(), 'string', match.group(1)))

    # Find plurals
    for match in re.finditer(r'<plurals name="([^"]+)"', content):
        elements.append((match.start(), 'plurals', match.group(1)))

    # Find string-arrays
    for match in re.finditer(r'<string-array name="([^"]+)"', content):
        elements.append((match.start(), 'string-array', match.group(1)))

    # Sort by position in file
    elements.sort(key=lambda x: x[0])

    # Return just (type, id) tuples in order
    return [(elem_type, elem_id) for _, elem_type, elem_id in elements]


def sort_all_strings(language=None):
    """Sort strings in language files to match the order in the source file.

    Args:
        language: Optional language code. If None, sorts all translation languages.
    """
    res_dir = Path('app/src/main/res')

    # Get the order from source file
    source_element_order = get_source_order()

    files_to_sort = []

    if language:
        # Sort specific language
        lang_file = res_dir / f'values-{language}' / 'strings.xml'
        if not lang_file.exists():
            print(f"Error: Language file not found: {lang_file}")
            sys.exit(1)
        files_to_sort.append((lang_file, language))
    else:
        # Sort all translation files (source defines the order, so we don't sort it)
        # Get all language directories
        lang_dirs = [d for d in res_dir.glob('values-*')
                     if d.is_dir()
                     and 'night' not in d.name
                     and 'v27' not in d.name
                     and 'v30' not in d.name]

        for lang_dir in sorted(lang_dirs):
            strings_file = lang_dir / 'strings.xml'
            if strings_file.exists():
                lang_name = lang_dir.name.replace('values-', '')
                files_to_sort.append((strings_file, lang_name))

    print("=" * 80)
    print("REORDERING STRINGS TO MATCH SOURCE")
    print("=" * 80)
    print(f"\nFiles to sort: {len(files_to_sort)}")
    print()

    success_count = 0
    error_count = 0

    for file_path, name in files_to_sort:
        success, message = sort_strings_file(file_path, source_element_order)
        if success:
            print(f"[OK] {name}")
            success_count += 1
        else:
            print(f"[ERROR] {name}: {message}")
            error_count += 1

    print("\n" + "=" * 80)
    print("SUMMARY")
    print("=" * 80)
    print(f"Successfully sorted: {success_count}")
    print(f"Errors: {error_count}")
    print("=" * 80)
    sys.exit(0)


def find_unused_strings():
    """Find strings defined in English but not used anywhere in the source code.

    Optimized approach: Read each source file once, use a single regex to find
    ALL string references, then compute the difference.
    """
    import time
    start_time = time.time()

    # Parse strings.xml to get all string names
    strings_file = Path('app/src/main/res/values/strings.xml')
    with open(strings_file, 'r', encoding='utf-8') as f:
        content = f.read()

    string_names = set(re.findall(r'<string name="([^"]+)"', content))
    total_strings = len(string_names)
    print(f"Total strings defined: {total_strings}")

    # Get all source files
    source_patterns = ['**/*.kt', '**/*.java', '**/*.xml']
    source_files = []
    for pattern in source_patterns:
        source_files.extend(list(Path('app/src').rglob(pattern)))

    # Exclude translation strings.xml files but keep arrays.xml and donottranslate.xml
    source_files = [f for f in source_files if 'values-' not in str(f) or 'strings.xml' not in str(f)]

    total_files = len(source_files)
    print(f"Total source files to check: {total_files}")

    file_discovery_time = time.time()
    print(f"  File discovery: {file_discovery_time - start_time:.2f}s")

    print(f"\nScanning files for string usage...")

    # Track all used strings
    used_strings = set()
    checked = 0
    bar_width = 40

    # Patterns to find any string reference (captures the string name)
    patterns = [
        r'R\.string\.(\w+)',           # R.string.name
        r'@string/(\w+)',              # @string/name
        r'"pref_key_(\w+)"',           # "pref_key_name"
    ]
    combined_pattern = '|'.join(patterns)

    # Read each file once and extract all string references
    for source_file in source_files:
        checked += 1
        # Update progress bar
        percent = (checked * 100) // total_files
        filled = (checked * bar_width) // total_files
        bar = '█' * filled + '░' * (bar_width - filled)
        print(f"\r  [{bar}] {percent:3d}% ({checked}/{total_files})", end="", flush=True)

        try:
            with open(source_file, 'r', encoding='utf-8', errors='ignore') as f:
                file_content = f.read()
        except Exception as e:
            print(f"\nError reading {source_file}: {e}")
            continue

        # Find all string references in this file
        matches = re.findall(combined_pattern, file_content)
        for match in matches:
            # match is a tuple of groups, only one will be non-empty
            string_name = next((m for m in match if m), None)
            if string_name:
                used_strings.add(string_name)
                # Also check for pref_key_ prefix pattern
                if string_name.startswith('pref_key_'):
                    used_strings.add(string_name[9:])  # Remove pref_key_ prefix

    # Complete the progress bar
    bar = '█' * bar_width
    scan_time = time.time()
    print(f"\r  [{bar}] 100% ({total_files}/{total_files}) Done! ({scan_time - file_discovery_time:.2f}s)")

    # Find unused strings
    unused = sorted(string_names - used_strings)

    total_time = time.time() - start_time
    print(f"\nTotal time: {total_time:.2f}s")

    if not unused:
        print("No unused strings found!")
        sys.exit(0)

    print(f"\nTotal unused strings: {len(unused)}")
    print("\n" + "="*60)
    print("UNUSED STRINGS FOUND:")
    print("="*60)

    # Simple list
    print("\nUnused strings:")
    for string_name in unused:
        print(f"  - {string_name}")

    # Output command to remove them
    print("\n" + "="*60)
    print("TO REMOVE THESE STRINGS, RUN:")
    print("="*60)
    print("python strings.py --remove", end="")
    for string_name in unused:
        print(f" {string_name}", end="")
    print("\n")

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
        print("Example: python strings.py --raw --set th-rTH string_id \"value\"")
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
            print("Example: python strings.py --get ru-rRU locale_app_name")
            sys.exit(1)
        language = sys.argv[arg_start + 1]
        string_id = sys.argv[arg_start + 2]
        get_string_value(language, string_id)
    # Handle get-plurals command
    elif arg == '--get-plurals':
        if len(sys.argv) < arg_start + 3:
            print("Error: --get-plurals requires 2 arguments: <language> <plurals_name>")
            print("Example: python strings.py --get-plurals ko-rKR notification_incognito_running_title")
            sys.exit(1)
        language = sys.argv[arg_start + 1]
        plurals_name = sys.argv[arg_start + 2]
        get_plurals_value(language, plurals_name)
    # Handle set command
    elif arg == '--set':
        if len(sys.argv) < arg_start + 4:
            print("Error: --set requires: <string_id> <lang> <value> [<lang> <value> ...]")
            print("Examples:")
            print("  python strings.py --set app_name ru-rRU 'Fulguris'")
            print("  python strings.py --set app_name de-rDE 'Fulguris' fr-rFR 'Fulguris' es-rES 'Fulguris'")
            print("  python strings.py --raw --set test ko-rKR '<xliff:g>%s</xliff:g>'")
            print("\nNote: String must exist in English source. Use --add to add new strings to English first.")
            sys.exit(1)

        string_id = sys.argv[arg_start + 1]

        # Parse pairs of language and value
        updates = []  # List of (language, value)
        i = arg_start + 2
        while i < len(sys.argv):
            if i + 1 >= len(sys.argv):
                print(f"Error: Missing value for language '{sys.argv[i]}'")
                sys.exit(1)
            language = sys.argv[i]
            value = sys.argv[i + 1]
            updates.append((language, value))
            i += 2

        # Process all updates
        success_count = 0
        error_count = 0
        for language, value in updates:
            # Validate value first
            is_valid, error_msg = validate_android_string(value)
            if not is_valid:
                print(f"[ERROR] {language}:{string_id} - {error_msg}")
                error_count += 1
                continue

            try:
                set_string_value_quiet(language, string_id, value, skip_escape=skip_escape)
                print(f"[OK] {language}:{string_id}")
                success_count += 1
            except Exception as e:
                print(f"[ERROR] {language}:{string_id} - {e}")
                error_count += 1

        if len(updates) > 1:
            print(f"\nUpdated: {success_count}, Errors: {error_count}")
        sys.exit(0 if error_count == 0 else 1)
    # Handle set-plurals command
    elif arg == '--set-plurals':
        if len(sys.argv) < arg_start + 5:
            print("Error: --set-plurals requires at least 4 arguments: <language> <plurals_name> <quantity> <value> [<quantity> <value> ...]")
            print("Examples:")
            print("  python strings.py --set-plurals ko-rKR notification_incognito_running_title other '%1$d 시크릿 탭 열림'")
            print("  python strings.py --set-plurals ko-rKR tabs_count one '1 tab' other '%1$d tabs'")
            print("  python strings.py --raw --set-plurals ko-rKR cookies other '<xliff:g>%d</xliff:g> cookies'")
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
            print("Example: python strings.py --add new_feature_name \"New Feature\"")
            sys.exit(1)
        string_id = sys.argv[arg_start + 1]
        value = sys.argv[arg_start + 2]
        add_string_to_english(string_id, value)
    # Handle add-plural command
    elif arg == '--add-plural':
        if len(sys.argv) < arg_start + 4:
            print("Error: --add-plural requires at least 3 arguments: <plurals_name> <quantity> <value> [<quantity> <value> ...]")
            print("Example: python strings.py --add-plural item_count one '1 item' other '%d items'")
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
            print("Error: --remove requires at least 1 argument: <string_id> [<string_id2> ...]")
            print("Example: python strings.py --remove obsolete_string")
            print("Example: python strings.py --remove string1 string2 string3")
            sys.exit(1)
        # Collect all string IDs to remove
        string_ids = sys.argv[arg_start + 1:]
        for string_id in string_ids:
            remove_string_from_all(string_id)
            if string_id != string_ids[-1]:
                print()  # Add blank line between removals
        sys.exit(0)
    # Handle check command
    elif arg == '--check':
        # Parse arguments for check command
        i = arg_start + 1
        full_check = False  # Default to incremental check
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
            elif sys.argv[i] == '--full':
                full_check = True
                i += 1
            elif not sys.argv[i].startswith('--'):
                # Language code
                show_all_for_lang = sys.argv[i]
                i += 1
            else:
                print(f"Error: Unknown option '{sys.argv[i]}' for --check")
                sys.exit(1)

        # Print check mode info
        if show_all_for_lang:
            print(f"Will show ALL issues for language: {show_all_for_lang}")
        if full_check:
            if show_all_for_lang and show_all_for_lang.startswith('en-'):
                print("Mode: FULL CHECK (flags strings DIFFERING from English source)")
            else:
                print("Mode: FULL CHECK (flags strings matching English even if in translated file)")
        else:
            print("Mode: INCREMENTAL (only flags missing strings)")
        if near_threshold > 0:
            print(f"Including near matches (≤{near_threshold} char difference)")
        print()

        if near_threshold > 0 and not show_all_for_lang:
            print(f"Checking with near matches (≤{near_threshold} char difference)\n")
        # Continue to check logic below
    # Handle summary command
    elif arg == '--summary':
        summary_only = True
        full_check = False  # Summary uses incremental mode by default
    # Handle unused strings command
    elif arg == '--unused':
        find_unused_strings()
    # Handle changed strings command
    elif arg == '--changed':
        find_changed_strings()
    # Handle sort command
    elif arg == '--sort':
        language = None
        if len(sys.argv) > arg_start + 1:
            language = sys.argv[arg_start + 1]
        sort_all_strings(language)
    # Unknown command
    else:
        print(f"Error: Unknown command '{arg}'")
        print("Run 'python strings.py --help' for usage information")
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
             and 'v30' not in d.name]

# Filter to only the requested language if specified
if show_all_for_lang:
    lang_dirs = [d for d in lang_dirs if d.name == f'values-{show_all_for_lang}']
    if not lang_dirs:
        print(f"Error: Language '{show_all_for_lang}' not found!")
        print(f"Available languages: {', '.join([d.name.replace('values-', '') for d in sorted(res_dir.glob('values-*')) if d.is_dir() and 'night' not in d.name and 'v27' not in d.name and 'v30' not in d.name])}")
        sys.exit(1)

print(f"Checking {len(lang_dirs)} {'language' if len(lang_dirs) == 1 else 'languages'} for translation quality\n")
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

    # Build set of string IDs present in translated file
    translated_string_ids = set()
    for line in trans_content.split('\n'):
        match = re.search(r'<string name="([^"]+)">', line)
        if match:
            translated_string_ids.add(match.group(1))

    # Check each English string
    for string_name, english_value in english_strings.items():
        # Skip exclusions
        if (len(english_value) <= 3 or
            english_value in international_terms or
            string_name.startswith('agent_') or
            string_name.startswith('log_level_') or
            '@string/' in english_value or
            string_name in ['android_open_source_project', 'jsoup', 'infinity', 'search_action']):
            continue

        # Check if string is in translated file
        if string_name not in translated_string_ids:
            # String is MISSING from translated file - always flag this
            lang_issues.append(f"  Missing: {string_name} = '{english_value}'")
            continue

        # String IS in translated file - only check in FULL mode
        if full_check:
            # Get the translated value
            pattern = f'<string name="{re.escape(string_name)}">(.+?)</string>'
            match = re.search(pattern, trans_content)
            if match:
                translated_value = match.group(1)

                # Check if it matches English exactly
                is_exact_match = translated_value == english_value
                char_diff = _char_difference(translated_value, english_value) if near_threshold > 0 else 999
                is_near_match = char_diff <= near_threshold and char_diff > 0

                # For English variants (en-rUS, en-rGB), flag strings that DON'T match
                # For other languages, flag strings that DO match (untranslated)
                is_english_variant = lang_name.startswith('en-')

                if is_english_variant:
                    # English variants: flag differences (potential issues to review)
                    if not is_exact_match:
                        lang_issues.append(f"  Differs from source: {string_name}")
                        lang_issues.append(f"    Source: '{english_value}'")
                        lang_issues.append(f"    en-rUS:  '{translated_value}'")
                else:
                    # Other languages: flag matches (untranslated)
                    if is_exact_match:
                        lang_issues.append(f"  Untranslated: {string_name} = '{english_value}'")
                    elif is_near_match:
                        lang_issues.append(f"  Near match ({char_diff} chars): {string_name}")
                        lang_issues.append(f"    EN: '{english_value}'")
                        lang_issues.append(f"    TR: '{translated_value}'")

    # Check placeholder consistency for strings that ARE in translated file (always check this)
    for line in trans_content.split('\n'):
        match = re.search(r'<string name="([^"]+)">(.+?)</string>', line)
        if match:
            string_name = match.group(1)
            translated_value = match.group(2)

            if string_name not in english_strings:
                continue

            english_value = english_strings[string_name]

            # Placeholder consistency - CRITICAL!
            english_placeholders = re.findall(r'%\d*\$?[sdif]|\{[^}]+\}|<xliff:g[^>]*>.*?</xliff:g>', english_value)
            trans_placeholders = re.findall(r'%\d*\$?[sdif]|\{[^}]+\}|<xliff:g[^>]*>.*?</xliff:g>', translated_value)

            if len(english_placeholders) != len(trans_placeholders):
                lang_issues.append(f"  [CRITICAL] Placeholder mismatch: {string_name}")
                lang_issues.append(f"    English: {english_placeholders}")
                lang_issues.append(f"    Translation: {trans_placeholders}")
                lang_issues.append(f"    WARNING: This WILL cause app crashes!")

    # Check plurals
    is_english_variant = lang_name.startswith('en-')

    for plurals_name, english_quantities in english_plurals.items():
        # Find the plurals block in translated content
        plurals_pattern = f'<plurals name="{re.escape(plurals_name)}">(.*?)</plurals>'
        plurals_match = re.search(plurals_pattern, trans_content, re.DOTALL)

        if not plurals_match:
            # Plurals resource missing entirely - always flag
            lang_issues.append(f"  Missing plurals: {plurals_name}")
            continue

        trans_plurals_content = plurals_match.group(1)
        trans_quantities = _extract_plural_items(trans_plurals_content)

        # Only check plurals content in FULL mode
        if full_check:
            if is_english_variant:
                # English variants: flag plurals that DIFFER from source
                differing_quantities = []
                for quantity, trans_value in trans_quantities.items():
                    if quantity in english_quantities:
                        english_value = english_quantities[quantity]
                        if trans_value != english_value:
                            differing_quantities.append(f"{quantity}: '{trans_value}' (source: '{english_value}')")

                if differing_quantities:
                    lang_issues.append(f"  Plurals '{plurals_name}' differs from source:")
                    for diff in differing_quantities:
                        lang_issues.append(f"    {diff}")
            else:
                # Other languages: flag plurals that MATCH source (untranslated)
                untranslated_quantities = []
                for quantity, trans_value in trans_quantities.items():
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
print("="*80)
