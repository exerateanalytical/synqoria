package org.synqoria.longitudinal;

import java.time.*;
import java.util.*;
import static org.synqoria.longitudinal.ClinicalCaseCore.*;
import static org.synqoria.longitudinal.ClinicalActionInput.*;

/** M04 encounter / ADT domain actions. Patient and facility identity originate only in the trusted Scope. */
public final class EncounterActions {
    private EncounterActions(){}
    public static final List<String> OPERATIONS=List.of(
       "encounter.checkin", "encounter.activate", "encounter.assign_clinician", "encounter.join_team",
       "encounter.leave_team", "encounter.change_responsible", "encounter.request_admission",
       "encounter.approve_admission", "encounter.propose_bed", "encounter.release_bed_proposal",
       "encounter.record_room", "encounter.record_location", "encounter.request_transfer",
       "encounter.accept_transfer", "encounter.cancel_transfer", "encounter.record_accepted_transfer",
       "encounter.record_leave", "encounter.record_return", "encounter.submit_discharge_plan",
       "encounter.authorize_discharge"
    );
    public static Result run(ClinicalCaseCore engine,Scope scope,Actor actor,Command command,
                             String operation,Map<String,String> input){
        if(!OPERATIONS.contains(operation))throw new IllegalArgumentException("unknown encounter action");
        Map<String,String> in=switch(operation) {
            case "encounter.checkin" -> validateExact(input);
            case "encounter.activate" -> validateExact(input);
            case "encounter.assign_clinician" -> validateExact(input,"clinicianId");
            case "encounter.join_team" -> validateExact(input,"clinicianId");
            case "encounter.leave_team" -> validateExact(input,"clinicianId");
            case "encounter.change_responsible" -> validateExact(input,"clinicianId");
            case "encounter.request_admission" -> validateExact(input,"reason");
            case "encounter.approve_admission" -> validateExact(input);
            case "encounter.propose_bed" -> validateExact(input,"bedId");
            case "encounter.release_bed_proposal" -> validateExact(input);
            case "encounter.record_room" -> validateExact(input,"room");
            case "encounter.record_location" -> validateExact(input,"location");
            case "encounter.request_transfer" -> validateExact(input,"destinationId");
            case "encounter.accept_transfer" -> validateExact(input);
            case "encounter.cancel_transfer" -> validateExact(input);
            case "encounter.record_accepted_transfer" -> validateExact(input);
            case "encounter.record_leave" -> validateExact(input);
            case "encounter.record_return" -> validateExact(input);
            case "encounter.submit_discharge_plan" -> validateExact(input,"plan");
            case "encounter.authorize_discharge" -> validateExact(input,"authority");
            default -> throw new IllegalArgumentException("unknown action");
        };
        return engine.execute(scope,actor,command,"encounter.write",operation,canonical(in),s->apply(s,actor,command,operation,in));
    }
    private static void apply(State s,Actor a,Command c,String op,Map<String,String> in){
        switch(op){
            case "encounter.checkin" -> {require(s.stage==Stage.PLANNED&&s.checkedInAt==null);
                s.checkedInAt=c.at();}
            case "encounter.activate" -> {require(s.stage==Stage.PLANNED&&s.checkedInAt!=null);
                s.stage=Stage.ACTIVE;s.startedAt=c.at();}
            case "encounter.assign_clinician" -> {require(s.stage==Stage.ACTIVE&&s.responsible==null);
                a.require("encounter.assign");s.responsible=requiredId(in,"clinicianId");s.careTeam.add(s.responsible);}
            case "encounter.join_team" -> {require(s.stage==Stage.ACTIVE);
                require(s.careTeam.add(requiredId(in,"clinicianId")));}
            case "encounter.leave_team" -> {require(s.stage==Stage.ACTIVE);
                var id=requiredId(in,"clinicianId");forbid(id.equals(s.responsible));require(s.careTeam.remove(id));}
            case "encounter.change_responsible" -> {require(s.stage==Stage.ACTIVE);
                a.require("encounter.assign");var id=requiredId(in,"clinicianId");require(s.careTeam.contains(id));s.responsible=id;}
            case "encounter.request_admission" -> {require(s.stage==Stage.ACTIVE&&!s.admissionRequested);
                s.admissionReason=required(in,"reason",1000);s.admissionRequested=true;s.admissionRequester=a.id();}
            case "encounter.approve_admission" -> {require(s.stage==Stage.ACTIVE&&s.admissionRequested&&!s.admissionApproved);
                a.require("encounter.admission.approve");forbid(a.id().equals(s.admissionRequester));s.admissionApproved=true;}
            case "encounter.propose_bed" -> {require(s.stage==Stage.ACTIVE&&s.admissionApproved&&s.bedReservation==null);
                // This is NOT a bed reservation or occupancy; external bed authority must provide cross-case atomic booking.
                s.bedReservation=requiredId(in,"bedId");}
            case "encounter.release_bed_proposal" -> {require(s.stage==Stage.ACTIVE&&s.bedReservation!=null);
                s.bedReservation=null;}
            case "encounter.record_room" -> {require(s.stage==Stage.ACTIVE);
                s.room=required(in,"room",128);}
            case "encounter.record_location" -> {require(s.stage==Stage.ACTIVE);
                s.location=required(in,"location",128);}
            case "encounter.request_transfer" -> {require(s.stage==Stage.ACTIVE&&!s.transferRequested);
                s.transferDestination=requiredId(in,"destinationId");
                s.transferRequested=true;s.transferAccepted=false;s.transferRequester=a.id();}
            case "encounter.accept_transfer" -> {require(s.stage==Stage.ACTIVE&&s.transferRequested&&!s.transferAccepted);
                a.require("encounter.transfer.accept");forbid(a.id().equals(s.transferRequester));s.transferAccepted=true;}
            case "encounter.cancel_transfer" -> {require(s.stage==Stage.ACTIVE&&s.transferRequested);
                s.transferRequested=false;s.transferAccepted=false;s.transferDestination=null;s.transferRequester=null;}
            case "encounter.record_accepted_transfer" -> {require(s.stage==Stage.ACTIVE&&s.transferRequested&&s.transferAccepted);
                // Facility transfer requires a separate cross-cell transaction; here only the accepted request is recorded.
                s.location="transfer-accepted:"+s.transferDestination;
                s.transferRequested=false;s.transferAccepted=false;s.transferDestination=null;s.transferRequester=null;}
            case "encounter.record_leave" -> {require(s.stage==Stage.ACTIVE&&!s.onLeave);s.onLeave=true;}
            case "encounter.record_return" -> {require(s.stage==Stage.ACTIVE&&s.onLeave);s.onLeave=false;}
            case "encounter.submit_discharge_plan" -> {require(s.stage==Stage.ACTIVE&&!s.onLeave&&s.responsible!=null);
                s.dischargePlan=required(in,"plan",4096);s.stage=Stage.DISCHARGE_PLANNED;}
            case "encounter.authorize_discharge" -> {require(s.stage==Stage.DISCHARGE_PLANNED&&!s.onLeave);
                a.require("encounter.discharge.authorize");s.dischargeAuthority=required(in,"authority",256);
                s.dischargeAuthorized=true;s.dischargeAt=c.at();s.stage=Stage.DISCHARGED;}
            default -> throw new IllegalArgumentException("unknown encounter operation");
        }
    }
}
