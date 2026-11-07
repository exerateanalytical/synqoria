package org.synqoria.fleet;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Clock;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.synqoria.fleet.domain.Domain.*;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cell/facility-scoped transaction boundary. One physical JDBC transaction carries scope settings,
 * the idempotency record, state change, audit event and outbox event. The runtime principal must be
 * the restricted non-owner, non-BYPASSRLS role, verified on every transaction.
 */
@Component
public class Db {
 /** HTTP-shaped result so that committed rejections replay exactly like successes. */
 public record Reply(int status,Object body){}

 final JdbcTemplate jdbc;
 final JsonMapper json;
 final TransactionTemplate tx;
 final Clock clock;
 final ZoneId zone;
 private final String expectedRole;

 public Db(JdbcTemplate jdbc,JsonMapper json,PlatformTransactionManager manager,Clock clock,
   @Value("${synqoria.fleet.db-runtime-role}")String role,
   @Value("${synqoria.fleet.facility-zone:UTC}")String zone){
  if(!"synqoria_fleet_runtime".equals(role))throw new IllegalStateException("fleet_db_role_must_be_least_privileged");
  this.jdbc=jdbc;this.json=json;this.tx=new TransactionTemplate(manager);this.clock=clock;this.expectedRole=role;
  this.zone=ZoneId.of(zone);
 }

 public <T> T scoped(Actor actor,Supplier<T> op){
  return Objects.requireNonNull(tx.execute(t->{
   Boolean safe=jdbc.queryForObject("SELECT rolcanlogin AND NOT rolinherit AND NOT rolsuper AND NOT rolbypassrls "+
    "AND NOT rolcreaterole AND NOT rolcreatedb AND NOT rolreplication FROM pg_roles WHERE rolname=current_user",Boolean.class);
   String principal=jdbc.queryForObject("SELECT current_user",String.class);
   if(!expectedRole.equals(principal)||!Boolean.TRUE.equals(safe))throw new Violation("database_role_denied");
   jdbc.queryForObject("SELECT set_config('synqoria.cell',?,true)",String.class,actor.cell().toString());
   jdbc.queryForObject("SELECT set_config('synqoria.facility',?,true)",String.class,actor.facility().toString());
   jdbc.queryForObject("SELECT set_config('synqoria.tenants',?,true)",String.class,actor.tenants().isEmpty()?"*":String.join(",",actor.tenants().stream().map(Object::toString).sorted().toList()));
   return op.get();
  }));
 }

 String encode(Object o){return json.writeValueAsString(o);}
 Object decode(String s){return json.readValue(s,Object.class);}
 static String sha(String text){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
  catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }

 /**
  * Idempotent command. Same key + same request replays the stored reply (including committed
  * rejections); same key + different request is an idempotency_collision.
  */
 public Reply command(Actor actor,UUID commandId,String kind,Object request,Supplier<Reply> body){
  if(commandId==null)throw new Violation("idempotency_key_required");
  String hash=sha(kind+"|"+encode(request));
  return scoped(actor,()->{
   jdbc.update("INSERT INTO synqoria_fleet.command_idempotency(cell_id,facility_id,actor_id,command_id,request_hash) "+
    "VALUES (?,?,?,?,?) ON CONFLICT DO NOTHING",actor.cell(),actor.facility(),actor.id(),commandId,hash);
   Map<String,Object> row=jdbc.queryForMap("SELECT request_hash,response::text AS response,http_status FROM "+
    "synqoria_fleet.command_idempotency WHERE cell_id=? AND facility_id=? AND actor_id=? AND command_id=? FOR UPDATE",
    actor.cell(),actor.facility(),actor.id(),commandId);
   if(!hash.equals(row.get("request_hash").toString().trim()))throw new Violation("idempotency_collision");
   if(row.get("response")!=null)return new Reply(((Number)row.get("http_status")).intValue(),decode((String)row.get("response")));
   Reply reply=body.get();
   jdbc.update("UPDATE synqoria_fleet.command_idempotency SET response=?::jsonb,http_status=? "+
    "WHERE cell_id=? AND facility_id=? AND actor_id=? AND command_id=?",encode(reply.body()),reply.status(),
    actor.cell(),actor.facility(),actor.id(),commandId);
   return reply;
  });
 }

