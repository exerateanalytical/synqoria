package org.synqoria.auditcore;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.*;
import static org.synqoria.auditcore.Rules.*;

/** M34 is not an identity, clinical-data or jurisdictional retention authority. */
@SpringBootApplication
public class AuditPlatform{
 public static void main(String[] args){SpringApplication.run(AuditPlatform.class,args);}
 @Bean Clock clock(){return Clock.systemUTC();}
 public static Actor actor(Jwt jwt,Instant now){
  if(jwt==null)throw new Problem("identity_denied");
  try{
   UUID actor=UUID.fromString(jwt.getSubject());
   UUID cell=UUID.fromString(jwt.getClaimAsString("synqoria_cell"));
   UUID facility=UUID.fromString(jwt.getClaimAsString("synqoria_facility"));
   List<String> grants=jwt.getClaimAsStringList("synqoria_permissions");
   if(grants==null||grants.size()>64||grants.isEmpty())throw new Problem("grant_denied");
   Set<String> distinct=new HashSet<>(grants);
   if(distinct.size()!=grants.size()||!PERMISSIONS.containsAll(distinct))throw new Problem("grant_denied");
   Number expires=jwt.getClaim("synqoria_grant_expires");
   long n=now.getEpochSecond();
   if(expires==null||expires.longValue()<=n||expires.longValue()>n+900)
    throw new Problem("grant_expired");
   return new Actor(actor,cell,facility,distinct);
  }catch(IllegalArgumentException|NullPointerException e){throw new Problem("identity_denied");}
 }
 @Configuration(proxyBeanMethods=false) @EnableWebSecurity
 public static class Security{
  @Bean SecurityFilterChain chain(HttpSecurity http)throws Exception{
   http.csrf(AbstractHttpConfigurer::disable)
    .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .authorizeHttpRequests(a->a.anyRequest().authenticated())
    .oauth2ResourceServer(o->o.jwt(Customizer.withDefaults()));
   return http.build();
  }
  @Bean JwtDecoder jwtDecoder(@Value("${synqoria.audit.issuer}")String issuer,
          @Value("${synqoria.audit.audience}")String audience){
   if(issuer==null||!issuer.startsWith("https://")||issuer.length()>512||
      audience==null||audience.isBlank()||audience.length()>180)
    throw new IllegalStateException("audit_oidc_not_configured");
   JwtDecoder decoder=JwtDecoders.fromIssuerLocation(issuer);
   if(!(decoder instanceof NimbusJwtDecoder d))
    throw new IllegalStateException("audit_oidc_decoder_missing");
   OAuth2TokenValidator<Jwt> audienceGate=jwt->jwt.getAudience()!=null&&jwt.getAudience().contains(audience)
    ?OAuth2TokenValidatorResult.success()
    :OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","invalid_audience",null));
   d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),audienceGate));
   return d;
  }
 }
 @RestControllerAdvice
 public static class Errors{
  @ExceptionHandler(Problem.class)
  ResponseEntity<Map<String,String>> problem(Problem e){
   HttpStatus status=switch(e.code()){
    case "identity_denied","grant_expired"->HttpStatus.UNAUTHORIZED;
    case "grant_denied","permission_denied","independent_checker_required"->HttpStatus.FORBIDDEN;
    case "not_found"->HttpStatus.NOT_FOUND;
    case "idempotency_collision","stale_version","invalid_transition","stale_source_sequence",
       "audit_attestation_required","audit_chain_invalid","active_legal_hold",
       "export_expired","export_range_incomplete"->HttpStatus.CONFLICT;
    case "db_role_denied"->HttpStatus.SERVICE_UNAVAILABLE;
    default->HttpStatus.UNPROCESSABLE_ENTITY;
   };
   return ResponseEntity.status(status).header("Cache-Control","no-store").body(Map.of("error",e.code()));
  }
  @ExceptionHandler({IllegalArgumentException.class,HttpMessageNotReadableException.class})
  ResponseEntity<Map<String,String>> malformed(Exception e){
   return ResponseEntity.badRequest().header("Cache-Control","no-store").body(Map.of("error","malformed_request"));
  }
  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String,String>> unavailable(DataAccessException e){
   boolean conflict=e instanceof DataIntegrityViolationException;
   return ResponseEntity.status(conflict?HttpStatus.CONFLICT:HttpStatus.SERVICE_UNAVAILABLE)
    .header("Cache-Control","no-store").body(Map.of("error",conflict?"integrity_violation":"audit_database_unavailable"));
  }
 }
}
