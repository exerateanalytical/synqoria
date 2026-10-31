"""Static HTML specimen only. Uses CSS from Next source, not compiled Next/React."""
from pathlib import Path
from playwright.sync_api import sync_playwright
import json
r=Path(__file__).resolve().parents[1]
css=(r/'apps/web/app/globals.css').read_text()
src=(r/'preview/registration-intake.html').read_text()
html=src.replace('<link rel="stylesheet" href="../apps/web/app/globals.css">','<style>'+css+'</style>')
checks=[]
with sync_playwright() as p:
 browser=p.chromium.launch(headless=True,executable_path='/usr/bin/chromium',args=['--no-sandbox'])
 try:
  for width in (320,360,390,412,768,1024,1280,1440,1920):
   page=browser.new_page(viewport={'width':width,'height':850})
   page.set_content(html)
   for locale in ('en','fr'):
    page.locator('#'+locale).click()
    assert page.locator('html').get_attribute('lang')==locale
    assert page.locator('#save').is_disabled()
    assert not page.evaluate('document.documentElement.scrollWidth > innerWidth'),(width,locale)
    assert page.locator('input[name="birth"]').count()==3
    assert page.locator('input[type="text"]').count()==1
    assert page.locator('.intake-alert').is_visible()
    checks.append({'width':width,'locale':locale,'overflow':False,'submit':'disabled'})
    if width in (390,1440) and locale=='fr':page.screenshot(path=str(r/f'evidence/batches/P02-v120-intake-fr-{width}.png'),full_page=True)
   page.close()
  page=browser.new_page(viewport={'width':320,'height':850})
  page.set_content(html)
  page.locator('#fr').click()
  page.add_style_tag(content='html{font-size:32px!important}body{font-size:2rem!important}')
  assert not page.evaluate('document.documentElement.scrollWidth > innerWidth'),'scaled text overflow'
  checks.append({'width':320,'locale':'fr','text_scale':'simulated_200_percent','overflow':False})
  page.close()
 finally:browser.close()
(r/'evidence/batches/P02-v120-registration-browser.json').write_text(json.dumps({'scope':'standalone HTML + actual global CSS; NOT compiled Next.js, NOT React behavior, NOT native','checks':checks},indent=2)+'\n')
print(f'PASS registration static specimen: {len(checks)} viewport/locale checks; no compiled application')
