import { useEffect, useState } from 'react';
import {
  FALLBACK_CATEGORIES,
  FALLBACK_SERVICES,
  ServiceCategory,
  ServiceEntry,
} from '../constants/serviceCatalogue';
import { fetchCatalogue } from '../services/catalogueApi';

/**
 * The live service catalogue, shared by every surface that lists services.
 *
 * Four components need it (services page, booking page, footer, global search) and it changes only
 * when an admin edits it, so the result is cached at module level and the request is made once per
 * page load — four independent fetches of the same hundred rows on every navigation is not a cost
 * worth paying for a list this stable.
 *
 * On failure it falls back to the list the site shipped with. A catalogue page that renders nothing
 * because one request timed out is worse than one showing a slightly stale list, and the fallback
 * is exactly what the site displayed before the catalogue became editable.
 */

interface CatalogueState {
  services: ServiceEntry[];
  categories: ServiceCategory[];
  loading: boolean;
  /** True when the API could not be reached and the shipped list is on screen instead. */
  stale: boolean;
}

const FALLBACK: CatalogueState = {
  services: FALLBACK_SERVICES,
  categories: FALLBACK_CATEGORIES,
  loading: false,
  stale: true,
};

let cache: CatalogueState | null = null;
let inFlight: Promise<CatalogueState> | null = null;
const subscribers = new Set<(state: CatalogueState) => void>();

const load = (): Promise<CatalogueState> => {
  if (inFlight) return inFlight;

  inFlight = fetchCatalogue()
    .then((catalogue) => {
      // An empty catalogue is treated as a failure to load, not as "the marketplace sells nothing":
      // a fresh database that has not been seeded yet would otherwise blank the whole site.
      if (catalogue.services.length === 0) return FALLBACK;
      return {
        services: catalogue.services,
        categories: catalogue.categories.map((category) => category.name),
        loading: false,
        stale: false,
      };
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

/** Drops the cache so the next mount refetches — used after an admin edits the catalogue. */
export const invalidateCatalogue = () => {
  cache = null;
};

export const useCatalogue = (): CatalogueState => {
  const [state, setState] = useState<CatalogueState>(
    // Served from cache on later mounts, so navigating back to the services page is instant.
    () => cache ?? { ...FALLBACK, loading: true, stale: false }
  );

  useEffect(() => {
    if (cache) {
      setState(cache);
      return;
    }

    let active = true;
    const notify = (next: CatalogueState) => {
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
