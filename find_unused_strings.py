import re
from pathlib import Path

# Parse strings.xml to get all string names
strings_file = Path('app/src/main/res/values/strings.xml')
with open(strings_file, 'r', encoding='utf-8') as f:
    content = f.read()

string_names = re.findall(r'<string name="([^"]+)"', content)
print(f"Total strings defined: {len(string_names)}")

# Get all source files
source_patterns = ['**/*.kt', '**/*.java', '**/*.xml']
source_files = []
for pattern in source_patterns:
    source_files.extend(list(Path('app/src').rglob(pattern)))

# Exclude translation strings.xml files but keep arrays.xml and donottranslate.xml
source_files = [f for f in source_files if 'values-' not in str(f) or 'strings.xml' not in str(f)]

print(f"Total source files to check: {len(source_files)}")

unused = []

for string_name in string_names:
    found = False
    patterns = [
        rf'R\.string\.{string_name}\b',
        rf'@string/{string_name}\b',
        rf'pref_key_{string_name}',
        rf'"pref_key_{string_name}"',  # Preference key in strings
    ]

    for source_file in source_files:
        try:
            with open(source_file, 'r', encoding='utf-8', errors='ignore') as f:
                file_content = f.read()
                if any(re.search(pattern, file_content) for pattern in patterns):
                    found = True
                    break
        except Exception as e:
            # Log errors instead of silently ignoring
            print(f"Error reading {source_file}: {e}")
            continue

    if not found:
        unused.append(string_name)

print(f"\nTotal unused strings: {len(unused)}")
print("\n" + "="*60)
print("UNUSED STRINGS FOUND:")
print("="*60)

if unused:
    # Output format for PowerShell array (can be copied directly)
    print("\n# PowerShell array format (copy this):")
    print("@(")
    for i, string_name in enumerate(unused):
        comma = "," if i < len(unused) - 1 else ""
        print(f"    '{string_name}'{comma}")
    print(")")

    # Also output as simple list
    print("\n# Simple list:")
    for string_name in unused:
        print(f"  - {string_name}")

    # Save to file
    with open('unused_strings_list.txt', 'w') as f:
        f.write('\n'.join(unused))
    print(f"\nSaved to unused_strings_list.txt")

    # Output command to remove them
    print("\n" + "="*60)
    print("TO REMOVE THESE STRINGS, RUN:")
    print("="*60)
    print(".\remove_string.ps1 -StringNames @(")
    for i, string_name in enumerate(unused):
        comma = "," if i < len(unused) - 1 else ""
        print(f"    '{string_name}'{comma}")
    print(")")
else:
    print("\nNo unused strings found!")

