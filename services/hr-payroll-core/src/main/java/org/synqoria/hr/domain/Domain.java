package org.synqoria.hr.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

/** Pure M24 domain: permissions, state machines, exact-decimal payroll engine, roster and leave rules. No I/O. */
public final class Domain {
 private Domain(){}

 public static class Violation extends RuntimeException {
  private final List<String> details;
  public Violation(String code){this(code,List.of());}
  public Violation(String code,List<String> details){super(code,null,false,false);this.details=List.copyOf(details);}
  public String code(){return getMessage();}
  public List<String> details(){return details;}
 }

 public static final class Perm {
  private Perm(){}
  public static final String STAFF_READ="m24:staff_read", STAFF_MANAGE="m24:staff_manage", STAFF_APPROVE="m24:staff_approve", CONFIDENTIAL_READ="m24:confidential_read",
   COMP_READ="m24:comp_read", CONTRACT_MANAGE="m24:contract_manage", CONTRACT_APPROVE="m24:contract_approve",
   ROSTER_READ="m24:roster_read", ROSTER_MANAGE="m24:roster_manage", ROSTER_REVIEW="m24:roster_review", OVERTIME_AUTHORIZE="m24:overtime_authorize",
   ATTENDANCE_CAPTURE="m24:attendance_capture", ATTENDANCE_REVIEW="m24:attendance_review", LEAVE_REQUEST="m24:leave_request", LEAVE_DECIDE="m24:leave_decide", LEAVE_READ="m24:leave_read",
   RULES_MANAGE="m24:rules_manage", RULES_APPROVE="m24:rules_approve", PAYROLL_READ="m24:payroll_read", PAYROLL_PREPARE="m24:payroll_prepare", PAYROLL_APPROVE="m24:payroll_approve",
   PAYMENT_INSTRUCT="m24:payment_instruct", PAYMENT_CONFIRM="m24:payment_confirm", RECONCILE="m24:reconcile", EXPORT="m24:export";
  public static final Set<String> ALL=Set.of(STAFF_READ,STAFF_MANAGE,STAFF_APPROVE,CONFIDENTIAL_READ,COMP_READ,CONTRACT_MANAGE,CONTRACT_APPROVE,ROSTER_READ,ROSTER_MANAGE,
   ROSTER_REVIEW,OVERTIME_AUTHORIZE,ATTENDANCE_CAPTURE,ATTENDANCE_REVIEW,LEAVE_REQUEST,LEAVE_DECIDE,LEAVE_READ,RULES_MANAGE,RULES_APPROVE,PAYROLL_READ,PAYROLL_PREPARE,
   PAYROLL_APPROVE,PAYMENT_INSTRUCT,PAYMENT_CONFIRM,RECONCILE,EXPORT);
  /** Checker-side permissions: never combined with the matching maker permission in one role by policy (also enforced per record). */
  public static final Set<String> CHECKER=Set.of(STAFF_APPROVE,CONTRACT_APPROVE,ROSTER_REVIEW,LEAVE_DECIDE,RULES_APPROVE,PAYROLL_APPROVE,PAYMENT_CONFIRM,ATTENDANCE_REVIEW);
 }
 public record Actor(UUID id,UUID cell,UUID facility,Set<String> grants){
  public Actor{grants=Set.copyOf(grants);}
  public void require(String p){if(!grants.contains(p))throw new Violation("permission_denied");}
  public boolean has(String p){return grants.contains(p);}
 }

 public enum StaffStatus{
  ONBOARDING,ACTIVE,SUSPENDED,TERMINATED;
  public boolean canApply(ChangeKind k){
   return switch(k){
    case ACTIVATE->this==ONBOARDING;
    case SUSPEND->this==ACTIVE;
    case REINSTATE->this==SUSPENDED;
    case TRANSFER->this==ACTIVE||this==SUSPENDED;
    case TERMINATE->this==ACTIVE||this==SUSPENDED||this==ONBOARDING;
    case REHIRE->this==TERMINATED;
   };
  }
  public StaffStatus after(ChangeKind k){
   return switch(k){case ACTIVATE,REINSTATE,REHIRE->ACTIVE;case SUSPEND->SUSPENDED;case TERMINATE->TERMINATED;case TRANSFER->this;};
  }
 }
 public enum ChangeKind{ACTIVATE,SUSPEND,REINSTATE,TRANSFER,TERMINATE,REHIRE}

