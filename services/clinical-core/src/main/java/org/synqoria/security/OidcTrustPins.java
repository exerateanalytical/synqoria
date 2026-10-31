package org.synqoria.security;
import java.net.URI;import java.util.*;
/** P03-B0020: issuer/client/audience pins for independently verified claims, no network discovery. */
public record OidcTrustPins(String issuer,String audience,String authorizedParty) {
 public OidcTrustPins {
  try{URI uri=URI.create(Objects.requireNonNull(issuer));
   if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null||uri.getQuery()!=null||uri.getPort()!=-1||issuer.endsWith("/")||issuer.length()>512)throw new IllegalArgumentException("Invalid issuer");
  }catch(RuntimeException e){throw new IllegalArgumentException("Invalid issuer",e);}
  if(audience==null||!audience.matches("[A-Za-z0-9._:/-]{3,256}")||authorizedParty==null||!authorizedParty.matches("[A-Za-z0-9._:-]{3,128}"))throw new IllegalArgumentException("Invalid client pins");
 }
 public boolean matches(String actualIssuer,Set<String> audiences,String azp){
  return issuer.equals(actualIssuer)&&audiences!=null&&audiences.size()>=1&&audiences.size()<=4&&audiences.contains(audience)&&authorizedParty.equals(azp);
 }
}
