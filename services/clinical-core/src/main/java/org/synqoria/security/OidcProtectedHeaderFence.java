package org.synqoria.security;
import java.util.*;
/** P03-B0025: strict policy for a header decoded by a separately trusted duplicate-rejecting JSON parser. */
public final class OidcProtectedHeaderFence {
 private OidcProtectedHeaderFence(){}
 public static boolean allows(Map<String,?> header,String expectedKid){
  if(header==null||header.size()!=3||expectedKid==null||!expectedKid.matches("[A-Za-z0-9_-]{8,80}"))return false;
  return "RS256".equals(header.get("alg"))&&"at+jwt".equals(header.get("typ"))&&expectedKid.equals(header.get("kid"));
 }
}
