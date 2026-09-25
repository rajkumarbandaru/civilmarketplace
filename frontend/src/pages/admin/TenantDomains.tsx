import React, { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert, Box, Button, Card, CardContent, Chip, IconButton, Stack, Table, TableBody, TableCell, TableHead, TableRow,
  TextField, Tooltip, Typography,
} from '@mui/material';
import { ContentCopy as CopyIcon } from '@mui/icons-material';
import { useDateTime } from '../../providers/UiConfigProvider';
import { apiErrorMessage } from '../../services/apiError';
import {
  DomainStatus, Tenant, TenantDomain, addDomain, checkDomain, fetchDomains, removeDomain,
} from '../../services/tenantApi';

const STATUS: Record<DomainStatus, { label: string; color: 'default' | 'info' | 'success' | 'warning' | 'error' }> = {
  PENDING_VERIFICATION: { label: 'Waiting for DNS', color: 'info' },
  VERIFIED: { label: 'Verified', color: 'info' },
  CERT_ISSUING: { label: 'Getting certificate', color: 'info' },
  ACTIVE: { label: 'Live', color: 'success' },
  DEGRADED: { label: 'DNS check failing', color: 'warning' },
  FAILED: { label: 'Failed', color: 'error' },
  REMOVED: { label: 'Removed', color: 'default' },
};
const WORKING: DomainStatus[] = ['PENDING_VERIFICATION', 'VERIFIED', 'CERT_ISSUING'];

const DomainRow: React.FC<{ domain: TenantDomain; tenantKey: string; onChanged: () => void }> = ({ domain, tenantKey, onChanged }) => {
  const { formatDateTime } = useDateTime();
  const check = useMutation({ mutationFn: () => checkDomain(tenantKey, domain.id), onSuccess: onChanged });
  const remove = useMutation({ mutationFn: () => removeDomain(tenantKey, domain.id), onSuccess: onChanged });
  const s = STATUS[domain.status];
  const needsDns = domain.status === 'PENDING_VERIFICATION' || domain.status === 'DEGRADED' || domain.status === 'FAILED';
  return (
    <Box data-testid={`domain-${domain.host}`} sx={{ py: 1.5, borderTop: 1, borderColor: 'divider' }}>
      <Stack direction="row" spacing={1} alignItems="center" sx={{ flexWrap: 'wrap', rowGap: 1 }}>
        <Typography sx={{ fontWeight: 600 }}>{domain.host}</Typography>
        <Chip size="small" color={s.color} label={s.label} />
        {domain.status === 'ACTIVE' && domain.certNotAfter && (
          <Typography variant="caption" color="text.secondary">certificate until {formatDateTime(domain.certNotAfter)}</Typography>
        )}
        <Box sx={{ flex: 1 }} />
        {domain.status !== 'ACTIVE' && (
          <Button size="small" disabled={check.isPending} onClick={() => check.mutate()}>Check now</Button>
        )}
        <Button size="small" color="error" disabled={remove.isPending} onClick={() => remove.mutate()}>Remove</Button>
      </Stack>
      {domain.lastError && domain.status !== 'ACTIVE' && (
        <Alert severity={domain.status === 'FAILED' ? 'error' : domain.status === 'DEGRADED' ? 'warning' : 'info'} sx={{ mt: 1 }}>
          {domain.lastError}
        </Alert>
      )}
      {needsDns && (
        <Box sx={{ mt: 1, overflowX: 'auto' }}>
          <Typography variant="body2" sx={{ mb: 0.5 }}>Ask the tenant to add these records at their DNS provider:</Typography>
          <Table size="small">
            <TableHead>
              <TableRow><TableCell>Type</TableCell><TableCell>Name</TableCell><TableCell>Value</TableCell><TableCell>Why</TableCell></TableRow>
            </TableHead>
            <TableBody>
              {domain.records.map((r) => (
                <TableRow key={r.type}>
                  <TableCell>{r.type}</TableCell>
                  <TableCell sx={{ fontFamily: 'monospace' }}>{r.name}</TableCell>
                  <TableCell sx={{ fontFamily: 'monospace' }} data-testid={`record-${r.type}`}>
                    {r.value}
                    <Tooltip title="Copy">
                      <IconButton size="small" aria-label={`Copy ${r.type} value`} onClick={() => navigator.clipboard?.writeText(r.value)}>
                        <CopyIcon fontSize="inherit" />
                      </IconButton>
                    </Tooltip>
                  </TableCell>
                  <TableCell>{r.purpose}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Box>
      )}
      {(check.isError || remove.isError) && (
        <Alert severity="error" sx={{ mt: 1 }}>{apiErrorMessage(check.error || remove.error, 'That did not work.')}</Alert>
      )}
    </Box>
  );
};

/**
 * A tenant's own host names: added here, proven by the tenant with a DNS record, then certified
 * automatically (ACME) and served. The tenant stays reachable on its platform subdomain throughout.
 */
const DomainsCard: React.FC<{ tenant: Tenant }> = ({ tenant }) => {
  const queryClient = useQueryClient();
  const [host, setHost] = useState('');
  const domains = useQuery({
    queryKey: ['domains', tenant.tenantKey],
    queryFn: () => fetchDomains(tenant.tenantKey),
    retry: false,
    refetchInterval: (q) => (q.state.data?.some((d) => WORKING.includes(d.status)) ? 3000 : false),
  });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['domains', tenant.tenantKey] });
  const add = useMutation({
    mutationFn: () => addDomain(tenant.tenantKey, host.trim()),
    onSuccess: () => { setHost(''); refresh(); },
  });
  if (tenant.tenantKey === 'platform') return null;
  return (
    <Card sx={{ mb: 3 }} data-testid="domains-card">
      <CardContent>
        <Typography variant="h6">Custom domains</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Serve this workspace on the tenant's own domain. It keeps working on its platform address meanwhile; the domain goes
          live once its DNS proves ownership and a certificate has been issued.
        </Typography>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ mb: 1 }}>
          <TextField size="small" label="Domain" placeholder="www.example.com" value={host}
            onChange={(e) => setHost(e.target.value)} sx={{ flex: 1, maxWidth: 420 }} />
          <Button variant="outlined" disabled={!host.trim() || add.isPending} onClick={() => add.mutate()}>Add domain</Button>
        </Stack>
        {add.isError && <Alert severity="error" sx={{ mb: 1 }}>{apiErrorMessage(add.error, 'The domain was not added.')}</Alert>}
        {domains.data?.map((d) => <DomainRow key={d.id} domain={d} tenantKey={tenant.tenantKey} onChanged={refresh} />)}
      </CardContent>
    </Card>
  );
};

export default DomainsCard;
