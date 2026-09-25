import React from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Alert, Box, Button, Card, CardContent, Chip, Stack, Table, TableBody, TableCell, TableHead, TableRow, Typography,
} from '@mui/material';
import { apiErrorMessage } from '../../services/apiError';
import { CAPABILITY_LABELS, MigrationReport, migrateAccounts } from '../../services/procurementApi';

const OUTCOME: Record<string, { label: string; color: 'success' | 'info' | 'default' }> = {
  CREATED: { label: 'Created', color: 'success' },
  WOULD_CREATE: { label: 'Will be created', color: 'info' },
  ALREADY_MIGRATED: { label: 'Already has one', color: 'default' },
};

/**
 * Staff: turn every material-supplier, labour-contractor and equipment-rental account into an
 * organization at once. Always previewed first; running it again only adds accounts added since.
 */
const MigrationCard: React.FC = () => {
  const queryClient = useQueryClient();
  const preview = useMutation({ mutationFn: () => migrateAccounts(true) });
  const run = useMutation({
    mutationFn: () => migrateAccounts(false),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['procurement-directory'] }),
  });
  const report: MigrationReport | undefined = run.data ?? preview.data;

  return (
    <Card data-testid="party-migration">
      <CardContent>
        <Typography variant="h6">Bring existing traders in</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Every material supplier, labour contractor and equipment rental account becomes an organization it owns, so it
          can quote and receive orders without registering again. Suppliers' published material rates become their catalogue.
        </Typography>
        <Stack direction="row" spacing={1}>
          <Button variant="outlined" disabled={preview.isPending} onClick={() => { run.reset(); preview.mutate(); }}>Preview</Button>
          <Button variant="contained" disabled={!preview.data || preview.data.created === 0 || run.isPending || !!run.data}
            onClick={() => run.mutate()}>
            {run.isPending ? 'Migrating…' : preview.data ? `Create ${preview.data.created} organization${preview.data.created === 1 ? '' : 's'}` : 'Create organizations'}
          </Button>
        </Stack>
        {(preview.error || run.error) && (
          <Alert severity="error" sx={{ mt: 2 }}>{apiErrorMessage(preview.error || run.error, 'That did not work.')}</Alert>
        )}
        {report && (
          <Box sx={{ mt: 2, overflowX: 'auto' }}>
            <Typography variant="body2" sx={{ mb: 1 }} data-testid="migration-summary">
              {report.dryRun ? 'Preview: ' : 'Done: '}{report.accounts} account{report.accounts === 1 ? '' : 's'},{' '}
              {report.created} {report.dryRun ? 'to create' : 'created'}, {report.alreadyMigrated} already migrated.
            </Typography>
            <Table size="small">
              <TableHead>
                <TableRow><TableCell>Account</TableCell><TableCell>Organization</TableCell><TableCell>Does</TableCell>
                  <TableCell align="right">Catalogue items</TableCell><TableCell /></TableRow>
              </TableHead>
              <TableBody>
                {report.entries.map((e) => (
                  <TableRow key={e.userId}>
                    <TableCell>{e.email}</TableCell>
                    <TableCell>{e.name}</TableCell>
                    <TableCell>{e.capabilities.map((c) => CAPABILITY_LABELS[c]).join(', ')}</TableCell>
                    <TableCell align="right">{e.catalogueItems || '—'}</TableCell>
                    <TableCell><Chip size="small" color={OUTCOME[e.outcome].color} label={OUTCOME[e.outcome].label} /></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Box>
        )}
      </CardContent>
    </Card>
  );
};

export default MigrationCard;
