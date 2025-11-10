#!/usr/bin/env python
# Complete remaining Korean translations with complex XML

import re
from pathlib import Path

translations = {
    'message_ssl_error': '보안 연결이 손상되었습니다:\\n<xliff:g id="domain_name" example="my.example.com"><b>%1$s</b></xliff:g>\\n\\n<xliff:g id="error_list" example="Certificate is invalid">%2$s</xliff:g>계속하시겠습니까?',
    'session_prompt_confirm_deletion_message': '세션 \\"<xliff:g id="session_name" example="Shopping">%s</xliff:g>\\"을(를) 정말로 삭제하시겠습니까?',
    'configuration_prompt_confirm_deletion_message': '\\"<xliff:g id="config_name" example="Landscape - 270° - sw718dp">%s</xliff:g>\\" 구성을 정말로 삭제하시겠습니까?',
    'session_switched': '\\"%s\\"(으)로 전환됨',
    'match_x_of_n': '<xliff:g id="current_match" example="1">%1$d</xliff:g>개 중 <xliff:g id="match_count" example="10">%2$d</xliff:g>번째 일치',
    'message_session_imported': '가져온 세션: <xliff:g id="session_name" example="News">%s</xliff:g>',
    'message_session_exported': '내보낸 세션: <xliff:g id="session_name" example="Cooking">%s</xliff:g>',
}

file_path = Path('app/src/main/res/values-ko-rKR/strings.xml')

# Read the file
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Update each translation
for string_id, new_value in translations.items():
    escaped_id = re.escape(string_id)
    pattern = f'(<string name="{escaped_id}">)([^<]*)(</string>)'

    if re.search(pattern, content):
        content = re.sub(pattern, f'\\1{new_value}\\3', content)
        print(f"[OK] Updated {string_id}")
    else:
        print(f"[SKIP] Not found: {string_id}")

# Write back
with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("\nCompleted!")

