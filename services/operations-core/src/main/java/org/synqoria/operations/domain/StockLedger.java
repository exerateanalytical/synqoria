package org.synqoria.operations.domain;

import java.util.Objects;

/**
 * M22 authoritative stock arithmetic, no floating quantities, no medication
 * dispensing. Every move must be atomically persisted with a stock_movements
 * audit entry and cell/facility scope by the JDBC service.
 */
public final class StockLedger {
 private StockLedger(){}
 public enum Movement {RECEIVE,RESERVE,ISSUE,RELEASE,RETURN,ADJUST}
 public record Balance(long onHand,long reserved,long version) {
  public Balance {
   if(onHand<0||reserved<0||reserved>onHand||version<0)
    throw new WorkflowViolation("invalid_stock_balance");
  }
  public long available(){return onHand-reserved;}
 }
 public static Balance apply(Balance current,Movement movement,long quantity,
                             boolean trustedReceipt,boolean trustedReturn,
                             boolean makerCheckerApproved){
  Objects.requireNonNull(current);Objects.requireNonNull(movement);
  if(quantity<1||quantity>1_000_000)throw new WorkflowViolation("invalid_stock_quantity");
  long on=current.onHand(),reserved=current.reserved();
  try{
   switch(movement){
    case RECEIVE -> {
     if(!trustedReceipt)throw new WorkflowViolation("trusted_receipt_required");
     on=Math.addExact(on,quantity);
    }
    case RESERVE -> {
     if(current.available()<quantity)throw new WorkflowViolation("insufficient_available_stock");
     reserved=Math.addExact(reserved,quantity);
    }
    case ISSUE -> {
     if(reserved<quantity)throw new WorkflowViolation("unreserved_issue_denied");
     on=Math.subtractExact(on,quantity);
     reserved=Math.subtractExact(reserved,quantity);
    }
    case RELEASE -> {
     if(reserved<quantity)throw new WorkflowViolation("overrelease_denied");
     reserved=Math.subtractExact(reserved,quantity);
    }
    case RETURN -> {
     if(!trustedReturn)throw new WorkflowViolation("return_provenance_required");
     on=Math.addExact(on,quantity);
    }
    case ADJUST -> throw new WorkflowViolation("signed_adjustment_review_required");
   }
   return new Balance(on,reserved,Math.addExact(current.version(),1));
  }catch(ArithmeticException e){throw new WorkflowViolation("stock_arithmetic_overflow");}
 }
}
