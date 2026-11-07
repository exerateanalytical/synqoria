package org.synqoria.scheduling;

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
public class SchedulingApplication {
 public static void main(String[] a){SpringApplication.run(SchedulingApplication.class,a);}
 @Bean Clock clock(){return Clock.systemUTC();}
 public record Actor(UUID id,UUID cell,UUID facility,Set<String> grants){
  public Actor{Objects.requireNonNull(id);Objects.requireNonNull(cell);Objects.requireNonNull(facility);grants=Set.copyOf(grants);}
  public void require(String grant){if(!grants.contains(grant))throw new Denied("permission_denied");}
 }
 public static final Set<String> PERMISSIONS=Set.of("m02:read","m02:resource_write","m02:slot_publish",
  "m02:book","m02:confirm","m02:arrive","m02:fulfil","m02:cancel",
  "m02:no_show","m02:waitlist","m02:offer","m02:waitlist_accept");
 public static final class Denied extends RuntimeException{
  public final String code;public Denied(String code){super(code);this.code=code;}
 }
 public static Actor actor(Jwt jwt,Instant now){
  try{
   if(jwt==null)throw new Denied("identity_denied");
   var id=UUID.fromString(jwt.getSubject());
   var cell=UUID.fromString(jwt.getClaimAsString("synqoria_cell"));
   var facility=UUID.fromString(jwt.getClaimAsString("synqoria_facility"));
   List<String> list=jwt.getClaimAsStringList("synqoria_permissions");
   if(list==null||list.isEmpty()||list.size()>64||new HashSet<>(list).size()!=list.size()
      ||!PERMISSIONS.containsAll(list))throw new Denied("grant_denied");
   Number exp=jwt.getClaim("synqoria_grant_expires");
   if(exp==null||exp.longValue()<=now.getEpochSecond()||exp.longValue()>now.getEpochSecond()+900)
    throw new Denied("grant_expired");
   return new Actor(id,cell,facility,Set.copyOf(list));
  }catch(IllegalArgumentException|NullPointerException e){throw new Denied("identity_denied");}
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
  @Bean JwtDecoder decoder(@Value("${synqoria.scheduling.issuer}")String issuer,
                           @Value("${synqoria.scheduling.audience}")String audience){
   if(issuer==null||!issuer.startsWith("https://")||issuer.length()>512||audience==null||audience.isBlank())
    throw new IllegalStateException("m02_oidc_not_configured");
   JwtDecoder x=JwtDecoders.fromIssuerLocation(issuer);
   if(!(x instanceof NimbusJwtDecoder d))throw new IllegalStateException("m02_oidc_decoder_required");
   OAuth2TokenValidator<Jwt> aud=jwt->jwt.getAudience()!=null&&jwt.getAudience().contains(audience)
    ?OAuth2TokenValidatorResult.success():
     OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","wrong_audience",null));
   d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),aud));
   return d;
  }
 }
 @RestControllerAdvice
 public static class Errors{
  @ExceptionHandler(Denied.class)
  ResponseEntity<Map<String,String>> denied(Denied e){
   int status=switch(e.code){
    case "identity_denied","grant_expired"->401;
    case "permission_denied","grant_denied"->403;
    case "not_found"->404;
    case "stale_version","booking_conflict","invalid_transition","evidence_unavailable",
     "idempotency_collision","slot_not_open","resource_not_active"->409;
    default->422;
   };
   return ResponseEntity.status(status).header("Cache-Control","no-store").body(Map.of("error",e.code));
  }
  @ExceptionHandler({HttpMessageNotReadableException.class,IllegalArgumentException.class})
  ResponseEntity<Map<String,String>> malformed(Exception e){
   return ResponseEntity.badRequest().body(Map.of("error","malformed_request"));
  }
  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String,String>> db(DataAccessException e){
   int s=e instanceof DataIntegrityViolationException?409:503;
   return ResponseEntity.status(s).header("Cache-Control","no-store")
    .body(Map.of("error",s==409?"database_conflict":"database_unavailable"));
  }
 }
}
