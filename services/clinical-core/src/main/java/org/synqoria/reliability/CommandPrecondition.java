package org.synqoria.reliability;
/** P01-B0012 conditional mutation protocol. This is a domain adapter, not a running HTTP handler. */
public final class CommandPrecondition {
 private CommandPrecondition(){}
 public enum Decision { PROCEED, PRECONDITION_REQUIRED, STALE, MALFORMED }
 public static String etag(long version){if(version<1)throw new IllegalArgumentException("version");return "\"v"+version+"\"";}
 public static Decision check(String ifMatch,long currentVersion){
  if(currentVersion<1)throw new IllegalArgumentException("invalid authoritative version");
  if(ifMatch==null||ifMatch.isEmpty())return Decision.PRECONDITION_REQUIRED;
  if(!ifMatch.matches("\\\"v[1-9][0-9]{0,17}\\\""))return Decision.MALFORMED;
  try { long supplied=Long.parseLong(ifMatch.substring(2,ifMatch.length()-1));return supplied==currentVersion?Decision.PROCEED:Decision.STALE; }
  catch(NumberFormatException ex){return Decision.MALFORMED;}
 }
 public static int httpStatus(Decision d){return switch(d){case PROCEED->200;case PRECONDITION_REQUIRED->428;case STALE->412;case MALFORMED->400;};}
}
