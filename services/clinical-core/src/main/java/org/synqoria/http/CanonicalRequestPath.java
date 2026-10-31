package org.synqoria.http;
import java.net.URI;
import java.util.UUID;
/** Reject ambiguous URIs before any patient/repository lookup. */
public final class CanonicalRequestPath {
 private CanonicalRequestPath() {}
 public record Match(String resource, UUID id) {}
 public static Match parse(URI uri) {
  if(uri==null || uri.getRawQuery()!=null || uri.getRawFragment()!=null)return null;
  String path=uri.getRawPath();
  if(path==null||path.length()>200||!path.matches("/[a-z0-9/-]*")||path.contains("//"))return null;
  if(path.equals("/health/live"))return new Match("live",null);
  if(path.equals("/health/ready"))return new Match("ready",null);
  if(path.equals("/api/v1/facility/context"))return new Match("facility",null);
  String prefix="/api/v1/patients/";
  if(!path.startsWith(prefix))return null;
  String part=path.substring(prefix.length());
  if(!part.matches("[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))return null;
  try{return new Match("patient",UUID.fromString(part));}catch(IllegalArgumentException ex){return null;}
 }
}
