/** Static preview contract declaration; not a clinical API or field schema. */
export interface CanonicalScreenRecord {
 id:string; module:string; name:string; layout:string;
 data_input_groups:string; primary_action:string;
}
export interface ScreenPreviewContract {
 screenId:string; module:string; title:string;
 layout:"F"|"L"|"D"|"X"|"B"|"T"|"H";
 fieldGroups:string[]; tabs:string[]; controls:string[];
 filters:string[]; primaryAction:string; saveMode:"memory-only-preview";
 authorization:"unconnected"; release:"denied";
 contentStatus:"contract-generated-proposal";uiStateIds:string[];
}
export declare const SCREEN_LAYOUTS: Record<string,string>;
export declare const UI_STATES: string[];
export declare const UI_DIALOGS: string[];
export declare const UI_PRINTS: string[];
export declare const UI_COMPONENTS: string[];
export declare const UI_LAYOUT_CONFIG: Record<string,{tabs:string[];controls:string[];filters:string[]}>;
export declare function buildScreenContract(record:CanonicalScreenRecord):ScreenPreviewContract;
