"""P02-B0094: Chromium static CSS reflow at 9 widths and short viewport, not Next build."""
from pathlib import Path
from playwright.sync_api import sync_playwright
root=Path(__file__).resolve().parents[1]
css=(root/'apps/web/app/globals.css').read_text()
html=(root/'preview/operational-shell.html').read_text().replace('<link rel="stylesheet" href="../apps/web/app/globals.css">','<style>'+css+'</style>')
assert 'max-height:min(40dvh' in css and 'scrollbar-gutter:stable' in css
checks=0
with sync_playwright() as p:
    browser=p.chromium.launch(executable_path='/usr/bin/chromium',args=['--no-sandbox'])
    try:
        page=browser.new_page(viewport={'width':390,'height':540})
        page.set_content(html)
        page.locator('#fr').click()
        for width in (320,360,390,412,768,1024,1280,1440,1920):
            page.set_viewport_size({'width':width,'height':540})
            assert not page.evaluate('document.documentElement.scrollWidth>innerWidth'),f'horizontal overflow {width}'
            assert page.locator('#action').is_disabled()
            checks+=2
            if width<768:
                assert page.locator('#bottom').is_visible()
                assert page.locator('#bottom').locator('a,span,button').count()==4
                checks+=2
        page.set_viewport_size({'width':320,'height':460})
        page.locator('#more').click()
        page.add_style_tag(content='html{font-size:32px!important}body{font-size:2rem!important}')
        panel=page.locator('#mobile-more-panel')
        assert panel.is_visible()
        box=panel.bounding_box()
        nav=page.locator('#bottom').bounding_box()
        assert box and nav and box['y']+box['height'] <= nav['y']+1,(box,nav)
        assert box['y']>=0
        assert not page.evaluate('document.documentElement.scrollWidth>innerWidth')
        assert panel.evaluate('(e)=>getComputedStyle(e).overflowY')=='auto'
        assert page.locator('#action').is_disabled()
        checks+=6
        page.screenshot(path=str(root/'evidence/batches/v280-static-320-short-200pct.png'),full_page=True)
    finally:browser.close()
print(f'PASS P02-B0094: {checks} Chromium CSS checks; 9 widths, FR, 200% font, 460px keyboard-like viewport (static specimen)')
