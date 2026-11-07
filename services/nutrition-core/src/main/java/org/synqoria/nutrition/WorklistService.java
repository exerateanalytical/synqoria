package org.synqoria.nutrition;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.synqoria.nutrition.domain.Domain;
import org.synqoria.nutrition.domain.Domain.*;

/** M26-S01 dietary patient worklist. Read-only; exceptions are evaluated by the same pure safety gate. */
@Service
public class WorklistService {
 private final Db db;
 private final Safety safety;
 public WorklistService(Db db,Safety safety){this.db=db;this.safety=safety;}

 private static final Map<String,String> SORTS=Map.of(
  "ward","pc.ward_code %1$s,pc.bed_code %1$s,pc.patient_ref","patient","pc.patient_label %1$s,pc.patient_ref",
  "diet","o.diet_category %1$s NULLS LAST,pc.patient_ref","meal","lm.status %1$s NULLS LAST,pc.patient_ref",
  "updated","GREATEST(pc.updated_at,COALESCE(o.updated_at,pc.updated_at),COALESCE(lm.updated_at,pc.updated_at)) %1$s,pc.patient_ref");

 public record Query(String ward,String mealStatus,String orderStatus,Boolean allergy,Boolean fasting,Boolean exception,
   String q,String sort,String dir,int page,int size){}

 private static final String BASE=
  " FROM synqoria_nutrition.patient_context pc"+
  " LEFT JOIN synqoria_nutrition.dietary_orders o ON o.patient_ref=pc.patient_ref AND o.status='APPROVED'"+
  " LEFT JOIN synqoria_nutrition.allergy_snapshots al ON al.patient_ref=pc.patient_ref"+
  " LEFT JOIN LATERAL (SELECT f.ends_at,f.reason_code FROM synqoria_nutrition.fasting_instructions f WHERE f.patient_ref=pc.patient_ref"+
  "  AND f.status='ACTIVE' AND f.starts_at<=? AND (f.ends_at IS NULL OR f.ends_at>?) ORDER BY f.ends_at DESC NULLS FIRST LIMIT 1) fa ON true"+
  " LEFT JOIN LATERAL (SELECT ms.id,ms.status,ms.meal_type,ms.meal_date,ms.hold_reason,ms.updated_at FROM synqoria_nutrition.meal_services ms"+
  "  WHERE ms.patient_ref=pc.patient_ref AND ms.status<>'CANCELLED' ORDER BY ms.meal_date DESC,ms.updated_at DESC LIMIT 1) lm ON true"+
  " LEFT JOIN LATERAL (SELECT count(*) AS n FROM synqoria_nutrition.delivery_incidents di JOIN synqoria_nutrition.meal_services m2 ON m2.id=di.meal_service_id"+
  "  WHERE m2.patient_ref=pc.patient_ref AND di.status<>'RESOLVED') inc ON true";

