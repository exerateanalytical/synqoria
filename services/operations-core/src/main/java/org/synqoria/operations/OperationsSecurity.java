package org.synqoria.operations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;

/** Never accept a caller-provided facility header, arbitrary JWT issuer or bearer payload. */
@Configuration(proxyBeanMethods=false)
@EnableWebSecurity
public class OperationsSecurity {
 @Bean
 SecurityFilterChain operationsChain(HttpSecurity http)throws Exception{
  http.csrf(AbstractHttpConfigurer::disable)
      .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(a->a.anyRequest().authenticated())
      .oauth2ResourceServer(o->o.jwt(Customizer.withDefaults()));
  return http.build();
 }
 @Bean
 JwtDecoder operationsJwtDecoder(@Value("${synqoria.ops.issuer}")String issuer,
                                 @Value("${synqoria.ops.audience}")String audience){
  if(!issuer.startsWith("https://")||issuer.length()>512||audience.isBlank()||audience.length()>180)
   throw new IllegalStateException("operations_oidc_not_configured");
  JwtDecoder delegate=JwtDecoders.fromIssuerLocation(issuer);
  if(!(delegate instanceof NimbusJwtDecoder decoder))
   throw new IllegalStateException("operations_oidc_decoder_unavailable");
  OAuth2TokenValidator<Jwt> audienceCheck=jwt->jwt.getAudience()!=null&&jwt.getAudience().contains(audience)
   ?OAuth2TokenValidatorResult.success()
   :OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","invalid_audience",null));
  decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
   JwtValidators.createDefaultWithIssuer(issuer),audienceCheck));
  return decoder;
 }
}
