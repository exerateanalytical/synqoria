package org.synqoria.longitudinal;

import java.time.Clock;
import java.util.Objects;
import javax.sql.DataSource;
import org.synqoria.http.BearerAdmission;

/** Restricted constructor wiring for an externally configured HTTP server; not a deployed service. */
public final class LongitudinalClinicalWiring {
 private LongitudinalClinicalWiring(){}
 public static LongitudinalHttpHandler jdbcHandler(DataSource dataSource,
         LongitudinalEnvelopeCrypto.KeyResolver keys,BearerAdmission admission,
         LongitudinalHttpHandler.ClinicalEvidenceSource evidence,Clock clock){
  Objects.requireNonNull(admission);Objects.requireNonNull(evidence);Objects.requireNonNull(clock);
  // No OIDC, data source, clinical consent provider, KMS or server is created implicitly.
  return new LongitudinalHttpHandler(admission,evidence,
    new LongitudinalAuthenticatedGateway(new LongitudinalJdbcPersistence(dataSource,keys)),clock);
 }
}
