/** Canonical Warm Clinical tokens. Do not substitute gradients, shadows or ad-hoc palette values. */
export const colors = Object.freeze({
  canvas: '#F7F5F0',
  surface: '#FFFFFF',
  surfaceSubtle: '#F0EEE7',
  navy: '#073656',
  primary: '#1664D4',
  primaryHover: '#1253B1',
  primaryPressed: '#0E438F',
  textPrimary: '#172B4D',
  textSecondary: '#475569',
  navHover: '#104766',
  navSelected: '#165A7A',
  borderDecorative: '#DEDCD5',
  borderControl: '#64748B',
  focus: '#1664D4',
  infoBackground: '#EAF2FF', infoForeground: '#174EA6',
  successBackground: '#E8F5EC', successForeground: '#166534',
  warningBackground: '#FFF4D6', warningForeground: '#854D0E',
  dangerBackground: '#FDECEC', dangerForeground: '#B42318',
  neutralBackground: '#EDF2F7', neutralForeground: '#475569',
});
export type ColorToken = keyof typeof colors;
export const typography = Object.freeze({
  family: 'Inter, system-ui, sans-serif',
  bodyPx: 16,
  labelPx: 14,
  desktopTitlePx: 28,
  mobileTitlePx: 24,
  minimumTouchPx: 44,
  bedsideTouchPx: 48,
});
export const breakpoints = Object.freeze({ mobileMax: 767, tabletMax: 1199 });

function luminance(hex: string): number {
  if (!/^#[0-9a-fA-F]{6}$/.test(hex)) throw new Error('Invalid hex colour');
  const values = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16) / 255);
  const linear = values.map(v => v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4);
  return 0.2126 * linear[0]! + 0.7152 * linear[1]! + 0.0722 * linear[2]!;
}
export function contrastRatio(a: string, b: string): number {
  const [light, dark] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (light! + 0.05) / (dark! + 0.05);
}
export const approvedTextPairs: readonly (readonly [string, string])[] = [
  [colors.textPrimary, colors.surface],
  [colors.textSecondary, colors.surface],
  [colors.textPrimary, colors.canvas],
  [colors.textSecondary, colors.canvas],
  [colors.textPrimary, colors.surfaceSubtle],
  ['#FFFFFF', colors.navy],
  ['#FFFFFF', colors.primary],
  ['#FFFFFF', colors.navSelected],
  [colors.infoForeground, colors.infoBackground],
  [colors.successForeground, colors.successBackground],
  [colors.warningForeground, colors.warningBackground],
  [colors.dangerForeground, colors.dangerBackground],
];
