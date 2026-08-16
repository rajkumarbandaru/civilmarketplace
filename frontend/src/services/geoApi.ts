import api from './api';

/**
 * Country / state / city lists for the address pickers.
 *
 * Served by user-service from a bundled list rather than a third-party geography API, so the
 * address step of a booking cannot be blocked by somebody else's rate limit. Public, so the
 * register form can use it too.
 */

export interface Country {
  code: string;
  name: string;
  dialCode: string;
}

export interface State {
  code: string;
  name: string;
}

export const fetchCountries = async (): Promise<Country[]> => {
  const { data } = await api.get<Country[]>('/geo/countries');
  return data;
};

export const fetchStates = async (countryCode: string): Promise<State[]> => {
  const { data } = await api.get<State[]>(`/geo/countries/${countryCode}/states`);
  return data;
};

export const fetchCities = async (countryCode: string, state: string): Promise<string[]> => {
  const { data } = await api.get<string[]>(`/geo/countries/${countryCode}/cities`, {
    params: { state },
  });
  return data;
};
