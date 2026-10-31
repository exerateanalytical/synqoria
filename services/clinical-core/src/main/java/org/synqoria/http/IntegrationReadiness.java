package org.synqoria.http;
import java.util.*;
import java.util.function.BooleanSupplier;
/** Health-ready is true only when all configured essential dependencies pass fresh checks. */
public final class IntegrationReadiness {
 private final List<BooleanSupplier> checks;
 public IntegrationReadiness(List<BooleanSupplier> checks){
  if(checks==null||checks.size()<3||checks.stream().anyMatch(Objects::isNull))throw new IllegalArgumentException("Identity, database and audit checks required");
  this.checks=List.copyOf(checks);
 }
 public boolean ready(){for(var check:checks){try{if(!check.getAsBoolean())return false;}catch(Exception e){return false;}}return true;}
 public static IntegrationReadiness unavailable(){return new IntegrationReadiness(List.of(()->false,()->false,()->false));}
}
