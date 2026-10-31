package org.synqoria.registration;
import java.util.List;
public final class V190ChainVerifierTest {
 public static void main(String[] a){String zero=ReviewAuditChain.genesis(),binding="a".repeat(64);
  String first=ReviewAuditChain.append(zero,binding,1,"REVIEW_APPROVED");String second=ReviewAuditChain.append(first,binding,2,"REVIEW_REVOKED");
  var x=new ReviewChainVerifier.Link(1,"REVIEW_APPROVED",binding,zero,first);
  var y=new ReviewChainVerifier.Link(2,"REVIEW_REVOKED",binding,first,second);
  assert ReviewChainVerifier.verify(List.of(x,y));assert ReviewChainVerifier.verify(List.of(x));assert !ReviewChainVerifier.verify(List.of());
  assert !ReviewChainVerifier.verify(List.of(y,x));assert !ReviewChainVerifier.verify(List.of(x,x));
  assert !ReviewChainVerifier.verify(List.of(x,new ReviewChainVerifier.Link(2,"REVIEW_REVOKED",binding,first,"f".repeat(64))));
  assert !ReviewChainVerifier.verify(List.of(x,new ReviewChainVerifier.Link(2,"REVIEW_REVOKED",binding,zero,second)));
  String repeated=ReviewAuditChain.append(first,binding,2,"REVIEW_APPROVED");
  assert !ReviewChainVerifier.verify(List.of(x,new ReviewChainVerifier.Link(2,"REVIEW_APPROVED",binding,first,repeated)));
  assert !ReviewChainVerifier.verify(java.util.Arrays.asList(new ReviewChainVerifier.Link(1,null,binding,zero,first)));
  System.out.println("PASS P05-B0031 9 audit-chain checks");
 }
}
