"""
Generate changelog template for all supported languages and copy to clipboard.

Usage:
    python changelogs.py 239
    python changelogs.py 239 240
    python changelogs.py 238 239 240

Arguments:
    version_codes: One or more version numbers to compile changelogs for (e.g., 239 240)
                   Multiple changelogs will be concatenated with separator lines
"""

import sys
import os

# Languages from publish_google_play.py (Google Play Console language codes)
# See: https://support.google.com/googleplay/android-developer/table/4419860?hl=en
languages = [
    'en-US',  # English (US)
    'en-GB',  # English (UK)
    'ar',     # Arabic
    'cs-CZ',  # Czech
    'da-DK',  # Danish
    'de-DE',  # German
    'el-GR',  # Greek
    'es-ES',  # Spanish
    'fi-FI',  # Finnish
    'fr-FR',  # French
    'hi-IN',  # Hindi
    'hr',     # Croatian
    'hu-HU',  # Hungarian
    'id',     # Indonesian
    'it-IT',  # Italian
    'ja-JP',  # Japanese
    'ko-KR',  # Korean
    'lt',     # Lithuanian
    'nl-NL',  # Dutch
    'no-NO',  # Norwegian
    'pl-PL',  # Polish
    'pt-BR',  # Portuguese (Brazil)
    'pt-PT',  # Portuguese (Portugal)
    'ro',     # Romanian
    'ru-RU',  # Russian
    'sr',     # Serbian
    'sv-SE',  # Swedish
    'th',     # Thai
    'tr-TR',  # Turkish
    'uk',     # Ukrainian
    'vi',     # Vietnamese
    'zh-CN',  # Chinese (Simplified)
    'zh-TW',  # Chinese (Traditional)
]

def generate_template(version_codes):
    """Generate the changelog template from existing files for multiple versions."""
    script_dir = os.path.dirname(os.path.abspath(__file__))
    template = ""
    found_count = 0
    missing = []

    # If single version, process normally
    if len(version_codes) == 1:
        version_code = version_codes[0]
        for lang in languages:
            changelog_file = os.path.join(script_dir, 'metadata', 'android', lang, 'changelogs', f'{version_code}.txt')

            if os.path.exists(changelog_file):
                try:
                    with open(changelog_file, 'r', encoding='utf-8') as f:
                        content = f.read().strip()
                    template += f"<{lang}>\n{content}\n</{lang}>\n\n"
                    found_count += 1
                except Exception as e:
                    print(f"⚠️  Error reading {lang}: {e}")
                    template += f"<{lang}>\nError reading file\n</{lang}>\n\n"
                    missing.append(lang)
            else:
                template += f"<{lang}>\nEnter or paste your release notes for {lang} here\n</{lang}>\n\n"
                missing.append(lang)
    else:
        # Multiple versions - concatenate changelogs
        for lang in languages:
            concatenated_content = []
            all_found = True

            for version_code in version_codes:
                changelog_file = os.path.join(script_dir, 'metadata', 'android', lang, 'changelogs', f'{version_code}.txt')

                if os.path.exists(changelog_file):
                    try:
                        with open(changelog_file, 'r', encoding='utf-8') as f:
                            content = f.read().strip()
                        concatenated_content.append(content)
                    except Exception as e:
                        print(f"⚠️  Error reading {lang} v{version_code}: {e}")
                        all_found = False
                        break
                else:
                    all_found = False
                    break

            if all_found and concatenated_content:
                # Join multiple changelogs with separator
                separator = "\n\n---\n\n"
                combined = separator.join(concatenated_content)
                template += f"<{lang}>\n{combined}\n</{lang}>\n\n"
                found_count += 1
            else:
                template += f"<{lang}>\nEnter or paste your release notes for {lang} here\n</{lang}>\n\n"
                missing.append(lang)

    return template.strip(), found_count, missing

def copy_to_clipboard(text):
    """Copy text to clipboard using PowerShell Set-Clipboard for proper UTF-8 encoding."""
    try:
        import subprocess
        # Use PowerShell's Set-Clipboard which handles UTF-8 properly
        ps_command = ['powershell', '-NoProfile', '-Command', f'Set-Clipboard -Value @\"\n{text}\n\"@']
        subprocess.run(ps_command, check=True)
        return True
    except Exception as e:
        print(f"❌ Failed to copy to clipboard: {e}")
        # Fallback: try to save to a file
        try:
            with open('changelog_template.txt', 'w', encoding='utf-8') as f:
                f.write(text)
            print("💾 Saved to changelog_template.txt instead")
        except:
            pass
        return False

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: python changelogs.py <version_code> [version_code2] [version_code3] ...")
        print("\nExamples:")
        print("  python changelogs.py 239")
        print("  python changelogs.py 239 240")
        print("  python changelogs.py 238 239 240")
        sys.exit(1)

    version_codes = sys.argv[1:]

    if len(version_codes) == 1:
        print(f"📋 Compiling changelog template for version {version_codes[0]}...")
    else:
        print(f"📋 Compiling and concatenating changelog templates for versions: {', '.join(version_codes)}...")

    template, found_count, missing = generate_template(version_codes)

    print(f"✅ Found changelogs for {found_count}/{len(languages)} languages")

    if missing:
        print(f"⚠️  Missing changelogs for {len(missing)} languages: {', '.join(missing)}")

    if copy_to_clipboard(template):
        print("✅ Template copied to clipboard!")
        if len(version_codes) > 1:
            print(f"   Changelogs from versions {', '.join(version_codes)} have been concatenated.")
        print("\nYou can now paste it into Google Play Console's bulk changelog editor.")
    else:
        print("\n⚠️  Could not copy to clipboard automatically.")
        print("\nTemplate content:")
        print("=" * 60)
        print(template[:500] + "..." if len(template) > 500 else template)
        print("=" * 60)

    sys.exit(0)

