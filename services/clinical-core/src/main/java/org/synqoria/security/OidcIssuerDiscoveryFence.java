package org.synqoria.security;
import java.net.URI;import java.util.*;
/** P03-B0029. Validate externally fetched discovery metadata against configured trust pins; never fetch URLs here. */
public final class OidcIssuerDiscoveryFence {
 private OidcIssuerDiscoveryFence(){}
 public record Metadata(String issuer,String jwksUri,String authorizationEndpoint,String tokenEndpoint){}
 public static boolean valid(OidcTrustPins pins,Metadata m){
  if(pins==null||m==null||!pins.issuer().equals(m.issuer()))return false;
  try{
   URI origin=URI.create(pins.issuer());
   return endpoint(origin,m.jwksUri(),true)&&endpoint(origin,m.authorizationEndpoint(),false)&&endpoint(origin,m.tokenEndpoint(),false)
    &&!m.jwksUri().equals(m.tokenEndpoint())&&!m.jwksUri().equals(m.authorizationEndpoint());
  }catch(RuntimeException e){return false;}
 }
 private static boolean endpoint(URI issuer,String raw,boolean jwks){
  if(raw==null||raw.length()>1024||raw.indexOf('\\')>=0||raw.indexOf('%')>=0||raw.indexOf('@')>=0||raw.indexOf('#')>=0)return false;
  URI u=URI.create(raw);
  if(!u.isAbsolute()||!"https".equals(u.getScheme())||!Objects.equals(u.getHost(),issuer.getHost())||u.getPort()!=issuer.getPort()
    ||u.getRawUserInfo()!=null||u.getRawFragment()!=null||u.getRawQuery()!=null||u.getPath()==null||!u.getPath().startsWith("/"))return false;
  String path=u.getPath();
  return path.startsWith(issuer.getPath().endsWith("/")?issuer.getPath():issuer.getPath()+"/")&&!path.contains("//")&&!path.contains("/./")&&!path.contains("/../")&&!path.endsWith("/.")&&!path.endsWith("/..")
   &&(!jwks||path.endsWith("/certs")||path.endsWith("/jwks")||path.endsWith("/jwks.json"));
 }
}
