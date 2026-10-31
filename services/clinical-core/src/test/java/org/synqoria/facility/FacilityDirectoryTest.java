package org.synqoria.facility;

import org.synqoria.security.TrustedAccessContext;
import java.util.*;

public class FacilityDirectoryTest {
    private static int passed = 0;
    private static void check(boolean yes, String name) { if (!yes) throw new AssertionError(name); passed++; }
    public static void main(String[] args) {
        var cell=UUID.randomUUID(); var tenant=UUID.randomUUID(); var facility=UUID.randomUUID(); var actor=UUID.randomUUID();
        var active=new FacilityBinding(cell,tenant,facility,"ACTIVE",1);
        var ctx=new TrustedAccessContext(tenant,facility,actor,Set.of(),Set.of());
        var directory=new FacilityDirectory(List.of(active));
        check(directory.permitsActiveFacility(ctx,cell),"active matching relationship");
        check(!directory.permitsActiveFacility(ctx,UUID.randomUUID()),"wrong cell denied");
        check(!directory.permitsActiveFacility(null,cell),"missing actor scope denied");
        check(!directory.permitsActiveFacility(ctx,null),"missing cell denied");
        check(!directory.permitsActiveFacility(new TrustedAccessContext(UUID.randomUUID(),facility,actor,Set.of(),Set.of()),cell),"wrong tenant denied");
        check(!directory.permitsActiveFacility(new TrustedAccessContext(tenant,UUID.randomUUID(),actor,Set.of(),Set.of()),cell),"wrong facility denied");
        check(new FacilityDirectory(List.of(new FacilityBinding(cell,tenant,facility,"SUSPENDED",1))).findForTrustedContext(ctx,cell).isEmpty(),"suspension denies");
        check(new FacilityDirectory(List.of(new FacilityBinding(cell,tenant,facility,"RETIRED",1))).findForTrustedContext(ctx,cell).isEmpty(),"retired denies");
        check(new FacilityDirectory(List.of()).findForTrustedContext(ctx,cell).isEmpty(),"missing record denies");
        check(directory.findForTrustedContext(ctx,cell).orElseThrow().revision()==1,"revision retained");
        boolean rejected=false;try { new FacilityBinding(cell,tenant,facility,"ACTIVE",0); } catch (IllegalArgumentException ex) {rejected=true;}
        check(rejected,"unversioned facility rejected");
        rejected=false;try { new FacilityBinding(cell,tenant,facility,"UNKNOWN",1); } catch (IllegalArgumentException ex) {rejected=true;}
        check(rejected,"undefined facility status rejected");
        rejected=false;try { new FacilityDirectory(List.of(active,active)); } catch (IllegalArgumentException ex) {rejected=true;}
        check(rejected,"duplicate facility rejected");
        // Snapshot API intentionally has no mutation method.
        System.out.println("PASS: "+passed+" Java facility directory assertions");
    }
}
