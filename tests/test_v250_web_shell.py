"""Chromium tests on static interactive specimen; not a compiled Next.js application."""
from pathlib import Path
import sys
from playwright.sync_api import sync_playwright
root=Path(__file__).resolve().parents[1]
css=(root/'apps/web/app/globals.css').read_text()
source=(root/'apps/web/components/ShellFrame.tsx').read_text()
html=(root/'preview/operational-shell.html').read_text().replace('<link rel="stylesheet" href="../apps/web/app/globals.css">','<style>'+css+'</style>')
batch=sys.argv[1];count=0
def ok(condition,label):
 global count
 assert condition,label
 count+=1
with sync_playwright() as p:
 browser=p.chromium.launch(headless=True,executable_path='/usr/bin/chromium',args=['--no-sandbox'])
 try:
  page=browser.new_page(viewport={'width':390,'height':844})
  page.set_content(html,wait_until='load')
  if batch=='P02-B0084':
   ok('aria-controls="mobile-more-panel"' in source,'Next source missing More semantics')
   ok('event.key===\'Escape\'' in source,'Next source missing Escape')
   ok(page.locator('#bottom').locator('a,span,button').count()==4,'too many mobile destinations')
   ok(page.locator('#mobile-more-panel').is_hidden(),'More visible initially')
   page.locator('#more').click()
   ok(page.locator('#more').get_attribute('aria-expanded')=='true','expanded state')
   ok(page.locator('#mobile-more-panel').is_visible(),'More not visible')
   ok(page.locator('#mobile-more-panel a').count()==0,'unauthorized More navigation')
   page.locator('#more-fr').click()
   ok(page.locator('html').get_attribute('lang')=='fr','More locale switch')
   ok(page.locator('#more').inner_text()=='Plus','French More label')
   page.keyboard.press('Escape')
   ok(page.locator('#mobile-more-panel').is_hidden(),'Escape did not close')
   ok(page.evaluate('document.activeElement.id')=='more','focus not restored')
   ok(page.locator('#action').is_disabled(),'clinical action enabled')
  elif batch=='P02-B0085':
   ok('mobile-more-panel' in css and 'max-height:min(60vh,500px)' in css,'source responsive More policy')
   page.locator('#more').click();page.locator('#more-fr').click()
   for width in [320,360,390,412,768,1024,1280,1440,1920]:
    page.set_viewport_size({'width':width,'height':900})
    ok(not page.evaluate('document.documentElement.scrollWidth>innerWidth'),f'horizontal overflow {width}')
    ok(page.locator('#action').is_disabled(),f'clinical action enabled {width}')
    if width in (320,390,1440):page.screenshot(path=str(root/f'evidence/batches/v250-shell-{width}.png'),full_page=True)
   page.set_viewport_size({'width':320,'height':900})
   page.add_style_tag(content='html{font-size:32px!important}body{font-size:2rem!important}')
   ok(not page.evaluate('document.documentElement.scrollWidth>innerWidth'),'200% overflow')
   ok(page.locator('#action').is_disabled(),'clinical action enabled at 200%')
   page.locator('#more').click() # close
   ok(page.locator('#mobile-more-panel').is_hidden(),'More close failed')
  else:raise ValueError('Unknown batch')
  print('PASS',batch,count,'Chromium assertions; static specimen and source contract, NOT Next build')
 finally:browser.close()
