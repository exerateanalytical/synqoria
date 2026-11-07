import type { NextConfig } from 'next';
const nextConfig: NextConfig = { transpilePackages: ['@synqoria/ux-core'], reactStrictMode: true, serverExternalPackages: ['pg'] };
export default nextConfig;
