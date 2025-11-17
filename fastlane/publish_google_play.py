"""
Upload Fulguris metadata to Google Play Store using Python
No Ruby/Fastlane required!

Installation:
    pip install google-api-python-client google-auth-httplib2 google-auth-oauthlib

Usage:
    python upload_metadata.py path/to/service-account.json
"""

import sys
import os
from googleapiclient.discovery import build
from google.oauth2 import service_account

SCOPES = ['https://www.googleapis.com/auth/androidpublisher']
PACKAGE_NAME = 'net.slions.fulguris.full.playstore'  # Update with your actual package name if different

def upload_metadata(service_account_file):
    """Upload metadata for all languages to Google Play Store."""

    # Authenticate
    print("🔐 Authenticating with Google Play API...")
    credentials = service_account.Credentials.from_service_account_file(
        service_account_file, scopes=SCOPES)

    service = build('androidpublisher', 'v3', credentials=credentials)

    # Start edit
    print(f"📝 Starting edit for package: {PACKAGE_NAME}")
    edit_request = service.edits().insert(body={}, packageName=PACKAGE_NAME)
    edit = edit_request.execute()
    edit_id = edit['id']
    print(f"✅ Edit ID: {edit_id}")

    # Languages to upload (Google Play Console language codes)
    # See: https://support.google.com/googleplay/android-developer/table/4419860?hl=en
    languages = [
        'en-US',  # English (US)
        'ar',     # Arabic
        'cs-CZ',  # Czech
        'da-DK',  # Danish
        'de-DE',  # German
        'el-GR',  # Greek
        'es-ES',  # Spanish
        'fi-FI',  # Finnish
        'fr-FR',  # French
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

    uploaded = 0
    failed = 0
    skipped_not_enabled = []

    # Get the directory where this script is located (fastlane/)
    script_dir = os.path.dirname(os.path.abspath(__file__))

    # Upload listing for each language
    for lang in languages:
        # Path is relative to script location
        metadata_dir = os.path.join(script_dir, 'metadata', 'android', lang)

        if not os.path.exists(metadata_dir):
            print(f"⚠️  Skipping {lang} - directory not found")
            continue

        try:
            # Read metadata files
            title_file = os.path.join(metadata_dir, 'title.txt')
            short_desc_file = os.path.join(metadata_dir, 'short_description.txt')
            full_desc_file = os.path.join(metadata_dir, 'full_description.txt')

            with open(title_file, 'r', encoding='utf-8') as f:
                title = f.read().strip()
            with open(short_desc_file, 'r', encoding='utf-8') as f:
                short_desc = f.read().strip()
            with open(full_desc_file, 'r', encoding='utf-8') as f:
                full_desc = f.read().strip()

            # Validate lengths
            if len(title) > 30:
                print(f"⚠️  {lang}: Title too long ({len(title)} chars, max 30)")
            if len(short_desc) > 80:
                print(f"⚠️  {lang}: Short description too long ({len(short_desc)} chars, max 80)")
            if len(full_desc) > 4000:
                print(f"⚠️  {lang}: Full description too long ({len(full_desc)} chars, max 4000)")

            # Upload listing
            listing_body = {
                'title': title,
                'shortDescription': short_desc,
                'fullDescription': full_desc
            }

            try:
                # Try to update existing listing
                service.edits().listings().update(
                    editId=edit_id,
                    packageName=PACKAGE_NAME,
                    language=lang,
                    body=listing_body
                ).execute()
                print(f'✅ Uploaded {lang:6s} - {title}')
                uploaded += 1
            except Exception as update_error:
                if 'not currently supported' in str(update_error) or 'does not exist' in str(update_error):
                    # Try to create new listing instead
                    try:
                        service.edits().listings().patch(
                            editId=edit_id,
                            packageName=PACKAGE_NAME,
                            language=lang,
                            body=listing_body
                        ).execute()
                        print(f'✅ Created {lang:6s} - {title}')
                        uploaded += 1
                    except Exception as create_error:
                        raise update_error  # Re-raise original error
                else:
                    raise update_error

        except Exception as e:
            error_msg = str(e)
            if 'not currently supported' in error_msg or 'language is not currently supported' in error_msg:
                skipped_not_enabled.append(lang)
                print(f'⏭️  Skipped {lang:6s} - Not enabled in Play Console')
            else:
                print(f'❌ Failed {lang}: {error_msg}')
                failed += 1

    # Commit changes
    print("\n💾 Committing changes...")
    try:
        service.edits().commit(editId=edit_id, packageName=PACKAGE_NAME).execute()
        commit_success = True
    except Exception as e:
        commit_success = False
        error_msg = str(e)
        print(f"\n❌ ERROR: COMMIT FAILED!")
        print(f"{'='*60}")
        if 'financial features' in error_msg.lower():
            print(f"⚠️  Reason: {error_msg}")
            print(f"\n📋 ACTION REQUIRED:")
            print(f"1. Go to: https://play.google.com/console/")
            print(f"2. Select your app")
            print(f"3. Go to: Policy → App content")
            print(f"4. Answer the 'Financial features' question")
            print(f"5. Save and run this script again")
            print(f"\n⚠️  Your metadata was prepared but NOT published.")
        else:
            print(f"⚠️  Reason: {error_msg}")
            print(f"\n⚠️  Your metadata was prepared but NOT published.")
        print(f"{'='*60}")

    if commit_success:
        print(f"\n{'='*60}")
        print(f"🎉 Upload complete!")
        print(f"{'='*60}")
    else:
        print(f"\n{'='*60}")
        print(f"❌ UPLOAD FAILED - Changes not published")
        print(f"{'='*60}")

    print(f"✅ Successfully uploaded: {uploaded} languages")
    if len(skipped_not_enabled) > 0:
        print(f"⏭️  Skipped (not enabled): {len(skipped_not_enabled)} languages")
        print(f"   Languages: {', '.join(skipped_not_enabled)}")
    if failed > 0:
        print(f"❌ Failed: {failed} languages")

    if len(skipped_not_enabled) > 0:
        print(f"\n📋 TO ENABLE SKIPPED LANGUAGES:")
        print(f"1. Go to: https://play.google.com/console/")
        print(f"2. Select your app → Store presence → Main store listing")
        print(f"3. Click 'Add language' and add these languages:")
        print(f"   {', '.join(skipped_not_enabled)}")
        print(f"4. Run this script again to upload metadata for them")

    if commit_success:
        print(f"\n🌍 Your app is now available in {uploaded} languages!")

    return commit_success

if __name__ == '__main__':
    if len(sys.argv) != 2:
        print("Usage: python upload_metadata.py path/to/service-account.json")
        sys.exit(1)

    service_account_file = sys.argv[1]

    if not os.path.exists(service_account_file):
        print(f"❌ Error: Service account file not found: {service_account_file}")
        sys.exit(1)

    success = upload_metadata(service_account_file)
    sys.exit(0 if success else 1)

