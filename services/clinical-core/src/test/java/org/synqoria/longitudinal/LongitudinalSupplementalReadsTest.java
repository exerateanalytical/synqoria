package org.synqoria.longitudinal;

import java.net.URI;
import java.util.*;
import static org.synqoria.longitudinal.LongitudinalHttpBoundaryTest.*;

/** HTTP-level scoped confidential note list and bidirectional observation correction provenance. */
public final class LongitudinalSupplementalReadsTest {
 static int checks;
 static void ok(boolean yes){checks++;if(!yes)throw new AssertionError("supplemental clinical read "+checks);}
 static void noteIndex()throws Exception{
  try(F f=new F()){
   f.active();UUID publicId=NOTE,secretId=UUID.fromString("10000000-0000-0000-0000-00000000000b");
   status(f.write(TOKEN_A,"note.create_draft",Map.of("noteId",publicId.toString())),200);
   status(f.write(TOKEN_A,"note.create_draft",Map.of("noteId",secretId.toString())),200);
   status(f.write(TOKEN_A,"note.mark_confidential",Map.of("noteId",secretId.toString(),"confidential","true")),200);
   var all=f.get("/note-index?limit=10");status(all,200);
   ok(all.body().contains(publicId.toString())&&all.body().contains(secretId.toString()));
   ok(!all.body().contains("\"body\""));
   f.allowConfidential=false;var filtered=f.get("/note-index?limit=10");status(filtered,200);
   ok(filtered.body().contains(publicId.toString())&&!filtered.body().contains(secretId.toString()));
   var page=f.get("/note-index?limit=1");status(page,200);ok(page.body().contains("\"nextId\""));
   status(f.get("/note-index?limit=101"),422);
   status(f.get("/note-index?afterId=12345"),404);
   status(f.getWith(null,URI.create(f.base+"/note-index")),401);
  }
 }
 static void lineage()throws Exception{
  try(F f=new F()){
   f.active();
   Map<String,String> original=Map.of("observationId",OBS.toString(),"code","local:pulse", "unit","/min",
     "value","82","measuredAt","2026-10-31T10:40:00Z");
   status(f.write(TOKEN_A,"observation.capture_numeric",original),200);
   status(f.write(TOKEN_A,"observation.request_verification",Map.of("observationId",OBS.toString())),200);
   status(f.write(TOKEN_B,"observation.verify",Map.of("observationId",OBS.toString())),200);
   Map<String,String> corrected=Map.of("observationId",OBS2.toString(),"priorId",OBS.toString(),"code","local:pulse",
       "unit","/min","value","84","measuredAt","2026-10-31T10:43:00Z");
   status(f.write(TOKEN_A,"observation.capture_correction",corrected),200);
   var old=f.get("/observations/"+OBS+"/lineage");status(old,200);
   ok(old.body().contains(OBS.toString())&&old.body().contains(OBS2.toString()));
   var recent=f.get("/observations/"+OBS2+"/lineage");status(recent,200);
   ok(recent.body().contains(OBS.toString())&&recent.body().contains(OBS2.toString()));
   ok(recent.body().contains("\"replaces\":\""+OBS+"\""));
   ok(recent.body().indexOf(OBS.toString())<recent.body().indexOf(OBS2.toString()));
   status(f.get("/observations/"+OBS2+"/lineage?limit=1"),404);
   status(f.get("/observations/00000000-0000-0000-0000-000000000000/lineage"),404);
   status(f.getWith(null,URI.create(f.base+"/observations/"+OBS+"/lineage")),401);
  }
 }
 public static void main(String[] args)throws Exception{
  if(args.length!=1)throw new IllegalArgumentException("note-index, lineage, all");
  if(args[0].equals("note-index")||args[0].equals("all"))noteIndex();
  if(args[0].equals("lineage")||args[0].equals("all"))lineage();
  System.out.println("PASS supplemental reads "+args[0]+" ("+checks+" focused assertions)");
 }
}
