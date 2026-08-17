import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Box, Card, CardContent, Typography, Grid, Button, Chip, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, Skeleton, Snackbar, Alert, Stack, Divider,
} from '@mui/material';
import { Assessment, Download, Refresh, TableChart } from '@mui/icons-material';
import { reportApi, ReportDefinition, ReportPreview } from '../../services/adminApi';
import { normalizeApiError } from '../../services/apiError';
import { SortableTableCell, useTableSort } from '../../components/admin/SortableTable';

/**
 * The reports an admin can take off the platform.
 *
 * The catalogue is served by admin-service rather than listed here, so a report added on the
 * backend appears without a frontend release — and, more importantly, so the columns shown in the
 * preview are the same columns the CSV writes.
 */
const ReportsPage: React.FC = () => {
  const [reports, setReports] = useState<ReportDefinition[]>([]);
  const [loadingCatalogue, setLoadingCatalogue] = useState(true);
  const [selectedKey, setSelectedKey] = useState<string | null>(null);
  const [preview, setPreview] = useState<ReportPreview | null>(null);

  // The columns differ per report, so the accessors are built from whatever this preview returned
  // rather than declared up front. Values arrive as strings; the shared comparator sorts them
  // numerically when they look like numbers, so an "Amount" column still orders sensibly.
  const previewRows = preview?.rows ?? [];
  const sortAccessors = useMemo(
    () => Object.fromEntries(
      (preview?.columns ?? []).map((column) => [column, (row: Record<string, string>) => row[column]])
    ) as Record<string, (row: Record<string, string>) => string>,
    [preview?.columns]
  );
  /**
   * Which column a report opens sorted on, chosen from the names it returned: a date if it has
   * one, otherwise an id, otherwise the first column. Every report here is a list of things that
   * happened — bookings, payments, signups — so its newest rows are the ones being checked, and
   * ordering by whatever column happened to come back first was arbitrary.
   */
  const defaultSortColumn = useMemo(() => {
    const columns = preview?.columns ?? [];
    const matching = (pattern: RegExp) => columns.find((column) => pattern.test(column));
    return matching(/date|time|when|created|updated|issued|joined|at$/i)
      ?? matching(/(^|\b|_)(id|code|no|number)(\b|_|$)/i)
      ?? columns[0];
  }, [preview?.columns]);

  const { sorted, sort, onSort } = useTableSort(
    previewRows,
    sortAccessors,
    defaultSortColumn ? { key: defaultSortColumn, direction: 'desc' as const } : undefined,
    // The columns change with the report, so the default has to be re-applied when they do.
    (preview?.columns ?? []).join('|'),
  );
  const [loadingPreview, setLoadingPreview] = useState(false);
  const [downloading, setDownloading] = useState<string | null>(null);
  const [toast, setToast] = useState<{ message: string; severity: 'success' | 'error' } | null>(null);

  const loadPreview = useCallback(async (key: string) => {
    try {
      setLoadingPreview(true);
      setSelectedKey(key);
      const response = await reportApi.getReportPreview(key);
      setPreview(response.data.data);
    } catch (err) {
      setPreview(null);
      setToast({ message: normalizeApiError(err, 'The report could not be generated').message, severity: 'error' });
    } finally {
      setLoadingPreview(false);
    }
  }, []);

  useEffect(() => {
    const loadCatalogue = async () => {
      try {
        setLoadingCatalogue(true);
        const response = await reportApi.getReports();
        const catalogue = response.data.data?.reports ?? [];
        setReports(catalogue);
        // Opening straight onto the first report means the screen is never an empty frame with a
        // list of things to click — the common case is "show me the numbers".
        if (catalogue.length > 0) loadPreview(catalogue[0].key);
      } catch (err) {
        setToast({ message: normalizeApiError(err, 'Reports could not be loaded').message, severity: 'error' });
      } finally {
        setLoadingCatalogue(false);
      }
    };
    loadCatalogue();
  }, [loadPreview]);

  /**
   * The CSV is fetched as a blob and handed to a temporary link rather than navigating to the
   * export URL: the request needs the auth header the axios client adds, which a plain link
   * would not carry.
   */
  const handleDownload = async (report: ReportDefinition) => {
    try {
      setDownloading(report.key);
      const response = await reportApi.exportReport(report.key);
      const url = URL.createObjectURL(new Blob([response.data], { type: 'text/csv;charset=utf-8' }));
      const link = document.createElement('a');
      link.href = url;
      link.download = `${report.key}-${new Date().toISOString().slice(0, 10)}.csv`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
      setToast({ message: `${report.label} exported`, severity: 'success' });
    } catch (err) {
      setToast({ message: normalizeApiError(err, 'The export failed').message, severity: 'error' });
    } finally {
      setDownloading(null);
    }
  };

  const grouped = useMemo(() => {
    const byCategory = new Map<string, ReportDefinition[]>();
    reports.forEach((report) => {
      const list = byCategory.get(report.category) ?? [];
      list.push(report);
      byCategory.set(report.category, list);
    });
    return Array.from(byCategory.entries());
  }, [reports]);

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 800, mb: 0.5 }}>Reports</Typography>
          <Typography variant="body2" color="text.secondary">
            Preview any report on screen, or export the full data as CSV
          </Typography>
        </Box>
        <Button
          startIcon={<Refresh />}
          variant="outlined"
          disabled={!selectedKey || loadingPreview}
          onClick={() => selectedKey && loadPreview(selectedKey)}
          sx={{ borderRadius: 2 }}
        >
          Refresh
        </Button>
      </Box>

      <Grid container spacing={3}>
        <Grid item xs={12} md={4}>
          <Card sx={{ borderRadius: 3 }}>
            <Box sx={{ p: 2.5, borderBottom: 1, borderColor: 'divider' }}>
              <Typography variant="h6" sx={{ fontWeight: 700 }}>Available reports</Typography>
            </Box>
            <CardContent sx={{ p: 2 }}>
              {loadingCatalogue ? (
                Array.from({ length: 5 }).map((_, idx) => <Skeleton key={idx} height={62} />)
              ) : reports.length === 0 ? (
                <Typography variant="body2" color="text.secondary" sx={{ p: 2 }}>
                  No reports are available.
                </Typography>
              ) : (
                grouped.map(([category, items], groupIdx) => (
                  <Box key={category} sx={{ mb: groupIdx < grouped.length - 1 ? 2 : 0 }}>
                    <Typography
                      variant="caption"
                      sx={{ px: 1, fontWeight: 700, color: 'text.secondary', letterSpacing: 0.6 }}
                    >
                      {category.toUpperCase()}
                    </Typography>
                    {items.map((report) => {
                      const active = report.key === selectedKey;
                      return (
                        <Box
                          key={report.key}
                          onClick={() => loadPreview(report.key)}
                          sx={{
                            mt: 1, p: 1.5, borderRadius: 2, cursor: 'pointer',
                            border: 1,
                            borderColor: active ? 'primary.main' : 'divider',
                            bgcolor: active ? 'action.selected' : 'transparent',
                            '&:hover': { bgcolor: 'action.hover' },
                          }}
                        >
                          <Stack direction="row" spacing={1.5} alignItems="flex-start">
                            <Assessment fontSize="small" sx={{ mt: 0.3, color: active ? 'primary.main' : 'text.secondary' }} />
                            <Box sx={{ minWidth: 0 }}>
                              <Typography variant="body2" sx={{ fontWeight: 600 }}>{report.label}</Typography>
                              <Typography variant="caption" color="text.secondary">
                                {report.description}
                              </Typography>
                            </Box>
                          </Stack>
                        </Box>
                      );
                    })}
                  </Box>
                ))
              )}
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={8}>
          <Card sx={{ borderRadius: 3 }}>
            <Box sx={{ p: 2.5, borderBottom: 1, borderColor: 'divider', display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 2 }}>
              <Box sx={{ minWidth: 0 }}>
                <Typography variant="h6" sx={{ fontWeight: 700 }} noWrap>
                  {preview?.label ?? 'Preview'}
                </Typography>
                {preview && (
                  <Typography variant="caption" color="text.secondary">
                    Showing {preview.rows.length} of {preview.totalRows} rows · generated {preview.generatedAt}
                  </Typography>
                )}
              </Box>
              <Button
                startIcon={<Download />}
                variant="contained"
                disabled={!selectedKey || downloading !== null}
                onClick={() => {
                  const report = reports.find((r) => r.key === selectedKey);
                  if (report) handleDownload(report);
                }}
                sx={{ borderRadius: 2, flexShrink: 0 }}
              >
                {downloading ? 'Exporting…' : 'Export CSV'}
              </Button>
            </Box>

            {loadingPreview ? (
              <Box sx={{ p: 3 }}>
                {Array.from({ length: 8 }).map((_, idx) => <Skeleton key={idx} height={36} />)}
              </Box>
            ) : !preview ? (
              <Box sx={{ p: 6, textAlign: 'center' }}>
                <TableChart sx={{ fontSize: 44, color: 'text.disabled' }} />
                <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                  Pick a report to preview it.
                </Typography>
              </Box>
            ) : preview.rows.length === 0 ? (
              <Box sx={{ p: 6, textAlign: 'center' }}>
                <Typography variant="body2" color="text.secondary">
                  This report has no rows yet.
                </Typography>
              </Box>
            ) : (
              <TableContainer sx={{ maxHeight: 560 }}>
                <Table stickyHeader size="small">
                  <TableHead>
                    <TableRow>
                      {preview.columns.map((column) => (
                        <SortableTableCell key={column} columnKey={column} sort={sort} onSort={onSort}>
                          {column}
                        </SortableTableCell>
                      ))}
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {sorted.map((row, idx) => (
                      <TableRow key={idx} hover>
                        {preview.columns.map((column) => (
                          <TableCell key={column} sx={{ whiteSpace: 'nowrap' }}>
                            <Typography variant="body2">{row[column] || '—'}</Typography>
                          </TableCell>
                        ))}
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}

            {preview && preview.totalRows > preview.rows.length && (
              <>
                <Divider />
                <Box sx={{ p: 2, display: 'flex', alignItems: 'center', gap: 1 }}>
                  <Chip size="small" label={`${preview.totalRows - preview.rows.length} more rows`} />
                  <Typography variant="caption" color="text.secondary">
                    The CSV export contains every row.
                  </Typography>
                </Box>
              </>
            )}
          </Card>
        </Grid>
      </Grid>

      <Snackbar
        open={toast !== null}
        autoHideDuration={4000}
        onClose={() => setToast(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      >
        <Alert severity={toast?.severity ?? 'success'} onClose={() => setToast(null)} variant="filled">
          {toast?.message}
        </Alert>
      </Snackbar>
    </Box>
  );
};

export default ReportsPage;
