package org.synqoria.longitudinal;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import static org.synqoria.longitudinal.ClinicalCaseCore.Scope;
import static org.synqoria.longitudinal.LongitudinalInboxSafety.Envelope;

/** Strict deterministic metadata-only wire format for a PRIVATE authenticated broker.
 * Neither authentication nor confidentiality is provided by this codec; a trusted broker
 * client and authenticated consumer service are mandatory deployment prerequisites. */
public final class LongitudinalInboxWire {
  private LongitudinalInboxWire() {}
  public static final int MAX_BYTES=512;
  public static byte[] encode(Envelope e) {
    Objects.requireNonNull(e);
    String text=String.join("|",e.scope().tenant().toString(),e.scope().facility().toString(),
      e.scope().patient().toString(),e.scope().encounter().toString(),e.eventId().toString(),
      e.commandId().toString(),Long.toString(e.revision()),e.eventHash(),e.eventType(),
      e.occurredAt().toString());
    byte[] raw=text.getBytes(StandardCharsets.US_ASCII);
    if(raw.length>MAX_BYTES)throw new IllegalArgumentException("inbox metadata exceeds budget");
    return raw;
  }
  public static Envelope decode(byte[] raw) {
    if(raw==null||raw.length<1||raw.length>MAX_BYTES)throw new IllegalArgumentException("invalid inbox frame");
    final String text;
    try { text=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw)).toString(); }
    catch(CharacterCodingException e){throw new IllegalArgumentException("invalid inbox encoding");}
    String[] x=text.split("\\|",-1);
    if(x.length!=10)throw new IllegalArgumentException("invalid inbox frame shape");
    try {
      Scope scope=new Scope(UUID.fromString(x[0]),UUID.fromString(x[1]),
         UUID.fromString(x[2]),UUID.fromString(x[3]));
      Envelope decoded=new Envelope(scope,UUID.fromString(x[4]),UUID.fromString(x[5]),
         Long.parseLong(x[6]),x[7],x[8],Instant.parse(x[9]));
      if(!text.equals(new String(encode(decoded),StandardCharsets.US_ASCII)))
        throw new IllegalArgumentException("noncanonical inbox frame");
      return decoded;
    } catch(RuntimeException e){throw new IllegalArgumentException("invalid inbox frame fields");}
  }
}
