package org.synqoria.longitudinal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static org.synqoria.longitudinal.LongitudinalInboxSafety.Envelope;
/** Host-authenticated broker transport binding. Does not authenticate broker itself. */
public final class LongitudinalInboxBrokerRecord {
  private LongitudinalInboxBrokerRecord() {}
  public record Record(String topic, String partitionKey, Map<String,String> headers, byte[] body) {
    public Record { Objects.requireNonNull(topic); Objects.requireNonNull(partitionKey); Objects.requireNonNull(headers);
      Objects.requireNonNull(body); headers=Map.copyOf(headers); body=body.clone(); }
    @Override public byte[] body(){return body.clone();}
  }
  private static String digest(byte[] input){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));}
    catch(Exception e){throw new IllegalStateException(e);}}
  public static String partitionKey(Envelope e){return digest((e.scope().tenant()+"|"+e.scope().facility()+"|"+
    e.scope().patient()+"|"+e.scope().encounter()).getBytes(StandardCharsets.US_ASCII));}
  public static Record serialize(String topic, Envelope event){
    if(!"synqoria.clinical.events.v1".equals(topic))throw new SecurityException("broker topic unavailable");
    byte[] body=LongitudinalInboxWire.encode(event);
    return new Record(topic,partitionKey(event),Map.of("schema","synqoria.inbox.v1", "event-sha256",digest(body)),body);
  }
  public static Envelope verify(Record record){
    if(!"synqoria.clinical.events.v1".equals(record.topic())||record.headers().size()!=2||
      !"synqoria.inbox.v1".equals(record.headers().get("schema"))||
      !digest(record.body()).equals(record.headers().get("event-sha256")))
      throw new SecurityException("broker frame unavailable");
    Envelope e=LongitudinalInboxWire.decode(record.body());
    if(!partitionKey(e).equals(record.partitionKey())) throw new SecurityException("broker partition unavailable");
    return e;
  }
}
