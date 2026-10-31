package org.synqoria.http;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.time.Clock;
import org.synqoria.security.PatientDisclosureFence;
import java.util.*;
/** Limited read-only boundary. No production identity verifier, policy store or database is supplied. */
public final class ClinicalHttpGateway implements HttpHandler {
 private final BearerAdmission bearer;private final FacilityAccessPort facilities;private final PatientReadPort patients;private final Clock clock;private final IntegrationReadiness readiness;
 public ClinicalHttpGateway(BearerAdmission bearer,CellFacilityGate facilities,AuditedPatientAccess patients,Clock clock){
  this(bearer,facilities::resolve,(actor,id,correlation,now)->patients.read(actor,id,now),clock,IntegrationReadiness.unavailable());
 }
 /** Use this overload with a transaction-bound JdbcPatientReadPort and independently verified dependency probes. */
 public ClinicalHttpGateway(BearerAdmission bearer,FacilityAccessPort facilities,PatientReadPort patients,Clock clock,IntegrationReadiness readiness){
  this.bearer=Objects.requireNonNull(bearer);this.facilities=Objects.requireNonNull(facilities);this.patients=Objects.requireNonNull(patients);this.clock=Objects.requireNonNull(clock);this.readiness=Objects.requireNonNull(readiness);
 }
 @Override public void handle(HttpExchange x) throws IOException {
  final UUID correlationId=UUID.randomUUID();
  try {
   var route=CanonicalRequestPath.parse(x.getRequestURI());
   if(route==null){ClinicalResponse.send(x,404,ClinicalResponse.problem(404,"not_found"),correlationId);return;}
   int envelopeRejection=RequestEnvelopePolicy.rejection(x);
   if(envelopeRejection!=0){ClinicalResponse.send(x,envelopeRejection,ClinicalResponse.problem(envelopeRejection,envelopeRejection==405?"method_not_allowed":envelopeRejection==406?"not_acceptable":"invalid_request"),correlationId);return;}
   if(route.resource().equals("live")){ClinicalResponse.send(x,200,"{\"process\":\"alive\"}",correlationId);return;}
   if(route.resource().equals("ready")){boolean ready=readiness.ready();ClinicalResponse.send(x,ready?200:503,ready?"{\"status\":\"ready\"}":ClinicalResponse.problem(503,"not_integrated"),correlationId);return;}
   var trusted=bearer.admit(x.getRequestHeaders(),clock.instant());
   if(trusted.isEmpty()){ClinicalResponse.send(x,401,ClinicalResponse.problem(401,"authentication_required"),correlationId);return;}
   var actor=trusted.get();
   var facility=facilities.resolve(actor);
   if(facility.isEmpty()){ClinicalResponse.send(x,404,ClinicalResponse.problem(404,"not_found"),correlationId);return;}
   if(route.resource().equals("facility")){
    if(!actor.capabilities().contains("facility:view")){ClinicalResponse.send(x,404,ClinicalResponse.problem(404,"not_found"),correlationId);return;}
    var f=facility.get();
    ClinicalResponse.send(x,200,ClinicalPayload.facility(f),correlationId);return;
   }
   var result=patients.read(actor,route.id(),correlationId,clock.instant());
   if(result.status()==AuditedPatientAccess.Status.NOT_DISCLOSED){ClinicalResponse.send(x,404,ClinicalResponse.problem(404,"not_found"),correlationId);return;}
   if(result.status()==AuditedPatientAccess.Status.UNAVAILABLE){ClinicalResponse.send(x,503,ClinicalResponse.problem(503,"service_unavailable"),correlationId);return;}
   var verified=PatientDisclosureFence.release(actor,route.id(),result);
   if(verified.isEmpty()){ClinicalResponse.send(x,404,ClinicalResponse.problem(404,"not_found"),correlationId);return;}
   ClinicalResponse.send(x,200,ClinicalPayload.patient(verified.get()),correlationId);
  }catch(RuntimeException e){ClinicalResponse.send(x,503,ClinicalResponse.problem(503,"service_unavailable"),correlationId);}
 }
}
