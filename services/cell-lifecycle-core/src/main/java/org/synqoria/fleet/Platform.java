package org.synqoria.fleet;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.synqoria.fleet.domain.Domain.*;

/** Separate hospital-cell M39 management-plane service. It never writes clinical, identity or allergy authorities. */
@SpringBootApplication
public class Platform {
 public static void main(String[] args){SpringApplication.run(Platform.class,args);}

 @Bean Clock clock(){return Clock.systemUTC();}

 /** Verified JWT (issuer, audience, signature validated by Spring) to scoped actor. */
 public static Actor actorFrom(Jwt jwt,Instant now){
  if(jwt==null)throw new Violation("identity_denied");
  try{
   UUID id=UUID.fromString(jwt.getSubject());
   UUID cell=UUID.fromString(jwt.getClaimAsString("synqoria_cell"));
   UUID facility=UUID.fromString(jwt.getClaimAsString("synqoria_facility"));
   List<String> grants=jwt.getClaimAsStringList("synqoria_permissions");
   if(grants==null||grants.isEmpty()||grants.size()>64)throw new Violation("grant_denied");
   Set<String> distinct=new HashSet<>(grants);
   if(distinct.size()!=grants.size()||!Perm.ALL.containsAll(distinct))throw new Violation("grant_denied");
   Number exp=jwt.getClaim("synqoria_grant_expires");
   long t=now.getEpochSecond();
   if(exp==null||exp.longValue()<=t||exp.longValue()>t+900)throw new Violation("grant_expired");
   List<String> scope=jwt.getClaimAsStringList("synqoria_tenant_scope");     // absent = platform-wide operator; present = only these tenants
   Set<UUID> tenants=new HashSet<>();
   if(scope!=null){if(scope.isEmpty()||scope.size()>200)throw new Violation("grant_denied");for(String ts:scope)tenants.add(UUID.fromString(ts));}
   return new Actor(id,cell,facility,distinct,tenants);
  }catch(IllegalArgumentException|NullPointerException e){throw new Violation("identity_denied");}
 }

 @Configuration(proxyBeanMethods=false)
 @EnableWebSecurity
 public static class Security {
  @Bean SecurityFilterChain hrChain(HttpSecurity http)throws Exception{
   http.csrf(AbstractHttpConfigurer::disable)
    .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .authorizeHttpRequests(a->a.requestMatchers("/actuator/health/**").permitAll().anyRequest().authenticated())
    .oauth2ResourceServer(o->o.jwt(Customizer.withDefaults()));
   return http.build();
  }
  @Bean JwtDecoder hrJwtDecoder(@Value("${synqoria.fleet.issuer}")String issuer,
    @Value("${synqoria.fleet.audience}")String audience){
   if(!issuer.startsWith("https://")||issuer.length()>512||audience.isBlank()||audience.length()>180)
    throw new IllegalStateException("fleet_oidc_not_configured");
   JwtDecoder delegate=JwtDecoders.fromIssuerLocation(issuer);
   if(!(delegate instanceof NimbusJwtDecoder d))throw new IllegalStateException("fleet_oidc_decoder_unavailable");
   OAuth2TokenValidator<Jwt> aud=jwt->jwt.getAudience()!=null&&jwt.getAudience().contains(audience)
    ?OAuth2TokenValidatorResult.success()
    :OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","invalid_audience",null));
   d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),aud));
   return d;
  }
 }

 @RestControllerAdvice
 public static class Errors {
  static HttpStatus status(String code){
   return switch(code){
    case "not_found"->HttpStatus.NOT_FOUND;
    case "stale_version","idempotency_collision","duplicate_record","invalid_transition","stale_placement_epoch","not_qualified","incompatible_release","evidence_mismatch","verification_failed","irreversible","rollout_stopped","integrity_violation","lag_too_high","route_in_migration","stage_not_open","stage_incomplete","health_gate_failed","qualification_incomplete","migration_in_progress","residency_violation"->HttpStatus.CONFLICT;
    case "route_unavailable"->HttpStatus.SERVICE_UNAVAILABLE;
    case "database_role_denied"->HttpStatus.SERVICE_UNAVAILABLE;
    case "identity_denied","grant_expired"->HttpStatus.UNAUTHORIZED;
    case "permission_denied","grant_denied","approver_must_be_independent","completer_must_be_independent","tenant_scope_denied"->HttpStatus.FORBIDDEN;
    default->HttpStatus.UNPROCESSABLE_ENTITY;
   };
  }
  @ExceptionHandler(Violation.class)
  public ResponseEntity<Map<String,Object>> violation(Violation v){
   return ResponseEntity.status(status(v.code())).header("Cache-Control","no-store")
    .body(Map.of("error",v.code(),"details",v.details()));
  }
  @ExceptionHandler(DuplicateKeyException.class)
  public ResponseEntity<Map<String,Object>> duplicate(DuplicateKeyException e){
   return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error","duplicate_record","details",List.of()));
  }
  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  public ResponseEntity<Map<String,Object>> integrity(org.springframework.dao.DataIntegrityViolationException e){
   return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error","integrity_violation","details",List.of()));
  }
  @ExceptionHandler({HttpMessageNotReadableException.class,IllegalArgumentException.class})
  public ResponseEntity<Map<String,Object>> malformed(Exception e){
   return ResponseEntity.badRequest().body(Map.of("error","malformed_request","details",List.of()));
  }
  @ExceptionHandler(DataAccessException.class)
  public ResponseEntity<Map<String,Object>> unavailable(DataAccessException e){
   return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error","fleet_unavailable","details",List.of()));
  }
 }
}
