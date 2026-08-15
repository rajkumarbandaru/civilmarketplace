import React, { useMemo } from 'react';
import { Box, MenuItem, TextField, Select, SelectChangeEvent } from '@mui/material';
import {
  AsYouType,
  getCountries,
  getCountryCallingCode,
  CountryCode,
} from 'libphonenumber-js';

export interface PhoneNumberFieldProps {
  /** ISO-3166 alpha-2 country, e.g. "IN". */
  country: CountryCode;
  onCountryChange: (country: CountryCode) => void;
  /** National-format digits as typed, e.g. "98765 43210". */
  value: string;
  onChange: (value: string) => void;
  onBlur?: () => void;
  label?: string;
  error?: boolean;
  helperText?: React.ReactNode;
  required?: boolean;
}

/** Display names for the country list, falling back to the raw ISO code. */
const regionNames =
  typeof Intl !== 'undefined' && 'DisplayNames' in Intl
    ? new Intl.DisplayNames(['en'], { type: 'region' })
    : undefined;

/**
 * Phone input with a country-code selector and live national-format formatting.
 *
 * The country and the national number are kept separate so the value the user sees
 * stays readable while the caller can still build a single E.164 string to submit.
 */
const PhoneNumberField: React.FC<PhoneNumberFieldProps> = ({
  country,
  onCountryChange,
  value,
  onChange,
  onBlur,
  label = 'Phone',
  error,
  helperText,
  required,
}) => {
  const countries = useMemo(() => {
    return getCountries()
      .map((code) => ({
        code,
        callingCode: getCountryCallingCode(code),
        name: regionNames?.of(code) || code,
      }))
      .sort((a, b) => a.name.localeCompare(b.name));
  }, []);

  const handleNumberChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const raw = event.target.value;

    // AsYouType only ever adds separators, so a backspace that lands on one would be
    // undone by re-formatting. Detect deletion and pass the raw text straight through.
    const deleting = raw.length < value.length;
    if (deleting) {
      onChange(raw);
      return;
    }

    onChange(new AsYouType(country).input(raw));
  };

  const handleCountryChange = (event: SelectChangeEvent<string>) => {
    const next = event.target.value as CountryCode;
    onCountryChange(next);
    // Re-format what is already typed for the newly selected country.
    onChange(new AsYouType(next).input(value.replace(/\D/g, '')));
  };

  return (
    <Box sx={{ display: 'flex', gap: 1, alignItems: 'flex-start' }}>
      <Select
        value={country}
        onChange={handleCountryChange}
        error={error}
        // The dropdown holds ~240 entries; cap the popup so it never fills the page.
        MenuProps={{ PaperProps: { sx: { maxHeight: 320 } } }}
        renderValue={(code) => `${code} +${getCountryCallingCode(code as CountryCode)}`}
        sx={{ minWidth: 120, flexShrink: 0 }}
        inputProps={{ 'aria-label': 'Country calling code' }}
      >
        {countries.map((c) => (
          <MenuItem key={c.code} value={c.code}>
            {c.name} (+{c.callingCode})
          </MenuItem>
        ))}
      </Select>

      <TextField
        fullWidth
        label={label}
        required={required}
        value={value}
        onChange={handleNumberChange}
        onBlur={onBlur}
        error={error}
        helperText={helperText}
        inputProps={{ inputMode: 'tel', autoComplete: 'tel-national' }}
      />
    </Box>
  );
};

export default PhoneNumberField;
