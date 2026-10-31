package org.synqoria.triage;

import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

/** Bounded strict JSON flat-object parser for the command HTTP boundary; rejects ambiguous keys and nested values.
 * Not a general-purpose JSON library. Never parse clinical observations through this transport.
 */
public final class TriageStrictJson {
 private TriageStrictJson() {}
 private static final Set<String> FIELDS=Set.of("commandId","expectedRevision","action","text","reference","rationale","appointment","observation","caseIds","stationOrClinician","after");
 public static Map<String,Object> parse(byte[] input) {
  if(input==null||input.length==0||input.length>16384)throw new IllegalArgumentException("invalid triage JSON size");
  try {
   String source=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
       .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(input)).toString();
   return new Parser(source).read();
  }catch(CharacterCodingException ex){throw new IllegalArgumentException("invalid UTF-8");}
 }
 private static final class Parser {
  final String s;int i=0;
  Parser(String s){this.s=s;}
  void ws(){while(i<s.length()&&(s.charAt(i)==' '||s.charAt(i)=='\n'||s.charAt(i)=='\r'||s.charAt(i)=='\t'))i++;}
  boolean eat(char c){ws();if(i<s.length()&&s.charAt(i)==c){i++;return true;}return false;}
  void need(char c){if(!eat(c))throw new IllegalArgumentException("invalid JSON framing");}
  String string(){
   ws();need('"');var out=new StringBuilder();
   while(i<s.length()){
    char c=s.charAt(i++);
    if(c=='"')return out.toString();
    if(c<0x20)throw new IllegalArgumentException("unescaped JSON control");
    if(c=='\\'){
     if(i>=s.length())throw new IllegalArgumentException("invalid JSON escape");
     char e=s.charAt(i++);
     switch(e){
      case '"','\\','/' -> out.append(e);
      case 'b' -> out.append('\b');case 'f' -> out.append('\f');case 'n' -> out.append('\n');
      case 'r' -> out.append('\r');case 't' -> out.append('\t');
      case 'u' -> {if(i+4>s.length())throw new IllegalArgumentException("short Unicode escape");
       int cp=0;for(int n=0;n<4;n++){int h=Character.digit(s.charAt(i++),16);if(h<0)throw new IllegalArgumentException("invalid Unicode escape");cp=(cp<<4)|h;}out.append((char)cp);}
      default -> throw new IllegalArgumentException("unsupported JSON escape");
     }
    }else out.append(c);
    if(out.length()>4096)throw new IllegalArgumentException("JSON value too large");
   }
   throw new IllegalArgumentException("unterminated JSON string");
  }
  Object array(){
   need('[');List<String> result=new ArrayList<>();
   if(eat(']'))throw new IllegalArgumentException("empty case list");
   while(true){if(result.size()==50)throw new IllegalArgumentException("case list exceeds bound");result.add(string());
    if(eat(']'))break;need(',');
   }
   return List.copyOf(result);
  }
  Object observation(){
   need('{');Map<String,Object> o=new LinkedHashMap<>();
   Set<String> fields=Set.of("id","code","value","unit","takenAt","replaces");
   if(eat('}'))throw new IllegalArgumentException("empty observation");
   while(true){String k=string();if(!fields.contains(k)||o.containsKey(k))throw new IllegalArgumentException("unknown or duplicate observation field");
    need(':');Object v=value();if(!(v instanceof String))throw new IllegalArgumentException("invalid observation field type");
    o.put(k,v);if(eat('}'))break;need(',');
   }
   for(String required:List.of("id","code","value","unit","takenAt"))if(!o.containsKey(required))throw new IllegalArgumentException("missing observation evidence");
   return Map.copyOf(o);
  }
  Object value(){ws();if(i>=s.length())throw new IllegalArgumentException("missing JSON value");
   if(s.charAt(i)=='"'){String value=string();
    for(int j=0;j<value.length();j++){
     char c=value.charAt(j);
     if(Character.isHighSurrogate(c)){if(j+1>=value.length()||!Character.isLowSurrogate(value.charAt(++j)))throw new IllegalArgumentException("unpaired surrogate");}
     else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("unpaired surrogate");
    }
    return value;
   }
   int start=i;while(i<s.length()&&s.charAt(i)>='0'&&s.charAt(i)<='9')i++;
   if(start==i||i-start>10||(i-start>1&&s.charAt(start)=='0'))throw new IllegalArgumentException("unsupported JSON value");
   try{return Integer.parseInt(s.substring(start,i));}catch(NumberFormatException ex){throw new IllegalArgumentException("invalid revision");}
  }
  Map<String,Object> read(){need('{');Map<String,Object> map=new LinkedHashMap<>();if(eat('}'))throw new IllegalArgumentException("empty JSON command");
   while(true){String key=string();if(!FIELDS.contains(key)||map.containsKey(key))throw new IllegalArgumentException("duplicate or unknown JSON key");need(':');
    Object val=key.equals("observation")?observation():key.equals("caseIds")?array():value();if(key.equals("expectedRevision")!= (val instanceof Integer)||key.equals("observation")!= (val instanceof Map)||key.equals("caseIds")!=(val instanceof List))throw new IllegalArgumentException("JSON field type mismatch");
    map.put(key,val);if(eat('}'))break;need(',');
   }
   ws();if(i!=s.length())throw new IllegalArgumentException("JSON trailing data");return Map.copyOf(map);
  }
 }
}
