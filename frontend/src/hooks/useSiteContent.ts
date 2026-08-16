import { useEffect, useState } from 'react';
import { FALLBACK_CONTENT } from '../constants/siteContent';
import { ContentSection, fetchSiteContent } from '../services/siteContentApi';

/**
 * The site's editable copy, shared by every public surface that renders it.
 *
 * Cached at module level and fetched once per page load, for the same reason the catalogue is: the
 * landing page, the footer and the navbar all need it, it changes only when an admin edits it, and
 * three fetches of the same payload per navigation is not a cost worth paying.
 *
 * On failure it falls back to the copy the site shipped with, so a timed-out request leaves the
 * page looking slightly stale rather than empty.
 */

interface SiteContentState {
  sections: ContentSection[];
  loading: boolean;
  /** True when the API could not be reached and the shipped copy is on screen instead. */
  stale: boolean;
}

const FALLBACK: SiteContentState = { sections: FALLBACK_CONTENT, loading: false, stale: true };

let cache: SiteContentState | null = null;
let inFlight: Promise<SiteContentState> | null = null;
const subscribers = new Set<(state: SiteContentState) => void>();

const load = (): Promise<SiteContentState> => {
  if (inFlight) return inFlight;

  inFlight = fetchSiteContent()
    .then((content) => {
      // An empty payload is treated as "not loaded", not as "the admin deleted the whole site":
      // an unmigrated database would otherwise blank every public page.
      if (!content.sections || content.sections.length === 0) return FALLBACK;
      return { sections: content.sections, loading: false, stale: false };
    })
    .catch(() => FALLBACK)
    .then((state) => {
      cache = state;
      inFlight = null;
      subscribers.forEach((notify) => notify(state));
      return state;
    });

  return inFlight;
};

/** Drops the cache so the next mount refetches — used after an admin saves an edit. */
export const invalidateSiteContent = () => {
  cache = null;
};

export const useSiteContent = (): SiteContentState => {
  const [state, setState] = useState<SiteContentState>(
    () => cache ?? { ...FALLBACK, loading: true, stale: false }
  );

  useEffect(() => {
    if (cache) {
      setState(cache);
      return;
    }

    let active = true;
    const notify = (next: SiteContentState) => {
      if (active) setState(next);
    };
    subscribers.add(notify);
    load();

    return () => {
      active = false;
      subscribers.delete(notify);
    };
  }, []);

  return state;
};

/**
 * One section by key, or the shipped default for that key when the admin has hidden it or the
 * fetch failed — callers render a block, so they need something to render either way. Returns
 * null only for a key that does not exist at all, which means the caller's block is switched off.
 */
export const useSection = (sectionKey: string): ContentSection | null => {
  const { sections, stale } = useSiteContent();
  const live = sections.find((section) => section.sectionKey === sectionKey);
  if (live) return live;
  // A section missing from a *successful* fetch was hidden deliberately; only fall back to the
  // shipped copy when the fetch itself failed.
  if (!stale) return null;
  return FALLBACK_CONTENT.find((section) => section.sectionKey === sectionKey) ?? null;
};

/** Every section of one page, in the order an admin arranged them. */
export const usePageSections = (pageKey: string): ContentSection[] => {
  const { sections } = useSiteContent();
  return sections
    .filter((section) => section.pageKey === pageKey)
    .sort((a, b) => a.columnIndex - b.columnIndex || a.sortOrder - b.sortOrder);
};
