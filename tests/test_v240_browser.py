"""Five individually executed P02 UX batches against live Chromium static specimen, NOT Next/Expo build."""
import sys,json
from pathlib import Path
from playwright.sync_api import sync_playwright
root=Path(__file__).resolve().parents[1]
css=(root/'apps/web/app/globals.css').read_text()
html=(root/'preview/operational-shell.html').read_text().replace('<link rel="stylesheet" href="../apps/web/app/globals.css">','<style>'+css+'</style>')
checks=0
def ok(value,msg):
 global checks
 assert value,msg
 checks+=1
batch=sys.argv[1]
with sync_playwright() as p:
 browser=p.chromium.launch(headless=True,executable_path='/usr/bin/chromium',args=['--no-sandbox'])
 try:
  page=browser.new_page(viewport={'width':390,'height':844})
  page.set_content(html,wait_until='load')
  if batch=='P02-B0078':
   for shell,title in [('portal','Patient and proxy portal'),('platform','Platform operations'),('workforce','Clinical workforce')]:
    page.locator('[data-shell="'+shell+'"]').click();ok(page.locator('#heading').inner_text()==title,'wrong shell title')
    ok(page.locator('[data-shell="'+shell+'"]').get_attribute('aria-current')=='page','missing current shell')
   ok(page.locator('.sidebar nav a').count()==0,'unauthorized module navigation present')
  elif batch=='P02-B0079':
   page.locator('#fr').click();ok(page.locator('html').get_attribute('lang')=='fr','locale lang');ok('Connexion requise' in page.locator('#status-heading').inner_text(),'status locale')
   page.locator('[data-shell="platform"]').click();ok('Administration de la plateforme' in page.locator('#heading').inner_text(),'long FR label')
   ok(page.locator('#fr').get_attribute('aria-pressed')=='true','aria pressed');page.locator('#en').click();ok(page.locator('#en').get_attribute('aria-pressed')=='true','EN restored')
  elif batch=='P02-B0080':
   nav=page.locator('#bottom');ok(nav.is_visible(),'mobile bottom absent');ok(nav.locator('a,span,button').count()==4,'not four destinations')
   ok(page.locator('#more').inner_text()=='More','More missing');ok(page.locator('#tasks').get_attribute('aria-disabled')=='true','locked task not indicated')
   page.set_viewport_size({'width':1024,'height':900});ok(not nav.is_visible(),'mobile bottom on desktop');ok(page.locator('.sidebar').is_visible(),'desktop sidebar hidden')
  elif batch=='P02-B0081':
   page.keyboard.press('Tab');ok(page.evaluate('document.activeElement.id')=='skip','skip link not first keyboard target')
   ok(page.locator('#action').is_disabled(),'clinical action enabled');ok(page.locator('#action').get_attribute('aria-describedby')=='reason','missing disabled explanation')
   ok(page.locator('#action').get_attribute('aria-disabled')=='true','aria disabled');ok(page.locator('#reason').inner_text().startswith('Verified identity'),'missing safe reason')
   ok(page.locator('body').evaluate('(e)=>getComputedStyle(e).backgroundColor')=='rgb(247, 245, 240)','Warm Clinical canvas mismatch')
  elif batch=='P02-B0082':
   for width in [320,360,390,412,768,1024,1280,1440,1920]:
    page.set_viewport_size({'width':width,'height':900});ok(not page.evaluate('document.documentElement.scrollWidth>innerWidth'),f'overflow {width}')
    ok(page.locator('#action').is_disabled(),f'unsafe action {width}')
    if width in [320,390,1440]:page.screenshot(path=str(root/f'evidence/batches/v240-locked-shell-{width}.png'),full_page=True)
   page.set_viewport_size({'width':320,'height':900});page.add_style_tag(content='html{font-size:32px!important}body{font-size:2rem!important}')
   ok(not page.evaluate('document.documentElement.scrollWidth>innerWidth'),'200% font overflow')
   page.locator('#fr').click();ok(not page.evaluate('document.documentElement.scrollWidth>innerWidth'),'FR 200% font overflow')
  else:raise ValueError('Unknown batch')
  print('PASS',batch,checks,'Chromium assertions (static interactive specimen; NOT compiled Next.js)')
  page.close()
 finally:browser.close()
