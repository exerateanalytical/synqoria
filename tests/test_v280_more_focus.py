"""P02-B0093: execute the TS controller's emitted JavaScript in Chromium DOM."""
from pathlib import Path
from subprocess import run
import tempfile
from playwright.sync_api import sync_playwright
root=Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory() as temp:
    result=run(['tsc','--target','es2022','--module','es2022','--lib','es2022,dom','--skipLibCheck','--outDir',temp,
                str(root/'apps/web/lib/mobile-more-controller.ts')],capture_output=True,text=True)
    assert result.returncode==0,result.stderr
    js=(Path(temp)/'mobile-more-controller.js').read_text().replace('export function','function')
    js+='\nwindow.attachMobileMoreDisclosure=attachMobileMoreDisclosure;'
    shell=(root/'apps/web/components/ShellFrame.tsx').read_text()
    assert 'attachMobileMoreDisclosure(moreButton.current,morePanel.current' in shell
    with sync_playwright() as p:
        browser=p.chromium.launch(executable_path='/usr/bin/chromium',args=['--no-sandbox'])
        try:
            page=browser.new_page(viewport={'width':390,'height':800})
            page.on('pageerror', lambda err: print('JS ERROR',err))
            page.set_content('<button id="more">More</button><section id="panel" tabindex="-1"><button id="first">EN</button><button id="last">FR</button></section><button id="outside">Outside</button>')
            page.add_script_tag(content=js)
            page.evaluate('''() => {window.closeCalls=[];window.detach=window.attachMobileMoreDisclosure(document.getElementById('more'),document.getElementById('panel'),restore=>{window.closeCalls.push(restore);if(restore)document.getElementById('more').focus()});}''')
            page.wait_for_timeout(80)
            assert page.evaluate('document.activeElement.id')=='first'
            page.keyboard.press('Shift+Tab')
            assert page.evaluate('document.activeElement.id')=='last'
            page.keyboard.press('Tab')
            assert page.evaluate('document.activeElement.id')=='first'
            page.keyboard.press('Escape')
            assert page.evaluate('document.activeElement.id')=='more'
            assert page.evaluate('window.closeCalls')==[True]
            page.evaluate('window.detach()')
            page.evaluate('''() => {window.closeCalls=[];window.detach=window.attachMobileMoreDisclosure(document.getElementById("more"),document.getElementById("panel"),v=>window.closeCalls.push(v));}''')
            page.evaluate("document.addEventListener('pointerdown',e=>{window.debug={node:e.target instanceof Node, inPanel:document.getElementById('panel').contains(e.target), inButton:document.getElementById('more').contains(e.target)}});document.getElementById('outside').dispatchEvent(new PointerEvent('pointerdown',{bubbles:true}))")
            assert page.evaluate('window.closeCalls')==[False]
            page.evaluate('window.detach()')
            page.evaluate('''() => {window.closeCalls=[];window.detach=window.attachMobileMoreDisclosure(document.getElementById("more"),document.getElementById("panel"),v=>window.closeCalls.push(v));}''')
            page.set_viewport_size({'width':1024,'height':800})
            page.wait_for_timeout(80)
            assert page.evaluate('window.closeCalls')==[False]
            page.evaluate('window.detach()')
            assert page.evaluate('window.closeCalls')==[False]
        finally:browser.close()
print('PASS P02-B0093: actual compiled TS controller Chromium focus loop, Escape, outside, resize, cleanup')
