package org.synqoria.privacy;

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
import static org.synqoria.privacy.Rules.*;

@SpringBootApplication
public class PrivacyPlatform {
 public static void main(String[] args){SpringApplication.run(PrivacyPlatform.class,args);}
 @Bean Clock clock(){return Clock.systemUTC();}
 public static Actor actor(Jwt jwt,Instant now){
  if(jwt==null)throw new Problem("identity_denied");
  try {
   UUID id=UUID.fromString(jwt.getSubject());
   UUID cell=UUID.fromString(jwt.getClaimAsString("synqoria_cell"));
   UUID facility=UUID.fromString(jwt.getClaimAsString("synqoria_facility"));
   List<String> grants=jwt.getClaimAsStringList("synqoria_permissions");
   if(grants==null||grants.size()>64||grants.isEmpty())throw new Problem("grant_denied");
   Set<String> set=new HashSet<>(grants);
   if(set.size()!=grants.size()||!PERMISSIONS.containsAll(set))throw new Problem("grant_denied");
   Number until=jwt.getClaim("synqoria_grant_expires");
   long sec=now.getEpochSecond();
   if(until==null||until.longValue()<=sec||until.longValue()>sec+900)
    throw new Problem("grant_expired");
   return new Actor(id,cell,facility,set);
  }catch(IllegalArgumentException|NullPointerException e){throw new Problem("identity_denied");}
 }
 @Configuration(proxyBeanMethods=false)
 @EnableWebSecurity
 public static class Security {
  @Bean SecurityFilterChain privacyFilter(HttpSecurity http)throws Exception{
   http.csrf(AbstractHttpConfigurer::disable)
    .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .authorizeHttpRequests(a->a.anyRequest().authenticated())
    .oauth2ResourceServer(o->o.jwt(Customizer.withDefaults()));
   return http.build();
  }
  @Bean JwtDecoder decoder(@Value("${synqoria.privacy.issuer}")String issuer,
                            @Value("${synqoria.privacy.audience}")String audience){
   if(!issuer.startsWith("https://")||issuer.length()>512||audience==null||
      audience.isBlank()||audience.length()>180)throw new IllegalStateException("privacy_oidc_not_configured");
   var decoder=JwtDecoders.fromIssuerLocation(issuer);
   if(!(decoder instanceof NimbusJwtDecoder d))throw new IllegalStateException("privacy_jwt_decoder_missing");
   OAuth2TokenValidator<Jwt> aud=jwt->jwt.getAudience()!=null&&jwt.getAudience().contains(audience)
    ?OAuth2TokenValidatorResult.success()
    :OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","invalid_audience",null));
   d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),aud));
   return d;
  }
 }
 @RestControllerAdvice
 public static class Errors {
  @ExceptionHandler(Problem.class)
  ResponseEntity<Map<String,String>> problem(Problem x){
   HttpStatus status=switch(x.code()){
    case "identity_denied","grant_expired"->HttpStatus.UNAUTHORIZED;
    case "permission_denied","grant_denied","independent_checker_required"->HttpStatus.FORBIDDEN;
    case "not_found"->HttpStatus.NOT_FOUND;
    case "stale_version","idempotency_collision","invalid_transition","consent_not_active",
      "authority_evidence_required","privacy_proof_revoked"->HttpStatus.CONFLICT;
    case "db_role_denied"->HttpStatus.SERVICE_UNAVAILABLE;
    default->HttpStatus.UNPROCESSABLE_ENTITY;
   };
   return ResponseEntity.status(status).header("Cache-Control","no-store").body(Map.of("error",x.code()));
  }
  @ExceptionHandler({HttpMessageNotReadableException.class,IllegalArgumentException.class})
  ResponseEntity<Map<String,String>> malformed(Exception x){
   return ResponseEntity.badRequest().header("Cache-Control","no-store").body(Map.of("error","malformed_request"));
  }
  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String,String>> unavailable(DataAccessException x){
   HttpStatus status=x instanceof DataIntegrityViolationException?HttpStatus.CONFLICT:HttpStatus.SERVICE_UNAVAILABLE;
   return ResponseEntity.status(status).header("Cache-Control","no-store")
    .body(Map.of("error",status==HttpStatus.CONFLICT?"integrity_violation":"privacy_database_unavailable"));
  }
 }
}
