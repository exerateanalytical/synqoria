"""Real Chromium browser checks of the static shell preview, not a Next.js app build."""
from pathlib import Path
from playwright.sync_api import sync_playwright
import json
root=Path(__file__).resolve().parents[1]
css=(root/'apps/web/app/globals.css').read_text()
html=(root/'preview/index.html').read_text().replace('<link rel="stylesheet" href="../apps/web/app/globals.css">','<style>'+css+'</style>')
report=[]
widths=[320,360,390,412,768,1024,1280,1440,1920]
with sync_playwright() as p:
    browser=p.chromium.launch(headless=True,executable_path='/usr/bin/chromium',args=['--no-sandbox'])
    try:
        for width in widths:
            page=browser.new_page(viewport={'width':width,'height':900},device_scale_factor=1)
            page.set_content(html,wait_until='load')
            overflow=page.evaluate('document.documentElement.scrollWidth > window.innerWidth')
            heading=page.get_by_role('heading',name='Workforce',exact=True)
            assert heading.is_visible(),f'{width}: heading missing'
            assert not overflow,f'{width}: horizontal overflow'
            assert page.get_by_role('button',name='Open work queue').is_disabled(),f'{width}: unconfigured clinical action enabled'
            bottom=page.locator('.mobile-bottom').is_visible()
            assert bottom==(width<768),f'{width}: mobile nav visibility incorrect'
            sidebar=page.locator('.sidebar').is_visible()
            assert sidebar==(width>=768),f'{width}: sidebar visibility incorrect'
            bg=page.locator('body').evaluate('(e)=>getComputedStyle(e).backgroundColor')
            assert bg=='rgb(247, 245, 240)',f'{width}: canvas token mismatch {bg}'
            report.append({'width':width,'overflow':overflow,'bottom_nav':bottom,'sidebar':sidebar,'clinical_action_disabled':True,'status':'PASS'})
            if width in [390,1440]:
                (root/'evidence/batches').mkdir(parents=True,exist_ok=True)
                page.screenshot(path=str(root/f'evidence/batches/P02-B0001-preview-{width}.png'),full_page=True)
            page.close()
        page=browser.new_page(viewport={'width':390,'height':900})
        page.set_content(html)
        page.add_style_tag(content='html{font-size:32px!important}body{font-size:2rem!important}')
        assert not page.evaluate('document.documentElement.scrollWidth > window.innerWidth'),'200% text: horizontal overflow'
        report.append({'width':390,'text_scale':'200% simulation','overflow':False,'status':'PASS'})
        page.close()
    finally:
        browser.close()
(root/'evidence/batches/P02-B0001-browser.json').write_text(json.dumps({'target':'static preview only','browser':'system Chromium','checks':report},indent=2)+'\n')
print(f'PASS: {len(report)} Chromium preview checks (9 widths plus 200% text simulation)')
