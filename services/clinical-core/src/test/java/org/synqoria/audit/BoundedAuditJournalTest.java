package org.synqoria.audit;
import java.nio.file.*;
import java.time.*;
import java.util.*;
public final class BoundedAuditJournalTest {
 static int n=0;static void ok(boolean v){if(!v)throw new AssertionError("audit test "+n);n++;}
 public static void main(String[] args)throws Exception{
  Path p=Files.createTempDirectory("synqoria-audit-test");Path file=p.resolve("patient-read.log");
  var j=new BoundedAuditJournal(file);UUID a=UUID.randomUUID(),t=UUID.randomUUID(),f=UUID.randomUUID(),id=UUID.randomUUID();
  ok(!j.verify());ok(j.commit(a,t,f,id,"patient:read",Instant.now()));ok(j.verify());
  ok(j.commit(a,t,f,id,"patient:read",Instant.now()));ok(Files.readAllLines(file).size()==2);ok(j.verify());
  ok(!j.commit(a,t,f,id,"unsafe:read",Instant.now()));
  Files.writeString(file,"tampered\n",StandardOpenOption.APPEND);ok(!j.verify());ok(!j.commit(a,t,f,id,"patient:read",Instant.now()));
  Path link=p.resolve("link.log");try{Files.createSymbolicLink(link,file);ok(!new BoundedAuditJournal(link).commit(a,t,f,id,"patient:read",Instant.now()));}catch(UnsupportedOperationException|java.nio.file.FileSystemException e){System.out.println("SKIP symlink test unavailable");}
  System.out.println("PASS P03-B0011: "+n+" local audit journal assertions; not production immutability");
 }
}
