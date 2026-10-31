package org.synqoria.registration;
import java.time.*;import java.util.*;
/** P05-B0001: provisional intake, no invented name or birth date required. */
public record RegistrationDraft(String legalName, LocalDate dateOfBirth, Integer estimatedAgeYears,
  BirthInformation birthInformation, String language, boolean duplicateReviewCleared) {
 public enum BirthInformation { KNOWN, ESTIMATED_AGE, UNKNOWN }
 public RegistrationDraft {
  Objects.requireNonNull(birthInformation,"birthInformation");
  if(legalName!=null){if(legalName.codePoints().anyMatch(c->Character.isISOControl(c)))throw new IllegalArgumentException("Invalid name"); legalName=legalName.strip(); if(legalName.length()<1||legalName.length()>200)throw new IllegalArgumentException("Invalid name");}
  if(!"en".equals(language)&&!"fr".equals(language))throw new IllegalArgumentException("Invalid language");
  if(birthInformation==BirthInformation.KNOWN && (dateOfBirth==null || estimatedAgeYears!=null))throw new IllegalArgumentException("Known birthdate required");
  if(birthInformation==BirthInformation.ESTIMATED_AGE && (dateOfBirth!=null || estimatedAgeYears==null||estimatedAgeYears<0||estimatedAgeYears>130))throw new IllegalArgumentException("Invalid estimated age");
  if(birthInformation==BirthInformation.UNKNOWN && (dateOfBirth!=null||estimatedAgeYears!=null))throw new IllegalArgumentException("Unknown requires absent birth fields");
 }
 /** Not a clinical age assessment; time validity is assessed at the command boundary. */
 public boolean plausibleAt(LocalDate today){return dateOfBirth==null||(!dateOfBirth.isAfter(today)&&!dateOfBirth.isBefore(today.minusYears(130)));}
}
