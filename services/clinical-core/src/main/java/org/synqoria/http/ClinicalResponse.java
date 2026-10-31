package org.synqoria.http;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
/** JSON only, no patient details in logs, no caching or redirects. */
public final class ClinicalResponse {
 private ClinicalResponse() {}
 public static void send(HttpExchange x,int status,String json) throws IOException {send(x,status,json,UUID.randomUUID());}
 public static void send(HttpExchange x,int status,String json,UUID requestId) throws IOException {
  if(requestId==null)throw new IllegalArgumentException("Correlation ID must be server-generated");
  var headers=x.getResponseHeaders();
  headers.set("Content-Type","application/json; charset=utf-8");
  headers.set("Cache-Control","no-store, private");
  headers.set("Pragma","no-cache");
  headers.set("X-Content-Type-Options","nosniff");
  headers.set("Referrer-Policy","no-referrer");
  headers.set("Content-Security-Policy","default-src 'none'; frame-ancestors 'none'");
  headers.set("Cross-Origin-Resource-Policy","same-origin");
  // P01-B0018: server-generated opaque trace token, never a user-supplied value.
  headers.set("X-Request-ID",requestId.toString());
  byte[] b=json.getBytes(StandardCharsets.UTF_8);
  x.sendResponseHeaders(status,b.length);
  try(var out=x.getResponseBody()){out.write(b);}
 }
 public static String problem(int status,String code) {return "{\"status\":"+status+",\"code\":\""+code+"\"}";}
}
