package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** P07-B0147: Bounded recovery offset range without rewinding committed broker cursor. Pure fail-closed policy only; caller supplies trusted context and transactional state. */
public final class InboxRecoveryOffsetWindow {
 private InboxRecoveryOffsetWindow(){}
 private static void require(boolean condition){if(!condition)throw new SecurityException("clinical event unavailable");}

 public record Window(long first,long last,long cursorAtRequest){public Window{require(first>=0&&last>=first&&last-first<500&&cursorAtRequest>=-1&&first>cursorAtRequest);}}
 public static Window request(long first,long last,long cursor){return new Window(first,last,cursor);}

}
