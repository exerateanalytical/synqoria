/**
 * SYNQORIA P03-B0060: bind every clinical admission to the deployment's
 * single authoritative hospital tenant and facility.
 *
 * A valid staff cookie from another hospital cannot be replayed into this
 * clinical cell, even if a remote facility-context endpoint is misrouted.
 * The expected tenant/facility are exclusively provisioned by the server.
 */
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[89ab0-9a-f]{12}$/i;
export class ClinicalCellAffinityError extends Error{
 constructor(){super("clinical_cell_affinity_denied");this.name="ClinicalCellAffinityError";}
}
const deny=()=>{throw new ClinicalCellAffinityError();};
export function requireClinicalCellAffinity(staff,env){
 if(!staff||typeof staff!=="object"||!env||typeof env!=="object"||
    !UUID.test(env.SYNQORIA_EXPECTED_TENANT_ID)||
    !UUID.test(env.SYNQORIA_EXPECTED_FACILITY_ID)||
    !UUID.test(staff.tenantId)||!UUID.test(staff.facilityId)||
    staff.tenantId!==env.SYNQORIA_EXPECTED_TENANT_ID||
    staff.facilityId!==env.SYNQORIA_EXPECTED_FACILITY_ID)deny();
 return Object.freeze({tenantId:staff.tenantId,facilityId:staff.facilityId});
}
