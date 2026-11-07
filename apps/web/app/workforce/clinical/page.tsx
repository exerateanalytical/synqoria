import registry from "../../../../../registers/canonical-screens.json";
import implementation from "../../../../../registers/operational-screen-progress-v0.1.json";
import CanonicalRouteDirectory from "./CanonicalRouteDirectory";
type RouteScreen={id:string;module:string;name:string;layout:string;primary_action:string};
export default function ClinicalOperationsIndex(){
 return <CanonicalRouteDirectory screens={registry.screen_register as RouteScreen[]} implemented={implementation.rows.map(x=>({id:x.id,category:x.category}))}/>;
}
