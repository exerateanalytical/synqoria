package org.synqoria.nutrition;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.synqoria.nutrition.domain.Domain.*;

/** API-layer admission and error-contract tests (SYNTHETIC tokens, no network, no database). */
class ApiAdmissionTest {
 static final Instant NOW=Instant.parse("2026-11-07T06:00:00Z");

 static Jwt jwt(Map<String,Object> overrides){
  Map<String,Object> claims=new HashMap<>(Map.of("sub",UUID.randomUUID().toString(),"synqoria_cell",UUID.randomUUID().toString(),
   "synqoria_facility",UUID.randomUUID().toString(),"synqoria_permissions",List.of("m26:worklist_read","m26:order_read"),
   "synqoria_grant_expires",NOW.plusSeconds(600).getEpochSecond()));
  claims.putAll(overrides);
  return new Jwt("token",NOW.minusSeconds(10),NOW.plusSeconds(600),Map.of("alg","RS256"),claims);
 }
 static String code(Jwt j){try{Platform.actorFrom(j,NOW);return "OK";}catch(Violation v){return v.code();}}

 @Test void validTokenYieldsScopedActor(){
  Actor a=Platform.actorFrom(jwt(Map.of()),NOW);
  assertTrue(a.grants().contains("m26:worklist_read"));
  a.require("m26:order_read");
  assertEquals("permission_denied",assertThrows(Violation.class,()->a.require("m26:order_prescribe")).code());
 }
 @Test void missingOrMalformedIdentityAndScopeClaimsAreDenied(){
  assertEquals("identity_denied",code(null));
  assertEquals("identity_denied",code(jwt(Map.of("sub","not-a-uuid"))));
  assertEquals("identity_denied",code(jwt(Map.of("synqoria_cell","x"))));
  Map<String,Object> noFacility=new HashMap<>(jwt(Map.of()).getClaims());noFacility.remove("synqoria_facility");
  assertEquals("identity_denied",code(new Jwt("t",NOW.minusSeconds(1),NOW.plusSeconds(60),Map.of("alg","RS256"),noFacility)));
 }
 @Test void grantsAreClosedSetUniqueBoundedAndShortLived(){
  assertEquals("grant_denied",code(jwt(Map.of("synqoria_permissions",List.of("m26:superuser")))));
  assertEquals("grant_denied",code(jwt(Map.of("synqoria_permissions",List.of("m21:anything")))));
  assertEquals("grant_denied",code(jwt(Map.of("synqoria_permissions",List.of("m26:order_read","m26:order_read")))));
  assertEquals("grant_denied",code(jwt(Map.of("synqoria_permissions",List.of()))));
  assertEquals("grant_expired",code(jwt(Map.of("synqoria_grant_expires",NOW.minusSeconds(1).getEpochSecond()))));
  assertEquals("grant_expired",code(jwt(Map.of("synqoria_grant_expires",NOW.plusSeconds(901).getEpochSecond()))));
 }
 @Test void errorCodesMapToStableHttpStatuses(){
  assertEquals(HttpStatus.FORBIDDEN,Platform.Errors.status("permission_denied"));
  assertEquals(HttpStatus.FORBIDDEN,Platform.Errors.status("approver_must_be_independent"));
  assertEquals(HttpStatus.UNAUTHORIZED,Platform.Errors.status("identity_denied"));
  assertEquals(HttpStatus.NOT_FOUND,Platform.Errors.status("not_found"));
  assertEquals(HttpStatus.CONFLICT,Platform.Errors.status("stale_version"));
  assertEquals(HttpStatus.CONFLICT,Platform.Errors.status("idempotency_collision"));
  assertEquals(HttpStatus.CONFLICT,Platform.Errors.status("diet_safety_block"));
  assertEquals(HttpStatus.UNPROCESSABLE_ENTITY,Platform.Errors.status("diet_contradictory"));
  var resp=new Platform.Errors().violation(new Violation("diet_contradictory",List.of("texture_vs_diet")));
  assertEquals(422,resp.getStatusCode().value());
  assertEquals("no-store",resp.getHeaders().getFirst("Cache-Control"));
  assertEquals("diet_contradictory",resp.getBody().get("error"));
  var integrity=new Platform.Errors().integrity(new DataIntegrityViolationException("secret table detail"));
  assertEquals(409,integrity.getStatusCode().value());
  assertFalse(integrity.getBody().toString().contains("secret"));
 }
}
