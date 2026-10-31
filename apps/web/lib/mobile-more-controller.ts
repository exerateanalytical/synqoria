/** Mobile-only accessible disclosure. Never changes authorization or grants clinical access. */
export function attachMobileMoreDisclosure(
  button: HTMLButtonElement,
  panel: HTMLElement,
  onClose: (restoreFocus: boolean) => void,
): () => void {
  const candidates = () => Array.from(panel.querySelectorAll<HTMLElement>(
    'button:not([disabled]),a[href],input:not([disabled]),select:not([disabled]),[tabindex]:not([tabindex="-1"])',
  )).filter(node => node.getClientRects().length > 0 && !node.hasAttribute('inert'));
  const raf = window.requestAnimationFrame(() => candidates()[0]?.focus());
  const keydown = (event: KeyboardEvent) => {
    if (event.key === 'Escape') {
      event.preventDefault();
      onClose(true);
      return;
    }
    if (event.key !== 'Tab') return;
    const focusable = candidates();
    if (!focusable.length) {
      event.preventDefault();
      panel.focus();
      return;
    }
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    const current = document.activeElement;
    if (!panel.contains(current) || (event.shiftKey && current === first)) {
      event.preventDefault();
      (event.shiftKey ? last : first).focus();
    } else if (!event.shiftKey && current === last) {
      event.preventDefault();
      first.focus();
    }
  };
  const pointer = (event: PointerEvent) => {
    if (event.target instanceof Node && !panel.contains(event.target) && !button.contains(event.target)) {
      onClose(false);
    }
  };
  const resize = () => { if (window.innerWidth >= 768) onClose(false); };
  const pagehide = () => onClose(false);
  document.addEventListener('keydown', keydown);
  document.addEventListener('pointerdown', pointer);
  window.addEventListener('resize', resize);
  window.addEventListener('pagehide', pagehide);
  return () => {
    window.cancelAnimationFrame(raf);
    document.removeEventListener('keydown', keydown);
    document.removeEventListener('pointerdown', pointer);
    window.removeEventListener('resize', resize);
    window.removeEventListener('pagehide', pagehide);
  };
}
