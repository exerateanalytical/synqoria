package org.synqoria.http;
import com.sun.net.httpserver.HttpExchange;
import java.util.*;
/** P01-B0017: read-only clinical endpoint rejects ambiguous request framing and browser cross-site requests. */
public final class RequestEnvelopePolicy {
 private RequestEnvelopePolicy(){}
 public static int rejection(HttpExchange exchange){
  var h=exchange.getRequestHeaders();
  if(!"GET".equals(exchange.getRequestMethod()))return 405;
  var a=h.get("Accept");
  if(a!=null&&(a.size()!=1||a.get(0).length()>128||!List.of("application/json","application/json; charset=utf-8","*/*").contains(a.get(0).trim().toLowerCase(Locale.ROOT))))return 406;
  if(h.containsKey("Transfer-Encoding")||h.containsKey("Origin")||h.containsKey("Cookie")||h.containsKey("Expect"))return 400;
  var lengths=h.get("Content-Length");
  if(lengths!=null&&(lengths.size()!=1||!"0".equals(lengths.get(0))))return 400;
  var contentTypes=h.get("Content-Type");
  if(contentTypes!=null)return 400;
  return 0;
 }
}
