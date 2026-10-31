package org.synqoria.facility;
import java.util.*;
public class LocationMovePolicyTest {
 static int n;static void yes(boolean b){if(!b)throw new AssertionError();n++;}
 public static void main(String[] args){
  var t=UUID.randomUUID();var f=UUID.randomUUID();var d=UUID.randomUUID();var d2=UUID.randomUUID();var l=UUID.randomUUID();
  var old=new LocationMovePolicy.Location(t,f,d,l,3,true);var next=new LocationMovePolicy.Destination(t,f,d2,true);
  yes(LocationMovePolicy.move(old,next,3)==LocationMovePolicy.Decision.ALLOW_REVISION_UPDATE);
  yes(LocationMovePolicy.move(old,next,2)==LocationMovePolicy.Decision.PRECONDITION_FAILED);
  yes(LocationMovePolicy.move(old,new LocationMovePolicy.Destination(UUID.randomUUID(),f,d2,true),3)==LocationMovePolicy.Decision.INVALID_SCOPE);
  yes(LocationMovePolicy.move(old,new LocationMovePolicy.Destination(t,UUID.randomUUID(),d2,true),3)==LocationMovePolicy.Decision.INVALID_SCOPE);
  yes(LocationMovePolicy.move(old,new LocationMovePolicy.Destination(t,f,d2,false),3)==LocationMovePolicy.Decision.INVALID_STATE);
  yes(LocationMovePolicy.move(old,new LocationMovePolicy.Destination(t,f,d,true),3)==LocationMovePolicy.Decision.INVALID_STATE);
  yes(LocationMovePolicy.move(new LocationMovePolicy.Location(t,f,d,l,Long.MAX_VALUE,true),next,Long.MAX_VALUE)==LocationMovePolicy.Decision.PRECONDITION_FAILED);
  System.out.println("PASS: "+n+" directory move assertions");
 }
}
