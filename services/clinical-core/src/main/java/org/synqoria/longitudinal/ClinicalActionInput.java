package org.synqoria.longitudinal;

import java.time.Instant;
import java.util.*;

/** Canonicalized inputs are STRICTLY clinical facts entered by a trusted staff member, not medical advice. */
final class ClinicalActionInput {
    private ClinicalActionInput(){}
    static Map<String,String> validate(Map<String,String> values,String... permitted){
        if(values==null)throw new IllegalArgumentException("missing input");
        Set<String> allowed=Set.of(permitted);
        for(var e:values.entrySet()){
            if(!allowed.contains(e.getKey())||e.getValue()==null||e.getValue().length()>8192)
                throw new IllegalArgumentException("unknown or oversized input");
        }
        return Map.copyOf(values);
    }
    static Map<String,String> validateExact(Map<String,String> values,String... required){
        var copy=validate(values,required);
        if(copy.size()!=required.length || !copy.keySet().equals(Set.of(required)))
            throw new IllegalArgumentException("missing or unexpected action field");
        return copy;
    }
    static String canonical(Map<String,String> fields){
        StringBuilder b=new StringBuilder();
        new TreeMap<>(fields).forEach((k,v)->b.append(k.length()).append(':').append(k).append(v.length()).append(':').append(v));
        return b.toString();
    }
    static String required(Map<String,String> v,String key,int max){
        return ClinicalCaseCore.text(v.get(key),max);
    }
    static UUID requiredId(Map<String,String> v,String key){return ClinicalCaseCore.uuid(v.get(key));}
    static Instant instant(Map<String,String> v,String key){
        try {return Instant.parse(required(v,key,40));}catch(RuntimeException e){throw new IllegalArgumentException("invalid timestamp");}
    }
    static void require(boolean condition){if(!condition)throw new IllegalStateException("unsafe clinical transition");}
    static void forbid(boolean condition){require(!condition);}
}
