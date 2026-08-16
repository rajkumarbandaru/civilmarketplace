import React, { useMemo, useState } from 'react';
import { TableCell, TableSortLabel } from '@mui/material';
import { SxProps, Theme } from '@mui/material/styles';

/**
 * Click-to-sort for the admin console's tables.
 *
 * Every screen had its own fixed order — categories by name, bookings by date, invoices by
 * whatever the API returned — and no way to ask a different question of the same list ("who has
 * the most bookings", "which invoice is largest"). Rather than eleven copies of the same compare
 * function, the sorting lives here and each table only declares how to read a column's value.
 *
 * Sorting is client-side over the rows already fetched. That is honest for these screens because
 * they load a full page of rows at a time; if a table ever moves to server-side paging its sort
 * has to move with it, or it will only ever sort the visible page.
 */

export type SortDirection = 'asc' | 'desc';

/** What a column sorts on. Strings compare with localeCompare, everything else with < / >. */
export type SortValue = string | number | boolean | Date | null | undefined;

export interface SortState<K extends string> {
  key: K | null;
  direction: SortDirection;
}

/** Absent, as opposed to zero or false — both of which are real values and sort normally. */
export const isBlank = (value: SortValue): boolean =>
  value === null || value === undefined || value === '';

/** Orders two present values. Blanks are handled by sortRows, not here. */
export const compareValues = (a: SortValue, b: SortValue): number => {
  if (a instanceof Date || b instanceof Date) {
    return new Date(a as Date).getTime() - new Date(b as Date).getTime();
  }
  if (typeof a === 'number' && typeof b === 'number') return a - b;
  if (typeof a === 'boolean' && typeof b === 'boolean') return Number(a) - Number(b);
  // numeric: true so "Invoice 2" sorts before "Invoice 10", which is what an admin reading an
  // id or a house number expects.
  return String(a).localeCompare(String(b), undefined, { numeric: true, sensitivity: 'base' });
};

/** The sort itself, kept separate from the hook so it can be exercised without a React renderer. */
export function sortRows<T>(
  rows: T[],
  read: (row: T) => SortValue,
  direction: SortDirection,
): T[] {
  const factor = direction === 'asc' ? 1 : -1;

  // Rows with no value are held out of the sort and appended, so they sit at the bottom in BOTH
  // directions: an unset value is not "smallest", it is absent, and an admin flipping to
  // descending wants the largest rows first, not a block of blanks. Doing this inside the
  // comparator instead does not work — the direction factor negates its result along with
  // everything else, which floated every blank to the top on the second click.
  const present: T[] = [];
  const blanks: T[] = [];
  rows.forEach((row) => (isBlank(read(row)) ? blanks : present).push(row));

  // Sorting a copy: the caller's array is often state, and sorting it in place makes React miss
  // the change.
  present.sort((a, b) => compareValues(read(a), read(b)) * factor);
  return [...present, ...blanks];
}

/**
 * Sorts `rows` by the currently selected column.
 *
 * @param rows      the list as fetched; never mutated
 * @param accessors one function per sortable column key, returning the value to compare
 * @param initial   column to sort by on first render, and its direction
 */
// The column keys are inferred from `accessors`, not from `initial`: writing the accessor map is
// what declares which columns are sortable, and inferring from the optional initial key collapsed
// the type to that one literal.
export function useTableSort<T, A extends Record<string, (row: T) => SortValue>>(
  rows: T[],
  accessors: A,
  initial?: { key: Extract<keyof A, string>; direction?: SortDirection },
) {
  type K = Extract<keyof A, string>;
  const [sort, setSort] = useState<SortState<K>>({
    key: initial?.key ?? null,
    direction: initial?.direction ?? 'asc',
  });

  const onSort = (key: K) =>
    setSort((current) =>
      current.key === key
        ? { key, direction: current.direction === 'asc' ? 'desc' : 'asc' }
        : // A fresh column starts ascending — except dates, where "latest first" is the useful
          // first look, so tables pass a desc default for those via `initial` and get asc on the
          // second click.
          { key, direction: 'asc' },
    );

  const sorted = useMemo(() => {
    if (!sort.key) return rows;
    const read = accessors[sort.key];
    if (!read) return rows;
    return sortRows(rows, read, sort.direction);
    // accessors is rebuilt every render by callers; keying on the sort state and the rows is
    // enough, and depending on the object identity would re-sort on every render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rows, sort.key, sort.direction]);

  return { sorted, sort, onSort };
}

interface SortableTableCellProps<K extends string> {
  columnKey: K;
  sort: SortState<K>;
  onSort: (key: K) => void;
  children: React.ReactNode;
  align?: 'left' | 'right' | 'center';
  sx?: SxProps<Theme>;
}

/** A header cell that sorts the table when clicked. Drop-in replacement for a plain TableCell. */
export function SortableTableCell<K extends string>({
  columnKey, sort, onSort, children, align, sx,
}: SortableTableCellProps<K>) {
  const active = sort.key === columnKey;
  return (
    <TableCell
      align={align}
      sortDirection={active ? sort.direction : false}
      sx={{ fontWeight: 700, whiteSpace: 'nowrap', ...sx }}
    >
      <TableSortLabel
        active={active}
        direction={active ? sort.direction : 'asc'}
        onClick={() => onSort(columnKey)}
      >
        {children}
      </TableSortLabel>
    </TableCell>
  );
}
