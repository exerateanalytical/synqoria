package org.synqoria.content;

import static org.synqoria.content.Db.m;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.synqoria.content.Db.Reply;
import org.synqoria.content.domain.Domain;
import org.synqoria.content.domain.Domain.*;

/** M30-S01 terminology: code systems, validated imports, lookup, hierarchy and approved mappings. */
@Service
public class TerminologyService {
 private final Db db;
 public TerminologyService(Db db){this.db=db;}

 public record SystemRequest(String systemUri,String name,String licensingClass,String licenseRef){}
 public record ConceptIn(String code,String status,String displayEn,String displayFr,String parent,List<String> synonyms){}
 public record ImportRequest(String versionLabel,String declaredChecksum,Instant effectiveFrom,String provenance,List<ConceptIn> concepts){}
 public record MappingRequest(UUID sourceVersionId,String sourceCode,UUID targetVersionId,String targetCode,String relation){}
 public record MappingDecision(long expectedVersion,boolean approve){}

 public Reply createSystem(Actor a,UUID cmd,SystemRequest r){
  a.require(Perm.TERM_IMPORT);
  return db.command(a,cmd,"SYSTEM_CREATE",r,()->{
   if(r==null||!Domain.validUri(r.systemUri())||r.name()==null||r.name().isBlank()||r.name().length()>200)throw new Violation("invalid_code_system");
   String cls=Domain.parse(LicensingClass.class,r.licensingClass(),"licensing_class").name();
   if(cls.equals("LICENSED_RESTRICTED")&&(r.licenseRef()==null||r.licenseRef().isBlank()||r.licenseRef().length()>200))
    throw new Violation("license_reference_required");
   if(!db.rows("SELECT 1 FROM synqoria_content.code_systems WHERE system_uri=?",r.systemUri()).isEmpty())throw new Violation("duplicate_record");
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_content.code_systems(id,cell_id,facility_id,system_uri,name,licensing_class,license_ref,created_by) VALUES (?,?,?,?,?,?,?,?)",
    id,a.cell(),a.facility(),r.systemUri(),r.name().strip(),cls,r.licenseRef(),a.id());
   db.audit(a,cmd,"CODESYSTEM",id,"CREATE",null,"REGISTERED",1,m("uri",r.systemUri(),"licensing",cls));
   return new Reply(201,db.one("SELECT * FROM synqoria_content.code_systems WHERE id=?",id));
  });
 }
 public enum LicensingClass{OPEN,INTERNAL,LICENSED_RESTRICTED}

 /** Import. A failed import is itself recorded (REJECTED, with every problem found) and nothing else changes. */
 public Reply importVersion(Actor a,UUID cmd,UUID systemId,ImportRequest r){
  a.require(Perm.TERM_IMPORT);
  return db.command(a,cmd,"IMPORT|"+systemId,r,()->{
   if(db.rows("SELECT 1 FROM synqoria_content.code_systems WHERE id=?",systemId).isEmpty())throw new Violation("not_found");
   if(r==null||r.versionLabel()==null||!r.versionLabel().matches("[A-Za-z0-9._-]{1,64}")||r.effectiveFrom()==null||
      r.provenance()==null||r.provenance().isBlank()||r.provenance().length()>500||r.declaredChecksum()==null||!r.declaredChecksum().matches("[0-9a-f]{64}"))
    throw new Violation("invalid_import");
   if(!db.rows("SELECT 1 FROM synqoria_content.code_system_versions WHERE code_system_id=? AND version_label=? AND status='PUBLISHED'",systemId,r.versionLabel()).isEmpty())
    throw new Violation("version_exists");
   List<ImportConcept> concepts=r.concepts()==null?List.of():r.concepts().stream()
    .map(c->new ImportConcept(c.code(),c.status(),c.displayEn(),c.displayFr(),c.parent(),c.synonyms())).toList();
   Set<String> previous=new HashSet<>();
   for(Map<String,Object> p:db.rows("SELECT c.code FROM synqoria_content.concepts c WHERE c.version_id=(SELECT id FROM synqoria_content.code_system_versions "+
     "WHERE code_system_id=? AND status='PUBLISHED' ORDER BY effective_from DESC,created_at DESC LIMIT 1) AND c.status='ACTIVE'",systemId))previous.add((String)p.get("code"));
   List<String> errors=new ArrayList<>(Domain.validateImport(concepts,previous));
   String actual=errors.contains("EMPTY_IMPORT")||errors.contains("IMPORT_TOO_LARGE")?Domain.sha256("invalid"):Domain.contentChecksum(concepts);
   if(!actual.equals(r.declaredChecksum()))errors.add("CHECKSUM_MISMATCH");
   Collections.sort(errors);
   UUID vid=UUID.randomUUID();
   int count=concepts.size();
   if(!errors.isEmpty()){
    db.jdbc.update("INSERT INTO synqoria_content.code_system_versions(id,cell_id,facility_id,code_system_id,version_label,status,content_checksum,declared_checksum,concept_count,"+
     "effective_from,provenance,rejection_codes,imported_by) VALUES (?,?,?,?,?,'REJECTED',?,?,?,?,?,?,?)",vid,a.cell(),a.facility(),systemId,r.versionLabel(),actual,
     r.declaredChecksum(),Math.min(count,Integer.MAX_VALUE),Timestamp.from(r.effectiveFrom()),r.provenance(),errors.stream().limit(200).toArray(String[]::new),a.id());
    db.audit(a,cmd,"CODESYSTEMVERSION",vid,"IMPORT_REJECT",null,"REJECTED",1,m("errors",errors.size(),"first",errors.getFirst()));
    throw new CommittedRejection("import_rejected",errors.stream().limit(50).toList());
   }
   db.jdbc.update("INSERT INTO synqoria_content.code_system_versions(id,cell_id,facility_id,code_system_id,version_label,status,content_checksum,declared_checksum,concept_count,"+
    "effective_from,provenance,imported_by) VALUES (?,?,?,?,?,'PUBLISHED',?,?,?,?,?,?)",vid,a.cell(),a.facility(),systemId,r.versionLabel(),actual,actual,count,
    Timestamp.from(r.effectiveFrom()),r.provenance(),a.id());
   List<Object[]> rows=new ArrayList<>(),syn=new ArrayList<>();
   for(ConceptIn c:r.concepts()){
    UUID cid=UUID.randomUUID();
    rows.add(new Object[]{cid,a.cell(),a.facility(),vid,c.code(),c.status(),c.displayEn().strip(),c.displayFr(),c.parent()});
    if(c.synonyms()!=null)for(String s:new TreeSet<>(c.synonyms()))syn.add(new Object[]{a.cell(),a.facility(),cid,s.strip()});
   }
   db.jdbc.batchUpdate("INSERT INTO synqoria_content.concepts(id,cell_id,facility_id,version_id,code,status,display_en,display_fr,parent_code) VALUES (?,?,?,?,?,?,?,?,?)",rows);
   if(!syn.isEmpty())db.jdbc.batchUpdate("INSERT INTO synqoria_content.concept_synonyms(cell_id,facility_id,concept_id,synonym) VALUES (?,?,?,?) ON CONFLICT DO NOTHING",syn);
   db.audit(a,cmd,"CODESYSTEMVERSION",vid,"IMPORT",null,"PUBLISHED",1,m("concepts",count,"checksum",actual));
   return new Reply(201,db.one("SELECT * FROM synqoria_content.code_system_versions WHERE id=?",vid));
  });
 }

 // ---------------------------------------------------------------- lookup
 private static final String EFFECTIVE_VERSION=
  "(SELECT v.id FROM synqoria_content.code_system_versions v WHERE v.code_system_id=cs.id AND v.status='PUBLISHED' AND v.effective_from<=? "+
  "ORDER BY v.effective_from DESC,v.created_at DESC LIMIT 1)";

 public Map<String,Object> systems(Actor a,int page,int size){
  a.require(Perm.TERM_READ);
  page(page,size);
  return db.scoped(a,()->{
   Timestamp now=Timestamp.from(db.clock.instant());
   long total=db.jdbc.queryForObject("SELECT count(*) FROM synqoria_content.code_systems",Long.class);
   return db.pageOf(db.rows("SELECT cs.id,cs.system_uri,cs.name,cs.licensing_class,cs.created_at,"+
    "(SELECT jsonb_build_object('id',v.id,'versionLabel',v.version_label,'conceptCount',v.concept_count,'effectiveFrom',v.effective_from) FROM synqoria_content.code_system_versions v WHERE v.id="+EFFECTIVE_VERSION+") AS effective_version, "+
    "(SELECT count(*) FROM synqoria_content.code_system_versions v WHERE v.code_system_id=cs.id AND v.status='REJECTED') AS rejected_imports "+
    "FROM synqoria_content.code_systems cs ORDER BY cs.name,cs.id LIMIT ? OFFSET ?",now,size,(long)page*size),page,size,total);
  });
 }
 public List<Map<String,Object>> versions(Actor a,UUID systemId){
  a.require(Perm.TERM_READ);
  return db.scoped(a,()->{
   db.one("SELECT id FROM synqoria_content.code_systems WHERE id=?",systemId);
   return db.rows("SELECT id,version_label,status,concept_count,effective_from,provenance,rejection_codes,content_checksum,created_at FROM synqoria_content.code_system_versions "+
    "WHERE code_system_id=? ORDER BY created_at DESC,id",systemId);
  });
 }
 private static void page(int page,int size){if(page<0||size<1||size>100)throw new Violation("invalid_pagination");}

 /** Search uses the effective PUBLISHED version unless a version is given; inactive concepts are hidden unless requested. */
 public Map<String,Object> search(Actor a,UUID systemId,UUID versionId,String q,String status,boolean includeInactive,int page,int size){
  a.require(Perm.TERM_READ);
  page(page,size);
  if(q!=null&&q.length()>100)throw new Violation("invalid_query");
  if(status!=null&&!List.of("ACTIVE","INACTIVE","DEPRECATED").contains(status))throw new Violation("invalid_status");
  return db.scoped(a,()->{
   List<Object> args=new ArrayList<>();
   Timestamp now=Timestamp.from(db.clock.instant());
   StringBuilder from=new StringBuilder(" FROM synqoria_content.concepts c JOIN synqoria_content.code_system_versions v ON v.id=c.version_id "+
    "JOIN synqoria_content.code_systems cs ON cs.id=v.code_system_id WHERE v.status='PUBLISHED'");
   if(versionId!=null){from.append(" AND v.id=?");args.add(versionId);}
   else{from.append(" AND v.id="+EFFECTIVE_VERSION.replace("cs.id","cs.id"));args.add(now);}
   if(systemId!=null){from.append(" AND cs.id=?");args.add(systemId);}
   if(status!=null){from.append(" AND c.status=?");args.add(status);}
   else if(!includeInactive)from.append(" AND c.status='ACTIVE'");
   if(q!=null&&!q.isBlank()){
    String like="%"+q.strip().toLowerCase().replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
    from.append(" AND (lower(c.code) LIKE ? OR lower(c.display_en) LIKE ? OR lower(coalesce(c.display_fr,'')) LIKE ? OR EXISTS(SELECT 1 FROM synqoria_content.concept_synonyms s WHERE s.concept_id=c.id AND lower(s.synonym) LIKE ?))");
    args.add(like);args.add(like);args.add(like);args.add(like);
   }
   long total=db.jdbc.queryForObject("SELECT count(*)"+from,Long.class,args.toArray());
   args.add(size);args.add((long)page*size);
   return db.pageOf(db.rows("SELECT c.id,c.code,c.status,c.display_en,c.display_fr,c.parent_code,c.version_id,v.version_label,cs.system_uri,cs.name AS system_name"+from+
    " ORDER BY c.code,c.id LIMIT ? OFFSET ?",args.toArray()),page,size,total);
  });
 }
 public Map<String,Object> concept(Actor a,UUID versionId,String code){
  a.require(Perm.TERM_READ);
  return db.scoped(a,()->{
   Map<String,Object> c=new LinkedHashMap<>(db.one("SELECT c.id,c.code,c.status,c.display_en,c.display_fr,c.parent_code,c.version_id,v.version_label,cs.system_uri,cs.licensing_class "+
    "FROM synqoria_content.concepts c JOIN synqoria_content.code_system_versions v ON v.id=c.version_id JOIN synqoria_content.code_systems cs ON cs.id=v.code_system_id "+
    "WHERE c.version_id=? AND c.code=?",versionId,code));
   c.put("synonyms",db.jdbc.queryForList("SELECT synonym FROM synqoria_content.concept_synonyms WHERE concept_id=? ORDER BY synonym",String.class,c.get("id")));
   c.put("children",db.rows("SELECT code,status,display_en,display_fr FROM synqoria_content.concepts WHERE version_id=? AND parent_code=? ORDER BY code LIMIT 200",versionId,code));
   List<Map<String,Object>> ancestors=new ArrayList<>();
   String parent=(String)c.get("parentCode");
   for(int i=0;parent!=null&&i<32;i++){
    Map<String,Object> p=db.one("SELECT code,status,display_en,display_fr,parent_code FROM synqoria_content.concepts WHERE version_id=? AND code=?",versionId,parent);
    ancestors.add(p);parent=(String)p.get("parentCode");
   }
   c.put("ancestors",ancestors);
   c.put("mappings",db.rows("SELECT m.id,m.relation,m.target_version_id,m.target_code,t.display_en AS target_display,cs.system_uri AS target_system "+
    "FROM synqoria_content.concept_mappings m JOIN synqoria_content.concepts t ON t.version_id=m.target_version_id AND t.code=m.target_code "+
    "JOIN synqoria_content.code_system_versions tv ON tv.id=m.target_version_id JOIN synqoria_content.code_systems cs ON cs.id=tv.code_system_id "+
    "WHERE m.source_version_id=? AND m.source_code=? AND m.status='APPROVED' ORDER BY cs.system_uri,m.target_code",versionId,code));
   return c;
  });
 }
 public Map<String,Object> roots(Actor a,UUID versionId,int page,int size){
  a.require(Perm.TERM_READ);page(page,size);
  return db.scoped(a,()->{
   db.one("SELECT id FROM synqoria_content.code_system_versions WHERE id=? AND status='PUBLISHED'",versionId);
   long total=db.jdbc.queryForObject("SELECT count(*) FROM synqoria_content.concepts WHERE version_id=? AND parent_code IS NULL",Long.class,versionId);
   return db.pageOf(db.rows("SELECT code,status,display_en,display_fr,(SELECT count(*) FROM synqoria_content.concepts k WHERE k.version_id=c.version_id AND k.parent_code=c.code) AS child_count "+
    "FROM synqoria_content.concepts c WHERE version_id=? AND parent_code IS NULL ORDER BY code LIMIT ? OFFSET ?",versionId,size,(long)page*size),page,size,total);
  });
 }

 // ---------------------------------------------------------------- mappings (maker-checker)
 public Reply propose(Actor a,UUID cmd,MappingRequest r){
  a.require(Perm.MAP_PROPOSE);
  return db.command(a,cmd,"MAP_PROPOSE",r,()->{
   if(r==null||r.sourceVersionId()==null||r.targetVersionId()==null||!Domain.validCode(r.sourceCode())||!Domain.validCode(r.targetCode()))throw new Violation("invalid_mapping");
   if(!List.of("EQUIVALENT","NARROWER","BROADER","RELATED").contains(r.relation()))throw new Violation("invalid_relation");
   if(r.sourceVersionId().equals(r.targetVersionId()))throw new Violation("invalid_mapping");
   for(Object[] side:new Object[][]{{r.sourceVersionId(),r.sourceCode()},{r.targetVersionId(),r.targetCode()}})
    if(db.rows("SELECT 1 FROM synqoria_content.concepts c JOIN synqoria_content.code_system_versions v ON v.id=c.version_id WHERE c.version_id=? AND c.code=? AND v.status='PUBLISHED'",side[0],side[1]).isEmpty())
     throw new Violation("concept_not_found");
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_content.concept_mappings(id,cell_id,facility_id,source_version_id,source_code,target_version_id,target_code,relation,status,proposed_by,version) "+
    "VALUES (?,?,?,?,?,?,?,?,'PROPOSED',?,1)",id,a.cell(),a.facility(),r.sourceVersionId(),r.sourceCode(),r.targetVersionId(),r.targetCode(),r.relation(),a.id());
   db.audit(a,cmd,"MAPPING",id,"PROPOSE",null,"PROPOSED",1,m("relation",r.relation()));
   return new Reply(201,db.one("SELECT * FROM synqoria_content.concept_mappings WHERE id=?",id));
  });
 }
 public Reply decide(Actor a,UUID cmd,UUID id,MappingDecision d){
  a.require(Perm.MAP_APPROVE);
  return db.command(a,cmd,"MAP_DECIDE|"+id,d,()->{
   List<Map<String,Object>> r=db.rows("SELECT * FROM synqoria_content.concept_mappings WHERE id=? FOR UPDATE",id);
   if(r.isEmpty())throw new Violation("not_found");
   Map<String,Object> mp=r.getFirst();
   if(((Number)mp.get("version")).longValue()!=d.expectedVersion())throw new Violation("stale_version");
   if(!"PROPOSED".equals(mp.get("status")))throw new Violation("invalid_transition");
   if(a.id().equals(mp.get("proposedBy")))throw new Violation("approver_must_be_independent");
   if(d.approve()&&"EQUIVALENT".equals(mp.get("relation"))&&!db.rows("SELECT 1 FROM synqoria_content.concept_mappings WHERE status='APPROVED' AND relation='EQUIVALENT' "+
     "AND source_version_id=? AND source_code=? AND target_version_id=?",mp.get("sourceVersionId"),mp.get("sourceCode"),mp.get("targetVersionId")).isEmpty())
    throw new Violation("mapping_conflict");
   String to=d.approve()?"APPROVED":"REJECTED";
   db.jdbc.update("UPDATE synqoria_content.concept_mappings SET status=?,decided_by=?,decided_at=clock_timestamp(),version=version+1 WHERE id=?",to,a.id(),id);
   db.audit(a,cmd,"MAPPING",id,d.approve()?"APPROVE":"REJECT","PROPOSED",to,d.expectedVersion()+1,m());
   return new Reply(200,db.one("SELECT * FROM synqoria_content.concept_mappings WHERE id=?",id));
  });
 }
 public Map<String,Object> listMappings(Actor a,String status,int page,int size){
  a.require(Perm.TERM_READ);page(page,size);
  return db.scoped(a,()->{
   String w=status==null?"":" WHERE status=?";
   Object[] base=status==null?new Object[0]:new Object[]{status};
   long total=db.jdbc.queryForObject("SELECT count(*) FROM synqoria_content.concept_mappings"+w,Long.class,base);
   Object[] args=Arrays.copyOf(base,base.length+2);args[base.length]=size;args[base.length+1]=(long)page*size;
   return db.pageOf(db.rows("SELECT * FROM synqoria_content.concept_mappings"+w+" ORDER BY created_at DESC,id LIMIT ? OFFSET ?",args),page,size,total);
  });
 }
}