 /** Immutable audit event plus privacy-minimal outbox event (ids, status and version only). */
 public void audit(Actor actor,UUID command,String aggregateType,UUID aggregateId,String action,
   String from,String to,long version,Map<String,Object> details){
  UUID id=UUID.randomUUID();
  String d=encode(new TreeMap<>(details));
  jdbc.update("INSERT INTO synqoria_fleet.audit_events(event_id,cell_id,facility_id,aggregate_type,aggregate_id,"+
   "action,from_status,to_status,aggregate_version,actor_id,command_id,details,digest) VALUES (?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?)",
   id,actor.cell(),actor.facility(),aggregateType,aggregateId,action,from,to,version,actor.id(),command,d,
   sha(aggregateType+aggregateId+action+version+d));
  jdbc.update("INSERT INTO synqoria_fleet.outbox(event_id,cell_id,facility_id,event_type,aggregate_id,payload) "+
   "VALUES (?,?,?,?,?,?::jsonb)",id,actor.cell(),actor.facility(),"m39."+aggregateType.toLowerCase()+"."+action.toLowerCase(),
   aggregateId,encode(Map.of("aggregateId",aggregateId.toString(),"type",aggregateType,"action",action,
   "status",to,"version",version)));
 }

 /** Generic row mapper: snake_case to camelCase, arrays to lists, jsonb parsed, scope columns hidden. */
 public Map<String,Object> row(ResultSet rs)throws SQLException{
  ResultSetMetaData md=rs.getMetaData();
  Map<String,Object> out=new LinkedHashMap<>();
  for(int i=1;i<=md.getColumnCount();i++){
   String col=md.getColumnLabel(i);
   if(col.equals("cell_id")||col.equals("facility_id")||col.equals("total_count"))continue;
   Object v;String type=md.getColumnTypeName(i);
   switch(type){
    case "_text"->{Array a=rs.getArray(i);v=a==null?null:Arrays.asList((String[])a.getArray());}
    case "jsonb"->{String s=rs.getString(i);v=s==null?null:decode(s);}
    case "timestamptz"->{var t=rs.getObject(i,java.time.OffsetDateTime.class);v=t==null?null:t.toInstant().toString();}
    case "date"->{var t=rs.getDate(i);v=t==null?null:t.toLocalDate().toString();}
    case "bpchar"->{String s=rs.getString(i);v=s==null?null:s.trim();}
    default->v=rs.getObject(i);
   }
   out.put(camel(col),v);
  }
  return out;
 }
 static String camel(String s){
  StringBuilder b=new StringBuilder();boolean up=false;
  for(char c:s.toCharArray()){if(c=='_')up=true;else{b.append(up?Character.toUpperCase(c):c);up=false;}}
  return b.toString();
 }
 public List<Map<String,Object>> rows(String sql,Object...args){return jdbc.query(sql,(rs,n)->row(rs),args);}
 public Map<String,Object> one(String sql,Object...args){
  List<Map<String,Object>> r=rows(sql,args);
  if(r.size()!=1)throw new Violation("not_found");
  return r.getFirst();
 }
 public Map<String,Object> pageOf(List<Map<String,Object>> items,int page,int size,long total){
  Map<String,Object> m=new LinkedHashMap<>();
  m.put("items",items);m.put("page",page);m.put("size",size);m.put("total",total);
  return m;
 }
 public static Map<String,Object> m(Object...kv){
  Map<String,Object> out=new LinkedHashMap<>();
  for(int i=0;i<kv.length;i+=2)out.put((String)kv[i],kv[i+1]);
  return out;
 }
 public static java.sql.Array arr(java.sql.Connection c,Collection<String> v)throws SQLException{
  return c.createArrayOf("text",v.toArray());
 }
}