 private static final Pattern CODE=Pattern.compile("[A-Z0-9_-]{1,32}");
 public static boolean validCode(String c){return c!=null&&CODE.matcher(c).matches();}
 public static <E extends Enum<E>> E parse(Class<E> t,String v,String field){
  try{return Enum.valueOf(t,v);}catch(RuntimeException e){throw new Violation("invalid_"+field);}
 }
 public static String sha256(String s){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}
  catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }

 /** Effective-date rule for a lifecycle change. */
 public static void checkEffectiveDate(ChangeKind kind,LocalDate effective,LocalDate hireDate,LocalDate lastEffective,LocalDate terminationDate,LocalDate today){
  if(effective==null)throw new Violation("invalid_effective_date");
  if(effective.isBefore(lastEffective))throw new Violation("effective_date_conflict",List.of("BEFORE_LAST_CHANGE"));
  if(kind==ChangeKind.REHIRE){
   if(terminationDate==null||!effective.isAfter(terminationDate))throw new Violation("effective_date_conflict",List.of("REHIRE_NOT_AFTER_TERMINATION"));
  }else if(effective.isBefore(hireDate))throw new Violation("effective_date_conflict",List.of("BEFORE_HIRE"));
  if(effective.isAfter(today.plusDays(366)))throw new Violation("effective_date_conflict",List.of("TOO_FAR_AHEAD"));
 }

 // ================================================================== exact-decimal payroll engine
 public record RuleSetSpec(String currency,RoundingMode mode,int scale,BigDecimal monthlyHours,List<Map<String,Object>> components){}

 private static final Set<String> EARNING_TYPES=Set.of("FIXED","PERCENT_OF_BASIC","OVERTIME");
 private static final Set<String> DEDUCTION_TYPES=Set.of("FIXED","PERCENT_OF_BASIC","PERCENT_OF_GROSS","BRACKETS");

 /** Money and rates in rule JSON MUST be decimal strings. A JSON number could carry binary floating-point error. */
 static BigDecimal dec(Object o){
  if(!(o instanceof String s)||!s.matches("-?\\d{1,15}(\\.\\d{1,10})?"))throw new Violation("invalid_decimal");
  return new BigDecimal(s);
 }
 @SuppressWarnings("unchecked")
 private static Map<String,Object> mapOf(Object o){return o instanceof Map<?,?> m?(Map<String,Object>)m:null;}

 /** Structural validation of a country/employer rule set. Nothing here knows any statutory rate. */
 public static List<String> validateRules(Map<String,Object> rules,String currency){
  List<String> e=new ArrayList<>();
  if(rules==null)return List.of("MISSING_RULES");
  if(!(rules.get("schemaVersion") instanceof Number n)||n.intValue()!=1)e.add("UNSUPPORTED_SCHEMA_VERSION");
  if(!currency.equals(rules.get("currency")))e.add("CURRENCY_MISMATCH");
  Map<String,Object> r=mapOf(rules.get("rounding"));
  if(r==null||!List.of("HALF_UP","HALF_EVEN","DOWN","UP").contains(r.get("mode"))||!(r.get("scale") instanceof Number sc)||sc.intValue()<0||sc.intValue()>4)e.add("INVALID_ROUNDING");
  try{if(dec(rules.get("monthlyHours")).signum()<=0)e.add("INVALID_MONTHLY_HOURS");}catch(Violation v){e.add("INVALID_MONTHLY_HOURS");}
  if(!(rules.get("components") instanceof List<?> comps)||comps.isEmpty()||comps.size()>100){e.add("INVALID_COMPONENTS");return e;}
  Set<String> codes=new HashSet<>();
  for(Object o:comps){
   Map<String,Object> c=mapOf(o);
   if(c==null||!(c.get("code") instanceof String code)||!validCode(code)){e.add("INVALID_COMPONENT_CODE");continue;}
   if(!codes.add(code))e.add("DUPLICATE_COMPONENT:"+code);
   String kind=String.valueOf(c.get("kind")),type=String.valueOf(c.get("type"));
   boolean earning=kind.equals("EARNING"),deduction=kind.equals("DEDUCTION");
   if(!earning&&!deduction){e.add("INVALID_KIND:"+code);continue;}
   if(!(earning?EARNING_TYPES:DEDUCTION_TYPES).contains(type)){e.add("INVALID_TYPE:"+code);continue;}
   try{
    switch(type){
     case "FIXED"->{if(dec(c.get("amount")).signum()<0)e.add("NEGATIVE_AMOUNT:"+code);}
     case "PERCENT_OF_BASIC","PERCENT_OF_GROSS"->{BigDecimal rate=dec(c.get("rate"));if(rate.signum()<0||rate.compareTo(BigDecimal.ONE)>0)e.add("RATE_OUT_OF_RANGE:"+code);
      if(c.containsKey("cap")&&dec(c.get("cap")).signum()<0)e.add("NEGATIVE_CAP:"+code);}
     case "OVERTIME"->{if(dec(c.get("multiplier")).compareTo(BigDecimal.ONE)<0)e.add("MULTIPLIER_BELOW_ONE:"+code);}
     case "BRACKETS"->{
      if(!(c.get("brackets") instanceof List<?> bl)||bl.isEmpty()||bl.size()>20){e.add("INVALID_BRACKETS:"+code);}
      else{BigDecimal prev=BigDecimal.ZERO;for(int i=0;i<bl.size();i++){Map<String,Object> b=mapOf(bl.get(i));
       if(b==null){e.add("INVALID_BRACKETS:"+code);break;}
       BigDecimal rate=dec(b.get("rate"));if(rate.signum()<0||rate.compareTo(BigDecimal.ONE)>0)e.add("RATE_OUT_OF_RANGE:"+code);
       if(b.get("upTo")==null){if(i!=bl.size()-1)e.add("OPEN_BRACKET_NOT_LAST:"+code);}
       else{BigDecimal up=dec(b.get("upTo"));if(up.compareTo(prev)<=0)e.add("BRACKETS_NOT_ASCENDING:"+code);prev=up;}}}
     }
     default->{}
    }
   }catch(Violation v){e.add("INVALID_DECIMAL:"+code);}
  }
  Collections.sort(e);
  return e;
 }
 public static RuleSetSpec spec(Map<String,Object> rules){
  Map<String,Object> r=mapOf(rules.get("rounding"));
  @SuppressWarnings("unchecked") List<Map<String,Object>> comps=(List<Map<String,Object>>)rules.get("components");
  return new RuleSetSpec((String)rules.get("currency"),RoundingMode.valueOf((String)r.get("mode")),((Number)r.get("scale")).intValue(),dec(rules.get("monthlyHours")),comps);
 }

 public record Adjustment(String kind,BigDecimal amount){}
 public record PeriodInputs(BigDecimal hoursWorked,BigDecimal overtimeHours,BigDecimal unpaidDays,BigDecimal workingDays,List<Adjustment> adjustments){}
 public record LineItem(String code,String kind,BigDecimal amount){}
 public record PayLine(String currency,BigDecimal gross,BigDecimal totalDeductions,BigDecimal net,List<LineItem> items){}

 /**
  * Exact decimal calculation. Every line is rounded once with the rule set's mode/scale; totals are sums of rounded lines,
  * so a payslip always adds up. Deductions are computed on gross (or basic) as the rule set declares.
  */
 public static PayLine calculate(RuleSetSpec rs,String contractCurrency,String payBasis,BigDecimal base,PeriodInputs in){
  if(!rs.currency().equals(contractCurrency))throw new Violation("currency_mismatch");
  List<LineItem> items=new ArrayList<>();
  BigDecimal hourly=payBasis.equals("HOURLY")?base:base.divide(rs.monthlyHours(),20,RoundingMode.HALF_EVEN);
  BigDecimal basic=payBasis.equals("HOURLY")?r(rs,base.multiply(in.hoursWorked())):r(rs,base);
  BigDecimal gross=BigDecimal.ZERO;
  items.add(new LineItem("BASIC","EARNING",basic));gross=gross.add(basic);
  for(Map<String,Object> c:rs.components()){
   if(!"EARNING".equals(c.get("kind")))continue;
   BigDecimal amt=switch((String)c.get("type")){
    case "FIXED"->r(rs,dec(c.get("amount")));
    case "PERCENT_OF_BASIC"->r(rs,basic.multiply(dec(c.get("rate"))));
    case "OVERTIME"->r(rs,hourly.multiply(in.overtimeHours()).multiply(dec(c.get("multiplier"))));
    default->throw new IllegalStateException();
   };
   if(amt.signum()!=0){items.add(new LineItem((String)c.get("code"),"EARNING",amt));gross=gross.add(amt);}
  }
  BigDecimal deductions=BigDecimal.ZERO;
  if(payBasis.equals("MONTHLY")&&in.unpaidDays().signum()>0){
   if(in.workingDays().signum()<=0)throw new Violation("invalid_working_days");
   BigDecimal d=r(rs,base.multiply(in.unpaidDays()).divide(in.workingDays(),20,RoundingMode.HALF_EVEN));
   if(d.compareTo(basic)>0)d=basic;
   items.add(new LineItem("UNPAID_LEAVE","DEDUCTION",d));deductions=deductions.add(d);
  }
  for(Adjustment a:in.adjustments()){
   BigDecimal amt=r(rs,a.amount());
   if(a.kind().equals("DEDUCTION")){items.add(new LineItem("ADJ_DEDUCTION","DEDUCTION",amt));deductions=deductions.add(amt);}
   else{items.add(new LineItem("ADJ_"+a.kind(),"EARNING",amt));gross=gross.add(amt);}
  }
  for(Map<String,Object> c:rs.components()){
   if(!"DEDUCTION".equals(c.get("kind")))continue;
   BigDecimal amt=switch((String)c.get("type")){
    case "FIXED"->r(rs,dec(c.get("amount")));
    case "PERCENT_OF_BASIC"->capped(rs,basic.multiply(dec(c.get("rate"))),c);
    case "PERCENT_OF_GROSS"->capped(rs,gross.multiply(dec(c.get("rate"))),c);
    case "BRACKETS"->r(rs,brackets(gross,c));
    default->throw new IllegalStateException();
   };
   if(amt.signum()!=0){items.add(new LineItem((String)c.get("code"),"DEDUCTION",amt));deductions=deductions.add(amt);}
  }
  BigDecimal net=gross.subtract(deductions);
  if(net.signum()<0)throw new Violation("negative_net");
  return new PayLine(contractCurrency,gross,deductions,net,items);
 }
 private static BigDecimal r(RuleSetSpec rs,BigDecimal v){return v.setScale(rs.scale(),rs.mode());}
 private static BigDecimal capped(RuleSetSpec rs,BigDecimal v,Map<String,Object> c){
  BigDecimal x=r(rs,v);
  if(c.containsKey("cap")){BigDecimal cap=r(rs,dec(c.get("cap")));if(x.compareTo(cap)>0)x=cap;}
  return x;
 }
 @SuppressWarnings("unchecked")
 private static BigDecimal brackets(BigDecimal gross,Map<String,Object> c){
  BigDecimal total=BigDecimal.ZERO,lower=BigDecimal.ZERO;
  for(Map<String,Object> b:(List<Map<String,Object>>)c.get("brackets")){
   BigDecimal upper=b.get("upTo")==null?null:dec(b.get("upTo"));
   BigDecimal top=upper==null||gross.compareTo(upper)<0?gross:upper;
   if(top.compareTo(lower)>0)total=total.add(top.subtract(lower).multiply(dec(b.get("rate"))));
   if(upper==null||gross.compareTo(upper)<=0)break;
   lower=upper;
  }
  return total;
 }
 /** Reproducible fingerprint of a calculated batch: changing any staff amount, the period or the rule set changes it. */
 public static String batchHash(UUID period,String rulesHash,List<String[]> staffGrossNetContract){
  List<String[]> sorted=new ArrayList<>(staffGrossNetContract);
  sorted.sort(Comparator.comparing(a->a[0]));
  StringBuilder b=new StringBuilder(period+"|"+rulesHash);
  for(String[] l:sorted)b.append('\n').append(String.join("|",l));
  return sha256(b.toString());
 }

 // ================================================================== rostering
 public record Shift(UUID staff,Instant start,Instant end){}
 /** OVERLAP:<staff> and REST:<staff> problems; vacancies (staff == null) are ignored. */
 public static List<String> rosterProblems(Collection<Shift> shifts,Duration minRest){
  Map<UUID,List<Shift>> by=new HashMap<>();
  for(Shift s:shifts)if(s.staff()!=null)by.computeIfAbsent(s.staff(),k->new ArrayList<>()).add(s);
  TreeSet<String> out=new TreeSet<>();
  for(var e:by.entrySet()){
   List<Shift> l=e.getValue();l.sort(Comparator.comparing(Shift::start));
   for(int i=1;i<l.size();i++){
    Shift a=l.get(i-1),b=l.get(i);
    if(b.start().isBefore(a.end()))out.add("OVERLAP:"+e.getKey());
    else if(Duration.between(a.end(),b.start()).compareTo(minRest)<0)out.add("REST:"+e.getKey());
   }
  }
  return List.copyOf(out);
 }
 /** Coverage: for each date and required template, assigned (non-vacant) staff must reach the minimum. */
 public static List<String> coverageGaps(Map<UUID,Integer> minByTemplate,Collection<Object[]> assigned,LocalDate from,LocalDate to){
  Map<String,Integer> count=new HashMap<>();
  for(Object[] a:assigned)count.merge(a[0]+"@"+a[1],1,Integer::sum);
  TreeSet<String> gaps=new TreeSet<>();
  for(LocalDate d=from;!d.isAfter(to);d=d.plusDays(1))
   for(var req:minByTemplate.entrySet())
    if(count.getOrDefault(req.getKey()+"@"+d,0)<req.getValue())gaps.add("GAP:"+req.getKey()+":"+d);
  return List.copyOf(gaps);
 }

 // ================================================================== leave
 /** Whole completed months since hire x monthly accrual, capped. */
 public static BigDecimal accrued(LocalDate hire,LocalDate asOf,BigDecimal perMonth,BigDecimal max){
  if(asOf.isBefore(hire))return BigDecimal.ZERO;
  long months=ChronoUnit.MONTHS.between(hire,asOf);
  BigDecimal v=perMonth.multiply(BigDecimal.valueOf(months));
  return v.compareTo(max)>0?max:v;
 }
 /** Counts working days (Mon-Fri, excluding listed holidays) in an inclusive range. */
 public static int workingDays(LocalDate from,LocalDate to,Set<LocalDate> holidays){
  int n=0;
  for(LocalDate d=from;!d.isAfter(to);d=d.plusDays(1)){
   var dow=d.getDayOfWeek();
   if(dow!=java.time.DayOfWeek.SATURDAY&&dow!=java.time.DayOfWeek.SUNDAY&&!holidays.contains(d))n++;
  }
  return n;
 }
}
