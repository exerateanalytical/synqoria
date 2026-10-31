package org.synqoria.registration;
import java.util.List;
/** P05-B0031: verifies an ordered in-memory digest chain; not durable audit custody. */
public final class ReviewChainVerifier {
 public record Link(long version,String kind,String bindingDigest,String previousDigest,String digest){}
 private ReviewChainVerifier(){}
 public static boolean verify(List<Link> links){
  if(links==null||links.isEmpty())return false;
  String previous=ReviewAuditChain.genesis();long version=1;
  ReviewEventTransition.Snapshot state=new ReviewEventTransition.Snapshot(ReviewEventTransition.State.NONE,0);
  for(Link link:links){
   if(link==null||link.kind()==null||link.version()!=version||link.digest()==null||!link.digest().matches("[0-9a-f]{64}")||!previous.equals(link.previousDigest()))return false;
   try{if(!link.digest().equals(ReviewAuditChain.append(previous,link.bindingDigest(),version,link.kind())))return false;}
   catch(IllegalArgumentException e){return false;}
   try{state=ReviewEventTransition.apply(state,version,ReviewEventTransition.Kind.valueOf(link.kind()));}catch(IllegalArgumentException e){return false;}
   previous=link.digest();version++;
  }
  return true;
 }
}
