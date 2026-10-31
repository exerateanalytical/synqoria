package org.synqoria.scheduling;
import java.util.regex.Pattern;
/** Validates command fingerprints before storage or replay comparison. */
public final class AppointmentDigestValidation {
 private static final Pattern HEX=Pattern.compile("[0-9a-f]{64}");
 private AppointmentDigestValidation(){}
 public static String require(String digest){if(digest==null||!HEX.matcher(digest).matches())throw new IllegalArgumentException("Invalid command digest");return digest;}
}
