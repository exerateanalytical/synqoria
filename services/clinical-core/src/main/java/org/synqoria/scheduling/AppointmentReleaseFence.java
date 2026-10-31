package org.synqoria.scheduling;
public final class AppointmentReleaseFence {private AppointmentReleaseFence(){}public static void require(boolean postgresVerified,boolean oidcVerified,boolean securityApproved,boolean clinicalApproved){if(!postgresVerified||!oidcVerified||!securityApproved||!clinicalApproved)throw new SecurityException("clinical release denied");}}
