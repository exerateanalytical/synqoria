#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p /tmp/synqoria-security-classes
javac -d /tmp/synqoria-security-classes $(find services/clinical-core/src/main/java services/clinical-core/src/test/java -type f -name "*.java" | sort)
java -ea -cp /tmp/synqoria-security-classes org.synqoria.security.PatientScopePolicyTest

java -ea -cp /tmp/synqoria-security-classes org.synqoria.security.ClinicalAccessPolicyTest

java -ea -cp /tmp/synqoria-security-classes org.synqoria.security.PatientReadServiceTest
java -ea -cp /tmp/synqoria-security-classes org.synqoria.facility.FacilityDirectoryTest

java -ea -cp /tmp/synqoria-security-classes org.synqoria.events.IdempotencyGuardTest
java -ea -cp /tmp/synqoria-security-classes org.synqoria.events.OutboxClaimLedgerTest
java -ea -cp /tmp/synqoria-security-classes org.synqoria.security.TrustedSessionAdmissionTest
java -ea -cp /tmp/synqoria-security-classes org.synqoria.consent.ConsentScopePolicyTest
java -ea -cp /tmp/synqoria-security-classes org.synqoria.facility.FacilityLifecycleTest
java -ea -cp /tmp/synqoria-security-classes org.synqoria.facility.CellRouteDirectoryTest

java -ea -cp /tmp/synqoria-security-classes org.synqoria.reliability.IdempotencyLedgerTest

java -ea -cp /tmp/synqoria-security-classes org.synqoria.reliability.OutboxIntentTest

java -ea -cp /tmp/synqoria-security-classes org.synqoria.reliability.InboxSequenceGuardTest

java -ea -cp /tmp/synqoria-security-classes org.synqoria.security.SafeApiProblemTest

java -ea -cp /tmp/synqoria-security-classes org.synqoria.security.SessionFreshnessPolicyTest

java -ea -cp /tmp/synqoria-security-classes org.synqoria.facility.LocationScopePolicyTest

# v0.7.0 domain policy tests
java -ea -cp /tmp/synqoria-security-classes org.synqoria.reliability.CommandPreconditionTest
java -ea -cp /tmp/synqoria-security-classes org.synqoria.reliability.DeliveryRetryPolicyTest
java -ea -cp /tmp/synqoria-security-classes org.synqoria.security.PurposeOfUseDecisionTest
java -ea -cp /tmp/synqoria-security-classes org.synqoria.security.EmergencyAccessPolicyTest
java -ea -cp /tmp/synqoria-security-classes org.synqoria.facility.FacilityGrantRevisionTest
java -ea -cp /tmp/synqoria-security-classes org.synqoria.facility.LocationMovePolicyTest

java -ea -cp /tmp/synqoria-security-classes org.synqoria.http.ClinicalGatewayIntegrationTest

java -ea -cp /tmp/synqoria-security-classes org.synqoria.audit.BoundedAuditJournalTest
java -ea -cp /tmp/synqoria-security-classes org.synqoria.security.SessionEpochFenceTest
java -ea -cp /tmp/synqoria-security-classes org.synqoria.persistence.JdbcScopeIntegrationTest
java -ea -cp /tmp/synqoria-security-classes org.synqoria.persistence.V100TransactionIntegrationTest

# v0.11.0: focused implementation batches with mock JDBC and loopback HTTP.
for batch in P01-B0024 P01-B0025 P01-B0026 P01-B0027 P03-B0015 P03-B0016 P04-B0011; do
 java -ea -cp /tmp/synqoria-security-classes org.synqoria.integration.V110IntegrationTest "$batch"
done

# v0.12.0 M01 provisional identity registration (JDBC proxy only)
java -ea -cp /tmp/synqoria-security-classes org.synqoria.registration.V120RegistrationTest

# v0.13.0 source-only registration safeguards; no live PostgreSQL/OIDC.
for batch in P05-B0006 P05-B0007 P05-B0008 P05-B0009 P05-B0010 P04-B0012; do
 java -ea -cp /tmp/synqoria-security-classes org.synqoria.registration.V130RegistrationSafetyTest "$batch"
