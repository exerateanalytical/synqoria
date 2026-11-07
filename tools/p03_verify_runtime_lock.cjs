'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const read=path=>JSON.parse(fs.readFileSync(path,'utf8'));
const web=read('apps/web/package.json'),root=read('package.json'),lock=read('package-lock.json');
assert.equal(web.dependencies.pg,'8.23.1');
assert.equal(lock.lockfileVersion,3);
assert.equal(lock.packages[''].version,root.version);
assert.equal(lock.packages['apps/web'].dependencies.pg,'8.23.1');
const pg=lock.packages['node_modules/pg'];
assert.ok(pg,'missing pg runtime resolution');
assert.equal(pg.version,'8.23.1');
assert.match(pg.resolved||'',/^https:\/\/registry\.npmjs\.org\/pg\/-\/pg-8\.23\.1\.tgz$/);
assert.match(pg.integrity||'',/^sha512-[A-Za-z0-9+/]+={0,2}$/);
assert.notEqual(pg.dev,true,'pg must not be dev-only');
for(const dependency of ['pg-connection-string','pg-pool','pg-protocol','pg-types']){
 const row=lock.packages['node_modules/'+dependency];
 assert.ok(row,'missing production dependency '+dependency);
 assert.match(row.integrity||'',/^sha512-[A-Za-z0-9+/]+={0,2}$/);
}
console.log('verified exact pg 8.23.1 and reconciled lock graph');
