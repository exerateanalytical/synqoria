package org.synqoria.reliability;
public final class CommandPreconditionTest {
 static int n=0;static void yes(boolean b){if(!b)throw new AssertionError();n++;}
 public static void main(String[] args){
  yes("\"v1\"".equals(CommandPrecondition.etag(1)));
  yes(CommandPrecondition.check("\"v8\"",8)==CommandPrecondition.Decision.PROCEED);
  yes(CommandPrecondition.check(null,8)==CommandPrecondition.Decision.PRECONDITION_REQUIRED);
  yes(CommandPrecondition.check("",8)==CommandPrecondition.Decision.PRECONDITION_REQUIRED);
  yes(CommandPrecondition.check("\"v7\"",8)==CommandPrecondition.Decision.STALE);
  for(String bad:new String[]{"*","W/\"v8\"","\"v0\"","\"v8\",\"v9\"","\"v8\" ","\"v99999999999999999999\"","\"v-1\""})yes(CommandPrecondition.check(bad,8)==CommandPrecondition.Decision.MALFORMED);
  yes(CommandPrecondition.httpStatus(CommandPrecondition.Decision.STALE)==412);
  yes(CommandPrecondition.httpStatus(CommandPrecondition.Decision.PRECONDITION_REQUIRED)==428);
  System.out.println("PASS: "+n+" precondition assertions");
 }
}
