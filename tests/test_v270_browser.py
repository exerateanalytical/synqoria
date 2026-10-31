"""P02-B0090/P02-B0092: Chromium on static CSS specimen, NOT compiled Next.js."""
import pathlib,sys
from playwright.sync_api import sync_playwright
ROOT=pathlib.Path(__file__).resolve().parents[1]
css=(ROOT/'apps/web/app/globals.css').read_text()
html=(ROOT/'preview/operational-shell.html').read_text().replace('<link rel="stylesheet" href="../apps/web/app/globals.css">','<style>'+css+'</style>')
batch=sys.argv[1];count=0
def check(ok,label):
 global count
 assert ok,label
 count+=1
with sync_playwright() as p:
 browser=p.chromium.launch(headless=True,executable_path='/usr/bin/chromium',args=['--no-sandbox'])
 try:
  page=browser.new_page(viewport={'width':390,'height':844})
  page.set_content(html,wait_until='load')
  if batch=='P02-B0090':
   check(page.locator('#mobile-more-panel').is_hidden(),'hidden before click')
   page.locator('#more').click()
   check(page.locator('#mobile-more-panel').is_visible(),'panel visible')
   check(page.locator('#mobile-more-panel').evaluate('(e)=>getComputedStyle(e).position')=='fixed','not anchored')
   check(page.locator('#mobile-more-panel').evaluate('(e)=>getComputedStyle(e).overflowY')=='auto','not scrollable')
   check(page.locator('#more').get_attribute('aria-expanded')=='true','expanded attr')
   page.wait_for_timeout(70)
   check(page.evaluate('document.activeElement.id')=='more-en','focus not in panel')
   page.keyboard.press('Escape')
   check(page.locator('#mobile-more-panel').is_hidden(),'Escape close')
   check(page.evaluate('document.activeElement.id')=='more','focus not returned')
   check(page.locator('#action').is_disabled(),'clinical action available')
  elif batch=='P02-B0092':
   page.locator('#fr').click()
   for width in [320,360,390,412,768,1024,1280,1440,1920]:
    page.set_viewport_size({'width':width,'height':900})
    check(not page.evaluate('document.documentElement.scrollWidth>innerWidth'),f'overflow width={width}')
    check(page.locator('#action').is_disabled(),f'clinical enabled width={width}')
    if width<768:
     check(page.locator('#bottom').is_visible(),f'bottom hidden width={width}')
     check(page.locator('#bottom').locator('a,span,button').count()==4,f'bottom destinations width={width}')
   page.set_viewport_size({'width':320,'height':900})
   page.locator('#more').click()
   page.add_style_tag(content='html{font-size:32px!important}body{font-size:2rem!important}')
   check(not page.evaluate('document.documentElement.scrollWidth>innerWidth'),'200% overflow')
   check(page.locator('#action').is_disabled(),'clinical enabled at 200%')
   check(page.locator('#mobile-more-panel').is_visible(),'More missing at 200%')
   page.screenshot(path=str(ROOT/'evidence/batches/v270-static-shell-320-200pct.png'),full_page=True)
  else:raise ValueError(batch)
  print('PASS',batch,count,'Chromium static CSS/specimen assertions; NO Next.js build')
 finally:browser.close()
