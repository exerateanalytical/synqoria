from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
css=(root/'apps/web/app/globals.css').read_text()
for forbidden in ['linear-gradient(', 'radial-gradient(', 'box-shadow:', 'text-shadow:', 'filter:drop-shadow(', 'backdrop-filter:', 'overflow:hidden', 'transform:scale(']:
    assert forbidden not in css, f'Forbidden design pattern: {forbidden}'
for app in ('staff-mobile','patient-mobile'):
    text=(root/f'apps/{app}/App.tsx').read_text()
    assert "Not connected" in text
    assert 'accessibilityRole="tab"' in text
    assert text.count("id:'")>=4
    assert '#F7F5F0' in text and '#073656' in text
    assert 'clinical' in text.lower() or 'medical' in text.lower()
assert (root/'apps/web/app/workforce/page.tsx').exists()
assert (root/'apps/web/app/portal/page.tsx').exists()
assert (root/'apps/web/app/platform/page.tsx').exists()
print('PASS: static design prohibitions, native shell source and 3 web shell sources')
