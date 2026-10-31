"""Source-level gates only, not a Next or native app execution claim."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
web=(root/'apps/web/components/ShellFrame.tsx').read_text()
for marker in ("'use client'",'useState<Locale>',"lang={locale}",'aria-pressed',"translate(locale,key)","new Set()",'aria-disabled="true"','aria-describedby'):
    assert marker in web,marker
assert '<Link key={e.id}' in web and 'allowedWorkspaces' in web
css=(root/'apps/web/app/globals.css').read_text()
assert '[aria-pressed="true"]' in css and 'min-height:44px' in css
assert 'overflow:hidden' not in css and 'linear-gradient(' not in css
print('PASS: bilingual, denied-by-default web shell and restrained responsive style source assertions')
