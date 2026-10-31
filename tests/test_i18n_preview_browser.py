"""Real Chromium static specimen checks; deliberately NOT a Next.js or Expo build."""
from pathlib import Path
from playwright.sync_api import sync_playwright
import json
root=Path(__file__).resolve().parents[1]
css=(root/'apps/web/app/globals.css').read_text()
html=(root/'preview/i18n-shell.html').read_text().replace('<link rel="stylesheet" href="../apps/web/app/globals.css">','<style>'+css+'</style>')
checks=[]
with sync_playwright() as p:
 browser=p.chromium.launch(headless=True,executable_path='/usr/bin/chromium',args=['--no-sandbox'])
 try:
  for width in (320,360,390,412,768,1024,1280,1440,1920):
   page=browser.new_page(viewport={'width':width,'height':900})
   page.set_content(html,wait_until='load')
   for locale in ('en','fr'):
    page.get_by_role('button',name=locale.upper()).click()
    assert page.locator('html').get_attribute('lang')==locale
    assert page.locator('#cta').is_disabled()
    assert not page.evaluate('document.documentElement.scrollWidth > innerWidth'),f'overflow {width} {locale}'
    assert page.locator('.sidebar').is_visible()==(width>=768)
    assert page.locator('.mobile-bottom').is_visible()==(width<768)
    assert not page.locator('.sidebar nav').count(), 'unauthorized workspaces shown'
    checks.append({'width':width,'locale':locale,'overflow':False,'unauthorized_workspace_links':0,'status':'PASS'})
    if width in (390,1440) and locale=='fr':
     page.screenshot(path=str(root/f'evidence/batches/P02-B0002-fr-{width}.png'),full_page=True)
   page.close()
  page=browser.new_page(viewport={'width':320,'height':850})
  page.set_content(html)
  page.get_by_role('button',name='FR').click()
  page.add_style_tag(content='html{font-size:32px!important}body{font-size:2rem!important}')
  assert not page.evaluate('document.documentElement.scrollWidth>innerWidth'),'200% text horizontal overflow'
  checks.append({'width':320,'locale':'fr','simulated_text_scale':'200%','overflow':False,'status':'PASS'})
  page.close()
 finally:browser.close()
(root/'evidence/batches/P02-B0002-browser.json').write_text(json.dumps({'scope':'static standalone EN/FR specimen; NOT Next or native application','chromium_checks':checks},indent=2)+'\n')
print(f'PASS: {len(checks)} static Chromium bilingual/viewport checks; NOT an application build')