done
# v0.14.0 review-proof unit and mock-JDBC tests: never live reviewer or PostgreSQL authority
for batch in P05-B0011 P05-B0012 P05-B0013 P05-B0014 P05-B0015; do
 java -ea -cp /tmp/synqoria-security-classes org.synqoria.registration.V140ReviewBoundaryTest "$batch"
done
# v0.15.0 reviewer release policy foundation, no runtime identity or database execution.
for batch in P05-B0016 P05-B0017 P05-B0018 P05-B0019 P05-B0020 P05-B0021; do
 java -ea -cp /tmp/synqoria-security-classes org.synqoria.registration.V150ReviewGuardTest "$batch"
done

# v0.16.0: five independent reviewer/registration safety batches, local Java only.
for batch in P05-B0022 P05-B0023 P05-B0024 P05-B0025 P05-B0026; do
 java -ea -cp /tmp/synqoria-security-classes org.synqoria.registration.V160ReviewSafetyTest "$batch"
done

# v0.17.0: six independent signature/framing/pins/time/key/grant primitives; no production OIDC.
for batch in P03-B0018 P03-B0019 P03-B0020 P03-B0021 P03-B0022 P03-B0023; do
 java -ea -cp /tmp/synqoria-security-classes org.synqoria.security.V170IdentityFoundationTest "$batch"
done

# v0.18.0: individually verified trusted-key, protected-header, signed-byte, claim and audit boundaries.
# No vetted JSON parser, production OIDC or live grant authority is implied.
for batch in P03-B0024 P03-B0025 P03-B0026 P03-B0027 P03-B0028; do
 java -ea -cp /tmp/synqoria-security-classes org.synqoria.security.V180OidcBoundaryTest "$batch"
done

# v0.20.0: five independently tested OIDC trust-boundary primitives (NO production IdP)
for batch in P03-B0029 P03-B0030 P03-B0031 P03-B0032 P03-B0033; do
 java -ea -cp /tmp/synqoria-security-classes org.synqoria.security.V200OidcFoundationsTest "$batch"
done

# v0.23.0: scoped V2 reviewer event evidence, no DB/clinical authority.
for batch in P05-B0043 P05-B0044 P05-B0045 P05-B0046 P05-B0047; do
 java -ea -cp /tmp/synqoria-security-classes org.synqoria.registration.V230ScopedReviewTest "$batch"
done

# v0.25.0: mock JDBC restricted-review adapter; NO PostgreSQL integration or clinical authority.
for batch in P05-B0053 P05-B0054 P05-B0055 P05-B0056 P05-B0057; do
 java -ea -cp /tmp/synqoria-security-classes org.synqoria.registration.V250ReviewTransactionTest "$batch"
done

# v0.26.0: mock-JDBC role privileges, freshness and restricted transaction guards.
for batch in P05-B0058 P05-B0060 P05-B0061; do
 java -ea -cp /tmp/synqoria-security-classes org.synqoria.registration.V260ReviewerGateTest "$batch"
done

# v0.28.0 M02 scheduling foundations; all in-memory/unit-only, NO live booking or PostgreSQL.
for test in AppointmentWindowTest AppointmentAggregateTest ResourceReservationIndexTest AppointmentAccessPolicyTest AppointmentCommandFingerprintTest AppointmentWorkflowPreflightTest; do
 java -ea -cp /tmp/synqoria-security-classes "org.synqoria.scheduling.$test"
done

# Reconciled v0.27.1 recovery fences (legacy scheduling safeguards, NO production writes).
# The legacy JDBC table/migration remains quarantined until aligned and DB-tested.
for batch in P06-B0010 P06-B0011 P06-B0012 P06-B0013 P06-B0014 P06-B0015 P06-B0016 P06-B0017 P06-B0018 P06-B0019; do
 java -ea -cp /tmp/synqoria-security-classes org.synqoria.scheduling.V030SchedulingRecoveryTest "$batch"
done
