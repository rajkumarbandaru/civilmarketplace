import React, { useEffect, useState } from 'react';
import { Autocomplete, Grid, TextField } from '@mui/material';
import { Country, State, fetchCities, fetchCountries, fetchStates } from '../services/geoApi';

/**
 * Country → state → city, each list narrowed by the one above it.
 *
 * The city used to be free text, which is how a booking ends up in "hyd", "Hyd." and "Hyderabad"
 * at once — three cities as far as any search or report is concerned. Picking from a list makes
 * the value consistent without asking the customer to spell it the platform's way.
 *
 * Still an {@link Autocomplete} rather than a plain select: 302 Indian cities is far too many to
 * scroll, and typing two letters is faster than any dropdown.
 */

export interface AddressValue {
  /** ISO code, as the API expects it. */
  country: string;
  /** The country's display name, so a caller can print the address without a second lookup. */
  countryName?: string;
  state: string;
  city: string;
}

interface Props {
  value: AddressValue;
  onChange: (value: AddressValue) => void;
  /** Shown under the city field — the form's own validation message. */
  cityError?: string;
}

const AddressSelect: React.FC<Props> = ({ value, onChange, cityError }) => {
  const [countries, setCountries] = useState<Country[]>([]);
  const [states, setStates] = useState<State[]>([]);
  const [cities, setCities] = useState<string[]>([]);
  const [loadingStates, setLoadingStates] = useState(false);
  const [loadingCities, setLoadingCities] = useState(false);

  useEffect(() => {
    let active = true;
    fetchCountries()
      .then((rows) => {
        if (!active) return;
        setCountries(rows);
        // India is where the platform operates, so it is preselected when nothing is chosen yet —
        // one fewer tap for almost every customer, and still changeable.
        const india = rows.find((c) => c.code === 'IN');
        if (!value.country && india) {
          onChange({ ...value, country: india.code, countryName: india.name });
        }
      })
      // A failed list leaves the picker empty rather than blocking the step; the fields below it
      // still accept a typed value.
      .catch(() => undefined);
    return () => {
      active = false;
    };
    // Runs once: the preselect must not re-fire whenever the parent re-renders.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!value.country) {
      setStates([]);
      return;
    }
    let active = true;
    setLoadingStates(true);
    fetchStates(value.country)
      .then((rows) => active && setStates(rows))
      .catch(() => active && setStates([]))
      .finally(() => active && setLoadingStates(false));
    return () => {
      active = false;
    };
  }, [value.country]);

  useEffect(() => {
    if (!value.country || !value.state) {
      setCities([]);
      return;
    }
    let active = true;
    setLoadingCities(true);
    fetchCities(value.country, value.state)
      .then((rows) => active && setCities(rows))
      .catch(() => active && setCities([]))
      .finally(() => active && setLoadingCities(false));
    return () => {
      active = false;
    };
  }, [value.country, value.state]);

  return (
    <Grid container spacing={2} sx={{ mb: 1 }}>
      <Grid item xs={12} sm={4}>
        <Autocomplete
          options={countries}
          getOptionLabel={(option) => option.name}
          value={countries.find((c) => c.code === value.country) ?? null}
          // Changing the country invalidates both fields below it — leaving "Telangana" under
          // "Canada" is worse than clearing them.
          onChange={(_, option) =>
            onChange({
              country: option?.code ?? '',
              countryName: option?.name ?? '',
              state: '',
              city: '',
            })
          }
          renderInput={(params) => <TextField {...params} label="Country" />}
        />
      </Grid>
      <Grid item xs={12} sm={4}>
        <Autocomplete
          options={states}
          loading={loadingStates}
          disabled={!value.country}
          getOptionLabel={(option) => option.name}
          value={states.find((s) => s.name === value.state) ?? null}
          onChange={(_, option) =>
            onChange({ ...value, state: option?.name ?? '', city: '' })
          }
          renderInput={(params) => <TextField {...params} label="State" />}
        />
      </Grid>
      <Grid item xs={12} sm={4}>
        <Autocomplete
          options={cities}
          loading={loadingCities}
          disabled={!value.state}
          // A city not on the list is still a real city — freeSolo keeps the picker from being a
          // wall for a village the dataset has never heard of.
          freeSolo
          value={value.city || null}
          onChange={(_, option) => onChange({ ...value, city: option ?? '' })}
          onInputChange={(_, input, reason) => {
            if (reason === 'input') onChange({ ...value, city: input });
          }}
          renderInput={(params) => (
            <TextField
              {...params}
              label="City"
              error={!!cityError}
              helperText={cityError}
            />
          )}
        />
      </Grid>
    </Grid>
  );
};

export default AddressSelect;
