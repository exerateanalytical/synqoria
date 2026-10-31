package org.synqoria.consent;
import java.time.Instant;import java.util.*;
public class ConsentScopePolicyTest {
 static int n;static void ok(boolean b,String m){if(!b)throw new AssertionError(m);n++;}
 public static void main(String[] args){
  var t=UUID.randomUUID();var f=UUID.randomUUID();var p=UUID.randomUUID();var now=Instant.parse("2026-10-29T20:00:00Z");
  var g=new ConsentScopePolicy.Grant(t,f,p,UUID.randomUUID(),"treatment","patient:read",now.minusSeconds(10),now.plusSeconds(30),null,2,"ACTIVE");
  ok(ConsentScopePolicy.allows(g,t,f,p,"treatment","patient:read",now,true),"valid explicit grant");
  ok(!ConsentScopePolicy.allows(g,UUID.randomUUID(),f,p,"treatment","patient:read",now,true),"tenant mismatch");
  ok(!ConsentScopePolicy.allows(g,t,UUID.randomUUID(),p,"treatment","patient:read",now,true),"facility mismatch");
  ok(!ConsentScopePolicy.allows(g,t,f,UUID.randomUUID(),"treatment","patient:read",now,true),"patient mismatch");
  ok(!ConsentScopePolicy.allows(g,t,f,p,"marketing","patient:read",now,true),"purpose mismatch");
  ok(!ConsentScopePolicy.allows(g,t,f,p,"treatment","patient:write",now,true),"operation mismatch");
  ok(!ConsentScopePolicy.allows(g,t,f,p,"treatment","patient:read",now,false),"stale revocation check");
  ok(!ConsentScopePolicy.allows(g,t,f,p,"treatment","patient:read",now.plusSeconds(30),true),"expiry exclusive");
  ok(!ConsentScopePolicy.allows(g,t,f,p,"treatment","patient:read",now.minusSeconds(11),true),"not effective");
  var revoked=new ConsentScopePolicy.Grant(t,f,p,g.consentId(),"treatment","patient:read",g.effectiveAt(),g.expiresAt(),now,3,"REVOKED");
  ok(!ConsentScopePolicy.allows(revoked,t,f,p,"treatment","patient:read",now,true),"revoked denied");
  ok(!ConsentScopePolicy.allows(null,t,f,p,"treatment","patient:read",now,true),"missing grant denied");
  System.out.println("PASS: "+n+" explicit consent predicate assertions (no legal policy approval)");
 }
}
