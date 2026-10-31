package org.synqoria.longitudinal;

import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

/** Strict bounded decoder for a narrow clinical command, not a general-purpose JSON parser. */
public final class LongitudinalStrictJson {
  private LongitudinalStrictJson(){}
  public record Body(UUID commandId,long expectedRevision,String operation,Map<String,String> inputs) {}
  public static Body decode(byte[] bytes){
    if(bytes==null||bytes.length==0||bytes.length>32768)throw new IllegalArgumentException("invalid body size");
    String text;
    try{text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
    catch(CharacterCodingException ex){throw new IllegalArgumentException("invalid utf8");}
    var p=new Parser(text);
    Map<String,Object> root=p.root();
    if(!root.keySet().equals(Set.of("commandId","expectedRevision","operation","inputs")))
      throw new IllegalArgumentException("invalid command envelope");
    if(!(root.get("commandId") instanceof String id)||!id.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))
      throw new IllegalArgumentException("invalid command id");
    UUID uuid=UUID.fromString(id);if(!uuid.toString().equals(id))throw new IllegalArgumentException("noncanonical id");
    if(!(root.get("expectedRevision") instanceof Long rev)||rev<0)
      throw new IllegalArgumentException("invalid revision");
    if(!(root.get("operation") instanceof String op)||!op.matches("(encounter|note|observation)\\.[a-z_]{2,60}"))
      throw new IllegalArgumentException("invalid operation");
    if(!(root.get("inputs") instanceof Map<?,?> raw))throw new IllegalArgumentException("invalid inputs");
    Map<String,String> clean=new LinkedHashMap<>();
    for(var entry:raw.entrySet()){
      if(!(entry.getKey() instanceof String k)||!(entry.getValue() instanceof String v)||
        !k.matches("[A-Za-z][A-Za-z0-9]{0,63}")||v.length()>8192)
        throw new IllegalArgumentException("invalid input member");
      clean.put(k,v);
    }
    return new Body(uuid,rev,op,Map.copyOf(clean));
  }
  private static final class Parser {
    final String s;int i;
    Parser(String s){this.s=s;}
    void ws(){while(i<s.length()&&" \r\n\t".indexOf(s.charAt(i))>=0)i++;}
    boolean eat(char c){ws();if(i<s.length()&&s.charAt(i)==c){i++;return true;}return false;}
    void need(char c){if(!eat(c))throw new IllegalArgumentException("invalid json");}
    String str(){need('"');StringBuilder b=new StringBuilder();boolean ended=false;
      while(i<s.length()){
        char c=s.charAt(i++);if(c=='"'){ended=true;break;}
        if(c<0x20)throw new IllegalArgumentException("invalid string");
        if(c=='\\'){
          if(i>=s.length())throw new IllegalArgumentException("invalid escape");
          char e=s.charAt(i++);switch(e){
            case '"','\\','/' -> b.append(e);
            case 'n' -> b.append('\n');case 'r' -> b.append('\r');case 't' -> b.append('\t');
            case 'b' -> b.append('\b');case 'f' -> b.append('\f');
            case 'u' -> {if(i+4>s.length())throw new IllegalArgumentException("invalid unicode");
              int ch=0;for(int k=0;k<4;k++){int h=Character.digit(s.charAt(i++),16);
                if(h<0)throw new IllegalArgumentException("invalid unicode");ch=(ch<<4)|h;}b.append((char)ch);}
            default -> throw new IllegalArgumentException("invalid escape");
          }
        }else b.append(c);
        if(b.length()>8192)throw new IllegalArgumentException("oversized string");
      }
      if(!ended)throw new IllegalArgumentException("unterminated string");
      for(int p=0;p<b.length();p++){char c=b.charAt(p);
        if(Character.isHighSurrogate(c)){if(p+1>=b.length()||!Character.isLowSurrogate(b.charAt(++p)))throw new IllegalArgumentException("invalid surrogate");}
        else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("invalid surrogate");}
      return b.toString();
    }
    Map<String,String> inputs(){need('{');Map<String,String> a=new LinkedHashMap<>();if(eat('}'))return a;
      do{String k=str();if(a.containsKey(k)||a.size()>=32)throw new IllegalArgumentException("duplicate or excessive input");
        need(':');a.put(k,str());if(eat('}'))return a;need(',');}while(true);
    }
    Map<String,Object> root(){need('{');Map<String,Object> r=new LinkedHashMap<>();
      do{String k=str();if(r.containsKey(k)||r.size()>4)throw new IllegalArgumentException("duplicate or excessive field");
        need(':');Object v;
        if(k.equals("inputs"))v=inputs();
        else if(k.equals("expectedRevision")){
          ws();int start=i;while(i<s.length()&&Character.isDigit(s.charAt(i)))i++;
          if(i==start||i-start>18||(i-start>1&&s.charAt(start)=='0'))throw new IllegalArgumentException("bad revision");
          try{v=Long.parseLong(s.substring(start,i));}catch(NumberFormatException ex){throw new IllegalArgumentException("bad revision");}
        }else if(k.equals("commandId")||k.equals("operation"))v=str();
        else throw new IllegalArgumentException("unexpected field");
        r.put(k,v);if(eat('}'))break;need(',');}while(true);
      ws();if(i!=s.length())throw new IllegalArgumentException("trailing input");return r;
    }
  }
}
