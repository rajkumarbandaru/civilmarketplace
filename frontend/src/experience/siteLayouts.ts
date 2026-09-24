import manifest from './registry-manifest.json';

/**
 * Site layouts (architecture 05 §5): how the website's page templates arrange the block library.
 * A tenant picks the layout; the layout owns which blocks appear, in what order, and with which
 * variant — tenants do not hand-place blocks. Blocks still honour the CMS: a section switched off
 * there is omitted wherever the layout puts it, leaving no empty hole.
 */
export interface SiteLayout {
  key: string;
  label: string;
  /** The home page template: block keys, top to bottom. */
  home: string[];
  heroHeight: string;
}

export const SITE_LAYOUTS: Record<string, SiteLayout> = {
  marketplace: {
    key: 'marketplace', label: 'Marketplace',
    home: ['hero', 'stats', 'services', 'how-it-works', 'cta'],
    heroHeight: '90vh',
  },
  corporate: {
    key: 'corporate', label: 'Corporate',
    home: ['hero', 'how-it-works', 'stats', 'cta'],
    heroHeight: '70vh',
  },
  ecommerce: {
    key: 'ecommerce', label: 'E-commerce',
    home: ['hero', 'services', 'cta'],
    heroHeight: '55vh',
  },
};

export const siteLayout = (key: string | null | undefined): SiteLayout => {
  const layout = key ? SITE_LAYOUTS[key] : undefined;
  if (key && !layout) console.warn(`[experience] unknown site layout '${key}', using marketplace`);
  return layout ?? SITE_LAYOUTS.marketplace;
};

/** Layouts and the blocks they use must all be in the manifest. */
export const layoutRegistryMismatch = (): string[] => {
  const problems: string[] = [];
  for (const key of Object.keys(SITE_LAYOUTS)) if (!manifest.siteLayouts.includes(key)) problems.push(`layout ${key}`);
  for (const key of manifest.siteLayouts) if (!SITE_LAYOUTS[key]) problems.push(`no renderer for layout ${key}`);
  for (const l of Object.values(SITE_LAYOUTS)) {
    for (const b of l.home) if (!manifest.blocks.includes(b)) problems.push(`block ${b} in ${l.key}`);
  }
  return problems;
};
