import Link from "next/link";
import {UI_COMPONENTS,UI_DIALOGS,UI_PRINTS} from "../screen-contract.mjs";
import {ComponentGallery} from "../ComponentGallery";
import "../catalog.css";
export default function CatalogueComponents(){
 return <main className="syn-catalog"><nav className="syn-crumb" aria-label="Breadcrumb"><Link href="/ui/catalog">261-screen catalogue</Link><span>/</span><strong aria-current="page">Component gallery</strong></nav>
  <header className="syn-catalog-head"><div><p className="syn-kicker">SYNQORIA UI v1.1 · Completeness laboratory</p><h1>Shared controls, dialogs and print outputs</h1><p>Catalogue of 26 reusable components, 12 dialogs and 18 print contracts. Prototypes only: this is not proof of server integration, clinical acceptability or full component delivery.</p></div><span className="syn-chip">Clinical release denied</span></header>
  <section className="syn-panel"><ComponentGallery components={UI_COMPONENTS} dialogs={UI_DIALOGS} printables={UI_PRINTS}/></section>
  <footer><Link href="/ui/catalog">Back to screen catalogue</Link></footer>
 </main>;
}