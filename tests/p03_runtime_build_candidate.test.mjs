import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const read=p=>readFileSync(new URL('../'+p,import.meta.url),'utf8');
test('P03-B0094 pins restricted Postgres driver as server runtime dependency',()=>{
 const web=JSON.parse(read('apps/web/package.json'));
 assert.equal(web.dependencies.pg,'8.23.1');
 assert.equal(web.dependencies.next,'15.5.9');
 assert.equal(web.devDependencies.pg,undefined);
 assert.equal(web.private,true);
});
test('P03-B0095 requires actual Next server module external to preserve native pg loading',()=>{
 assert.match(read('apps/web/next.config.ts'),/serverExternalPackages: \['pg'\]/);
 const inst=read('apps/web/instrumentation.ts');
 assert.match(inst,/NEXT_RUNTIME!=="nodejs"/);
 assert.match(inst,/await initializeProductionStaffIdentity\(\)/);
});
test('P03-B0096 candidate CI fetches exact audited npm lock and builds real app',()=>{
 const content=read('.github/workflows/p03-runtime-build-candidate.yml');
 assert.match(content,/work\/reproducible-npm-lock-candidate-2026-11-05/);
 assert.match(content,/6fa90a4aa72db790d86b7782d45ced40389b5f8987da65138fae7548b5f9eb2b/);
 assert.match(content,/npm install --package-lock-only/);
 assert.match(content,/npm ci --no-audit --no-fund/);
 assert.match(content,/npm run build --workspace @synqoria\/web/);
 assert.doesNotMatch(content,/git push|clinicalRelease: true/);
});
