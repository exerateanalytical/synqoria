package org.synqoria.registration;
import java.sql.*;
/** P05-B0036: JDBC guard, not proof of PostgreSQL RLS or DB-role privileges. */
public final class ReviewTransactionFence {
 private ReviewTransactionFence(){}
 public static boolean eligible(Connection c){
  if(c==null)return false;
  try{return !c.isClosed()&&!c.getAutoCommit()&&!c.isReadOnly()&&c.getTransactionIsolation()==Connection.TRANSACTION_SERIALIZABLE;}
  catch(SQLException|RuntimeException e){return false;}
 }
}
