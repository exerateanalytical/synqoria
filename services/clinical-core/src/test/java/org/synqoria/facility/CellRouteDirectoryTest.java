package org.synqoria.facility;
import org.synqoria.security.TrustedAccessContext;
import java.net.URI;import java.util.*;
public class CellRouteDirectoryTest {
 static int n;static void ok(boolean b,String m){if(!b)throw new AssertionError(m);n++;}
 public static void main(String[] args){
  var cell=UUID.randomUUID();var tenant=UUID.randomUUID();var facility=UUID.randomUUID();var actor=UUID.randomUUID();
  var binding=new FacilityBinding(cell,tenant,facility,"ACTIVE",1);
  var caller=new TrustedAccessContext(tenant,facility,actor,Set.of("workspace:clinical"),Set.of());
  var active=new CellRouteDirectory.Cell(cell,URI.create("https://hospital-a.example.invalid"),"ACTIVE",1);
  var mapping=new CellRouteDirectory.Tenant(tenant,cell,true);
  var dir=new CellRouteDirectory(List.of(active),List.of(mapping),new FacilityDirectory(List.of(binding)));
  ok(dir.route(caller).orElseThrow().equals(active.endpoint()),"trusted route pinned");
  ok(dir.route(null).isEmpty(),"null denied");
  ok(dir.route(new TrustedAccessContext(UUID.randomUUID(),facility,actor,Set.of(),Set.of())).isEmpty(),"cross tenant denied");
  ok(dir.route(new TrustedAccessContext(tenant,UUID.randomUUID(),actor,Set.of(),Set.of())).isEmpty(),"cross facility denied");
  ok(new CellRouteDirectory(List.of(active),List.of(new CellRouteDirectory.Tenant(tenant,cell,false)),new FacilityDirectory(List.of(binding))).route(caller).isEmpty(),"suspended tenant denied");
  var suspended=new CellRouteDirectory.Cell(cell,active.endpoint(),"SUSPENDED",2);
  ok(new CellRouteDirectory(List.of(suspended),List.of(mapping),new FacilityDirectory(List.of(binding))).route(caller).isEmpty(),"suspended cell denied");
  var stopped=new FacilityBinding(cell,tenant,facility,"SUSPENDED",2);
  ok(new CellRouteDirectory(List.of(active),List.of(mapping),new FacilityDirectory(List.of(stopped))).route(caller).isEmpty(),"suspended facility denied");
  for(var url:List.of("http://hospital-a.example.invalid","https://127.0.0.1","https://localhost","https://hospital-a.example.invalid@evil.example.invalid/path",
      "https://hospital-a.example.invalid:8443","https://hospital-a.example.invalid?redirect=evil","https://hospital-a.example.invalid/secret")){
   boolean denied=false;try{new CellRouteDirectory.Cell(cell,URI.create(url),"ACTIVE",1);}catch(IllegalArgumentException e){denied=true;}ok(denied,"endpoint rejected "+url);
  }
  boolean denied=false;try{new CellRouteDirectory(List.of(active,active),List.of(mapping),new FacilityDirectory(List.of(binding)));}catch(IllegalArgumentException e){denied=true;}ok(denied,"duplicate cell denied");
  System.out.println("PASS: "+n+" cell routing assertions (no network/TLS)");
 }
}
