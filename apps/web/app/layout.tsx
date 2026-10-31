import type { Metadata } from 'next';
import './globals.css';
export const metadata: Metadata = { title: 'SYNQORIA', description: 'Africa medical intelligence synchronized' };
export default function RootLayout({children}:{children:React.ReactNode}) {
  return <html lang="en"><body>{children}</body></html>;
}
