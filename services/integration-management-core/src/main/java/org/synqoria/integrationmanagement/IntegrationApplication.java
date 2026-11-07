package org.synqoria.integrationmanagement;

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

@SpringBootApplication
public class IntegrationApplication{
 public static void main(String[] args){SpringApplication.run(IntegrationApplication.class,args);}
 @Bean Clock clock(){return Clock.systemUTC();}
 public record Actor(UUID id,UUID cell,UUID facility,Set<String> permissions){
  public Actor{
   Objects.requireNonNull(id);Objects.requireNonNull(cell);Objects.requireNonNull(facility);
   permissions=Set.copyOf(Objects.requireNonNull(permissions));
  }
  public void require(String permission){
   if(!permissions.contains(permission))throw new Problem("permission_denied");
  }
 }
 public static final Set<String> PERMISSIONS=Set.of("m35:read","m35:partner_create","m35:partner_verify",
 "m35:partner_enable","m35:partner_suspend","m35:api_create","m35:api_publish",
 "m35:app_request","m35:app_review","m35:mapping_create","m35:mapping_validate",
 "m35:mapping_approve","m35:message_ingest","m35:message_review","m35:replay_request",
 "m35:replay_review");
 public static final class Problem extends RuntimeException{
  public final String code;public Problem(String c){super(c);code=c;}
 }
 public static Actor actor(Jwt jwt,Instant now){
  try{
   if(jwt==null)throw new Problem("identity_denied");
   UUID id=UUID.fromString(jwt.getSubject()),cell=UUID.fromString(jwt.getClaimAsString("synqoria_cell")),
    facility=UUID.fromString(jwt.getClaimAsString("synqoria_facility"));
   List<String> ps=jwt.getClaimAsStringList("synqoria_permissions");
   if(ps==null||ps.isEmpty()||ps.size()>64||new HashSet<>(ps).size()!=ps.size()||
     !PERMISSIONS.containsAll(ps))throw new Problem("grant_denied");
   Number exp=jwt.getClaim("synqoria_grant_expires");
   if(exp==null||exp.longValue()<=now.getEpochSecond()||exp.longValue()>now.getEpochSecond()+900)
    throw new Problem("grant_expired");
   return new Actor(id,cell,facility,Set.copyOf(ps));
  }catch(IllegalArgumentException|NullPointerException e){throw new Problem("identity_denied");}
 }
 @Configuration(proxyBeanMethods=false) @EnableWebSecurity
 public static class Security{
  @Bean SecurityFilterChain filter(HttpSecurity http)throws Exception{
   http.csrf(AbstractHttpConfigurer::disable)
    .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .authorizeHttpRequests(a->a.anyRequest().authenticated())
    .oauth2ResourceServer(o->o.jwt(Customizer.withDefaults()));
   return http.build();
  }
  @Bean JwtDecoder decoder(@Value("${synqoria.integrations.issuer}")String issuer,
    @Value("${synqoria.integrations.audience}")String audience){
   if(issuer==null||!issuer.startsWith("https://")||issuer.length()>512||
     audience==null||audience.isBlank())throw new IllegalStateException("m35_oidc_not_configured");
   JwtDecoder candidate=JwtDecoders.fromIssuerLocation(issuer);
   if(!(candidate instanceof NimbusJwtDecoder d))throw new IllegalStateException("m35_oidc_decoder_required");
   OAuth2TokenValidator<Jwt> aud=jwt->jwt.getAudience()!=null&&jwt.getAudience().contains(audience)
    ?OAuth2TokenValidatorResult.success():OAuth2TokenValidatorResult.failure(
     new OAuth2Error("invalid_token","wrong_audience",null));
   d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),aud));
   return d;
  }
 }
 @RestControllerAdvice
 public static class Errors{
  @ExceptionHandler(Problem.class)
  ResponseEntity<Map<String,String>> error(Problem x){
   int status=switch(x.code){
    case "identity_denied","grant_expired"->401;
    case "grant_denied","permission_denied","independent_checker_required"->403;
    case "not_found"->404;
    case "stale_version","invalid_transition","proof_unavailable","partner_not_enabled",
      "api_not_published","idempotency_collision","message_not_quarantined"->409;
    case "db_role_denied"->503;
    default->422;
   };
   return ResponseEntity.status(status).header("Cache-Control","no-store").body(Map.of("error",x.code));
  }
  @ExceptionHandler({IllegalArgumentException.class,HttpMessageNotReadableException.class})
  ResponseEntity<Map<String,String>> malformed(Exception e){
   return ResponseEntity.badRequest().body(Map.of("error","malformed_request"));
  }
  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String,String>> db(DataAccessException e){
   int s=e instanceof DataIntegrityViolationException?409:503;
   return ResponseEntity.status(s).header("Cache-Control","no-store")
    .body(Map.of("error",s==409?"integrity_violation":"integration_database_unavailable"));
  }
 }
}