 public Map<String,Object> list(Actor a,Query q){
  a.require(Perm.WORKLIST_READ);
  if(q.page()<0||q.size()<1||q.size()>100)throw new Violation("invalid_pagination");
  String sortKey=q.sort()==null?"ward":q.sort();
  if(!SORTS.containsKey(sortKey))throw new Violation("invalid_sort");
  String dir="desc".equalsIgnoreCase(q.dir())?"DESC":"ASC";
  if(q.dir()!=null&&!q.dir().equalsIgnoreCase("asc")&&!q.dir().equalsIgnoreCase("desc"))throw new Violation("invalid_sort");
  if(q.mealStatus()!=null&&!List.of("PLANNED","PREPARED","DISPATCHED","DELIVERED","ACKNOWLEDGED","HELD").contains(q.mealStatus()))throw new Violation("invalid_filter");
  if(q.orderStatus()!=null&&!"NONE".equals(q.orderStatus()))Domain.parse(OrderStatus.class,q.orderStatus(),"filter");
  if(q.q()!=null&&q.q().length()>80)throw new Violation("invalid_filter");
  Instant now=db.clock.instant();
  java.sql.Timestamp t=java.sql.Timestamp.from(now);
  long staleBefore=db.maxAllergyAge.toSeconds();
  // SQL mirror of the domain gate, for filtering/paging only. Row-level codes come from Domain.evaluate.
  String exc="(o.id IS NULL OR al.patient_ref IS NULL OR al.verified_at < ?::timestamptz - make_interval(secs => ?) "+
   "OR (o.reconciled_allergy_version IS NOT NULL AND al.source_version > o.reconciled_allergy_version) "+
   "OR fa.reason_code IS NOT NULL OR COALESCE(lm.status='HELD',false) OR inc.n > 0 "+
   "OR COALESCE(NOT (al.allergen_codes <@ o.excluded_allergens),false))";
  return db.scoped(a,()->{
   List<Object> args=new ArrayList<>(List.of(t,t));
   StringBuilder w=new StringBuilder(" WHERE pc.admitted");
   if(q.ward()!=null){w.append(" AND pc.ward_code=?");args.add(q.ward());}
   if(q.mealStatus()!=null){w.append(" AND lm.status=?");args.add(q.mealStatus());}
   if("NONE".equals(q.orderStatus()))w.append(" AND o.id IS NULL");else if(q.orderStatus()!=null)w.append(" AND o.status=?");
   if(q.orderStatus()!=null&&!"NONE".equals(q.orderStatus()))args.add(q.orderStatus());
   if(q.allergy()!=null)w.append(q.allergy()?" AND cardinality(al.allergen_codes)>0":" AND (al.patient_ref IS NULL OR cardinality(al.allergen_codes)=0)");
   if(q.fasting()!=null)w.append(q.fasting()?" AND fa.reason_code IS NOT NULL":" AND fa.reason_code IS NULL");
   if(q.exception()!=null){
    w.append(q.exception()?" AND ":" AND NOT ").append(exc);args.add(t);args.add((double)staleBefore);
   }
   if(q.q()!=null&&!q.q().isBlank()){w.append(" AND (pc.patient_label ILIKE ? OR pc.bed_code ILIKE ?)");
    String like="%"+q.q().replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";args.add(like);args.add(like);}
   long total=db.jdbc.queryForObject("SELECT count(*)"+BASE+w,Long.class,args.toArray());
   List<Object> pageArgs=new ArrayList<>(args);pageArgs.add(t);pageArgs.add((double)staleBefore);pageArgs.add(q.size());pageArgs.add((long)q.page()*q.size());
   // the exception-expression placeholders inside SELECT precede the WHERE ones, so build args in order
   List<Object> ordered=new ArrayList<>();ordered.add(t);ordered.add((double)staleBefore);ordered.addAll(args);ordered.add(q.size());ordered.add((long)q.page()*q.size());
   List<Map<String,Object>> rows=db.rows("SELECT pc.patient_ref,pc.patient_label,pc.ward_code,pc.bed_code,pc.identity_version,"+
    "o.id AS order_id,o.status AS order_status,o.diet_category,o.texture,o.restrictions,o.excluded_allergens,o.meal_timing,o.special_instructions,"+
    "o.version AS order_version,o.approved_at,o.effective_to,o.reconciled_allergy_version,"+
    "al.allergen_codes,al.no_known_allergies,al.source_version AS allergy_version,al.verified_at AS allergy_verified_at,"+
    "fa.reason_code AS fasting_reason,fa.ends_at AS fasting_until,"+
    "lm.id AS meal_id,lm.status AS meal_status,lm.meal_type,lm.meal_date,lm.hold_reason,lm.updated_at AS meal_updated_at,inc.n AS open_incidents,"+
    exc+" AS has_exception,count(*) OVER() AS total_count"+BASE+w+
    " ORDER BY "+String.format(SORTS.get(sortKey),dir)+" LIMIT ? OFFSET ?",ordered.toArray());
   for(Map<String,Object> r:rows)r.put("exceptions",codesFor(a,r,now));
   return db.pageOf(rows,q.page(),q.size(),total);
  });
 }

 @SuppressWarnings("unchecked")
 private List<String> codesFor(Actor a,Map<String,Object> r,Instant now){
  UUID patient=(UUID)r.get("patientRef");
  List<String> out=new ArrayList<>();
  if(r.get("orderId")==null)out.add("DIET_UNKNOWN");
  else{
   List<String> c=safety.check(a,patient,(UUID)r.get("orderId"),null,LocalDate.ofInstant(now,db.zone),MealType.LUNCH,true);
   c.stream().filter(x->!x.equals("FASTING_ACTIVE")).forEach(out::add);
  }
  if(r.get("fastingReason")!=null)out.add("FASTING_ACTIVE");
  if("HELD".equals(r.get("mealStatus"))&&r.get("holdReason")!=null&&!out.contains((String)r.get("holdReason")))out.add((String)r.get("holdReason"));
  if(r.get("openIncidents") instanceof Number n&&n.longValue()>0)out.add("OPEN_INCIDENT");
  return out;
 }

 public Map<String,Object> detail(Actor a,UUID patient){
  a.require(Perm.WORKLIST_READ);
  return db.scoped(a,()->{
   Map<String,Object> pc=db.one("SELECT patient_ref,patient_label,ward_code,bed_code,admitted,identity_version,verified_at FROM synqoria_nutrition.patient_context WHERE patient_ref=?",patient);
   Map<String,Object> out=new LinkedHashMap<>(pc);
   out.put("orders",db.rows("SELECT * FROM synqoria_nutrition.dietary_orders WHERE patient_ref=? ORDER BY created_at DESC,id LIMIT 20",patient));
   out.put("allergies",db.rows("SELECT allergen_codes,no_known_allergies,source_version,verified_at FROM synqoria_nutrition.allergy_snapshots WHERE patient_ref=?",patient));
   out.put("fasting",db.rows("SELECT instruction_ref,status,reason_code,starts_at,ends_at,source_version FROM synqoria_nutrition.fasting_instructions WHERE patient_ref=? ORDER BY starts_at DESC LIMIT 20",patient));
   out.put("meals",db.rows("SELECT id,meal_date,meal_type,status,hold_reason,diet_category,texture,updated_at,version FROM synqoria_nutrition.meal_services WHERE patient_ref=? ORDER BY meal_date DESC,updated_at DESC LIMIT 30",patient));
   return out;
  });
 }
}
