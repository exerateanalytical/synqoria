package org.synqoria.referrals;
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
public class ReferralApplication{
 public static void main(String[] args){SpringApplication.run(ReferralApplication.class,args);}
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
 public static final Set<String> PERMISSIONS=Set.of("m16:read","m16:create","m16:authorize",
  "m16:send","m16:receiving_review","m16:transfer","m16:attend","m16:close","m16:cancel");
 public static final class Problem extends RuntimeException{
  public final String code;
  public Problem(String code){super(code);this.code=code;}
 }
 public static Actor actor(Jwt jwt,Instant now){
  try{
   if(jwt==null)throw new Problem("identity_denied");
   UUID id=UUID.fromString(jwt.getSubject());
   UUID cell=UUID.fromString(jwt.getClaimAsString("synqoria_cell"));
   UUID facility=UUID.fromString(jwt.getClaimAsString("synqoria_facility"));
   List<String> perms=jwt.getClaimAsStringList("synqoria_permissions");
   if(perms==null||perms.size()<1||perms.size()>64||new HashSet<>(perms).size()!=perms.size()
     ||!PERMISSIONS.containsAll(perms))throw new Problem("grant_denied");
   Number exp=jwt.getClaim("synqoria_grant_expires");
   if(exp==null||exp.longValue()<=now.getEpochSecond()||exp.longValue()>now.getEpochSecond()+900)
    throw new Problem("grant_expired");
   return new Actor(id,cell,facility,Set.copyOf(perms));
  }catch(IllegalArgumentException|NullPointerException e){throw new Problem("identity_denied");}
 }
 @Configuration(proxyBeanMethods=false) @EnableWebSecurity
 public static class Security{
  @Bean SecurityFilterChain chain(HttpSecurity http)throws Exception{
   http.csrf(AbstractHttpConfigurer::disable).sessionManagement(s->
    s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .authorizeHttpRequests(a->a.anyRequest().authenticated())
    .oauth2ResourceServer(o->o.jwt(Customizer.withDefaults()));
   return http.build();
  }
  @Bean JwtDecoder decoder(@Value("${synqoria.referrals.issuer}")String issuer,
      @Value("${synqoria.referrals.audience}")String audience){
   if(issuer==null||!issuer.startsWith("https://")||issuer.length()>512||
      audience==null||audience.isBlank())throw new IllegalStateException("m16_oidc_not_configured");
   JwtDecoder candidate=JwtDecoders.fromIssuerLocation(issuer);
   if(!(candidate instanceof NimbusJwtDecoder d))throw new IllegalStateException("m16_oidc_decoder_required");
   OAuth2TokenValidator<Jwt> aud=jwt->jwt.getAudience()!=null&&jwt.getAudience().contains(audience)
    ?OAuth2TokenValidatorResult.success():OAuth2TokenValidatorResult.failure(
     new OAuth2Error("invalid_token","invalid_audience",null));
   d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),aud));
   return d;
  }
 }
 @RestControllerAdvice
 public static class Errors{
  @ExceptionHandler(Problem.class)
  ResponseEntity<Map<String,String>> problem(Problem e){
   int status=switch(e.code){
    case "identity_denied","grant_expired"->401;
    case "grant_denied","permission_denied","independent_checker_required"->403;
    case "not_found"->404;
    case "invalid_transition","stale_version","evidence_unavailable","idempotency_collision",
         "proof_wrong_scope"->409;
    case "db_role_denied"->503;
    default->422;
   };
   return ResponseEntity.status(status).header("Cache-Control","no-store").body(Map.of("error",e.code));
  }
  @ExceptionHandler({IllegalArgumentException.class,HttpMessageNotReadableException.class})
  ResponseEntity<Map<String,String>> malformed(Exception e){
   return ResponseEntity.badRequest().body(Map.of("error","malformed_request"));
  }
  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String,String>> db(DataAccessException e){
   int status=e instanceof DataIntegrityViolationException?409:503;
   return ResponseEntity.status(status).header("Cache-Control","no-store").body(
    Map.of("error",status==409?"integrity_violation":"referral_database_unavailable"));
  }
 }
}
