import test from "node:test";
import assert from "node:assert/strict";
import {generateKeyPairSync} from "node:crypto";
import {loadTrustedStaffIdentityConfig,OidcDeploymentConfigError} from "../apps/web/lib/oidc-deployment-config.mjs";
const {publicKey}=generateKeyPairSync("rsa",{modulusLength:2048});
const issuer="https://identity.synqoria.org/realms/hospital",publicOrigin="https://staff.synqoria.org/";
const jwk={...publicKey.export({format:"jwk"}),kid:"key-2026",alg:"RS256",use:"sig"};
const env={
 SYNQORIA_OIDC_ISSUER:issuer,
 SYNQORIA_OIDC_AUTHORIZATION_ENDPOINT:issuer+"/protocol/openid-connect/auth",
 SYNQORIA_OIDC_TOKEN_ENDPOINT:issuer+"/protocol/openid-connect/token",
 SYNQORIA_OIDC_INTROSPECTION_ENDPOINT:issuer+"/protocol/openid-connect/token/introspect",
 SYNQORIA_PUBLIC_ORIGIN:publicOrigin,
 SYNQORIA_WORKFORCE_ORIGIN:"https://workforce.synqoria-clinical.org/",
 SYNQORIA_OIDC_CLIENT_ID:"synqoria-staff",
 SYNQORIA_OIDC_REDIRECT_URI:"https://staff.synqoria.org/api/auth/oidc/callback",
 SYNQORIA_OIDC_CLIENT_SECRET:"VaultSecret"+ "x".repeat(50),
 SYNQORIA_OIDC_FLOW_KEY:"a".repeat(64),
 SYNQORIA_STAFF_TOKEN_WRAP_KEY:"b".repeat(64),
 SYNQORIA_IDENTITY_AUDIT_PEPPER:"c".repeat(64),
 SYNQORIA_OIDC_API_AUDIENCE:"synqoria-clinical-core",
 SYNQORIA_EXPECTED_TENANT_ID:"11111111-1111-4111-8111-111111111111",
 SYNQORIA_EXPECTED_FACILITY_ID:"22222222-2222-4222-8222-222222222222",
 SYNQORIA_OIDC_JWKS_JSON:JSON.stringify({keys:[jwk]})
};
const denied=x=>assert.throws(()=>loadTrustedStaffIdentityConfig(x),OidcDeploymentConfigError);
test("P03-B0050 verified pinned issuer and tenant/facility routes only from trusted server config",()=>{
 const cfg=loadTrustedStaffIdentityConfig(env);
 assert.equal(cfg.publicOrigin,publicOrigin);
 assert.equal(cfg.oidc.issuer,issuer);
 assert.equal(cfg.oidc.clientId,env.SYNQORIA_OIDC_CLIENT_ID);
 assert.equal(cfg.oidc.redirectUri,env.SYNQORIA_OIDC_REDIRECT_URI);
 assert.equal(cfg.token.issuer,cfg.introspection.issuer);
 assert.equal(cfg.workforce.expectedTenantId,env.SYNQORIA_EXPECTED_TENANT_ID);
 assert.equal(cfg.workforce.expectedFacilityId,env.SYNQORIA_EXPECTED_FACILITY_ID);
 assert.equal(cfg.jwks.keys[0].kid,"key-2026");
 assert.equal(Object.isFrozen(cfg),true);
 assert.equal(Object.isFrozen(cfg.oidc),true);
 assert.equal(Object.isFrozen(cfg.jwks.keys[0]),true);
});
test("P03-B0050 all missing/short production secrets fail at startup, without fallback",()=>{
 for(const field of [
  "SYNQORIA_OIDC_ISSUER","SYNQORIA_OIDC_CLIENT_SECRET","SYNQORIA_OIDC_FLOW_KEY",
  "SYNQORIA_STAFF_TOKEN_WRAP_KEY","SYNQORIA_IDENTITY_AUDIT_PEPPER",
  "SYNQORIA_OIDC_JWKS_JSON","SYNQORIA_EXPECTED_TENANT_ID",
  "SYNQORIA_EXPECTED_FACILITY_ID"
 ]){
  denied({...env,[field]:undefined});
 }
 denied({...env,SYNQORIA_OIDC_FLOW_KEY:"x"});
 denied({...env,SYNQORIA_OIDC_CLIENT_SECRET:"x"});
 denied({...env,SYNQORIA_IDENTITY_AUDIT_PEPPER:"0000"});
});
test("P03-B0050 cross-realm IdP, untrusted localhost, redirect URI and workforce spoofing rejected",()=>{
 for(const overrides of [
  {SYNQORIA_OIDC_ISSUER:"http://identity.synqoria.org/realms/hospital"},
  {SYNQORIA_OIDC_TOKEN_ENDPOINT:"https://evil.example.org/token"},
  {SYNQORIA_OIDC_AUTHORIZATION_ENDPOINT:issuer.replace("hospital","evil")+"/protocol/openid-connect/auth"},
  {SYNQORIA_OIDC_INTROSPECTION_ENDPOINT:issuer+"/protocol/openid-connect/token/introspect?token=x"},
  {SYNQORIA_PUBLIC_ORIGIN:"https://staff.synqoria.org:444/"},
  {SYNQORIA_OIDC_REDIRECT_URI:"https://attacker.example/callback"},
  {SYNQORIA_WORKFORCE_ORIGIN:"https://localhost/"},
  {SYNQORIA_WORKFORCE_ORIGIN:"https://workforce.synqoria-clinical.org/tenant/evil"}
 ])denied({...env,...overrides});
});
test("P03-B0050 refuses private JWK fields, duplicate KIDs, unsigned and remote JWKS hints",()=>{
 for(const keys of [
  [{...jwk,d:"private-key-material"}],
  [{...jwk,jku:"https://attacker.example/key.json"}],
  [{...jwk,use:"enc"}],
  [{...jwk,key_ops:["sign"]}],
  [{...jwk,alg:"none"}],
  [{...jwk},{...jwk}],
  []
 ])denied({...env,SYNQORIA_OIDC_JWKS_JSON:JSON.stringify({keys})});
 denied({...env,SYNQORIA_OIDC_JWKS_JSON:"not json"});
});
