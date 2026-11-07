package org.synqoria.operations.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class StockLedgerTest {
 @Test void receiveRequiresVerifiedGoodsReceiptAndNeverUsesNegativeQuantities(){
  var zero=new StockLedger.Balance(0,0,0);
  assertThrows(WorkflowViolation.class,()->StockLedger.apply(zero,StockLedger.Movement.RECEIVE,10,false,false,false));
  assertThrows(WorkflowViolation.class,()->StockLedger.apply(zero,StockLedger.Movement.RECEIVE,-1,true,false,false));
  assertEquals(10,StockLedger.apply(zero,StockLedger.Movement.RECEIVE,10,true,false,false).onHand());
 }
 @Test void reserveIssueAndReleaseBalancePrecisely(){
  var available=new StockLedger.Balance(100,0,1);
  var reserved=StockLedger.apply(available,StockLedger.Movement.RESERVE,50,false,false,false);
  assertEquals(100,reserved.onHand());assertEquals(50,reserved.reserved());assertEquals(50,reserved.available());
  assertThrows(WorkflowViolation.class,()->StockLedger.apply(reserved,StockLedger.Movement.RESERVE,60,false,false,false));
  var issued=StockLedger.apply(reserved,StockLedger.Movement.ISSUE,20,false,false,false);
  assertEquals(80,issued.onHand());assertEquals(30,issued.reserved());assertEquals(50,issued.available());
  var released=StockLedger.apply(issued,StockLedger.Movement.RELEASE,30,false,false,false);
  assertEquals(0,released.reserved());assertEquals(80,released.available());assertEquals(4,released.version());
  assertThrows(WorkflowViolation.class,()->StockLedger.apply(released,StockLedger.Movement.ISSUE,1,false,false,false));
 }
 @Test void unauthorizedReturnsAndAdjustmentsCannotMintHospitalStock(){
  var b=new StockLedger.Balance(18,0,4);
  assertThrows(WorkflowViolation.class,()->StockLedger.apply(b,StockLedger.Movement.RETURN,1,false,false,false));
  assertThrows(WorkflowViolation.class,()->StockLedger.apply(b,StockLedger.Movement.ADJUST,1,false,false,true));
  assertEquals(19,StockLedger.apply(b,StockLedger.Movement.RETURN,1,false,true,false).onHand());
 }
 @Test void enforceLongOverflowAndInconsistentInventoryInvariants(){
  assertThrows(WorkflowViolation.class,()->new StockLedger.Balance(-1,0,0));
  assertThrows(WorkflowViolation.class,()->new StockLedger.Balance(2,3,0));
  assertThrows(WorkflowViolation.class,()->StockLedger.apply(new StockLedger.Balance(Long.MAX_VALUE,0,1),
   StockLedger.Movement.RECEIVE,1,true,false,false));
  assertThrows(WorkflowViolation.class,()->StockLedger.apply(new StockLedger.Balance(1,0,Long.MAX_VALUE),
   StockLedger.Movement.RECEIVE,1,true,false,false));
 }
}
