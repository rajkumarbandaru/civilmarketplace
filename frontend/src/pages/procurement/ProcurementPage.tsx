import React, { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  Alert, Box, Button, Card, CardContent, Chip, CircularProgress, Container, Stack, Tab, Table, TableBody, TableCell,
  TableHead, TableRow, Tabs, Typography,
} from '@mui/material';
import { useDateTime } from '../../providers/UiConfigProvider';
import { apiErrorMessage } from '../../services/apiError';
import {
  PO_STATUS_LABELS, PoStatus, ROLE_CAPABILITIES, RfqStatus, createOrganization, createOrganizationFromProfile,
  fetchMyOrganizations, fetchPurchaseOrders, fetchRfqs, formatMoney, CAPABILITY_LABELS,
} from '../../services/procurementApi';
import { useAppSelector } from '../../hooks';
import { ADMIN_ROLES } from '../../components/AdminRoute';
import PriceListsPanel from './PriceListsPanel';
import MigrationCard from './MigrationCard';
import OrganizationsPanel, { OrganizationForm } from './OrganizationsPanel';
import NewRfqDialog from './NewRfqDialog';

export const RFQ_STATUS_COLOR: Record<RfqStatus, 'info' | 'success' | 'default'> = {
  OPEN: 'info', AWARDED: 'success', CANCELLED: 'default',
};
export const PO_STATUS_COLOR: Record<PoStatus, 'warning' | 'info' | 'success' | 'default' | 'error'> = {
  PENDING_APPROVAL: 'warning', ISSUED: 'info', ACKNOWLEDGED: 'info', PARTIALLY_RECEIVED: 'info',
  RECEIVED: 'success', CLOSED: 'default', CANCELLED: 'error',
};

const RoleChips: React.FC<{ roles: string[] }> = ({ roles }) => (
  <>{roles.map((r) => <Chip key={r} size="small" variant="outlined" label={r === 'BUYER' ? 'Buying' : 'Selling'} sx={{ mr: 0.5 }} />)}</>
);

const TABS = ['rfqs', 'orders', 'prices', 'organizations'] as const;
type TabKey = typeof TABS[number];

/**
 * B2B procurement: the RFQs and orders of every organization the signed-in person acts for, as
 * buyer and as supplier, and the organizations themselves.
 */
const ProcurementPage: React.FC = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { formatDate } = useDateTime();
  const [params, setParams] = useSearchParams();
  const tab: TabKey = (TABS as readonly string[]).includes(params.get('tab') ?? '') ? (params.get('tab') as TabKey) : 'rfqs';
  const [raising, setRaising] = useState(false);

  const orgs = useQuery({ queryKey: ['procurement-orgs'], queryFn: fetchMyOrganizations, retry: false });
  const hasOrgs = (orgs.data?.length ?? 0) > 0;
  const rfqs = useQuery({ queryKey: ['procurement-rfqs'], queryFn: fetchRfqs, enabled: hasOrgs && tab === 'rfqs' });
  const orders = useQuery({ queryKey: ['procurement-orders'], queryFn: fetchPurchaseOrders, enabled: hasOrgs && tab === 'orders' });
  const firstOrg = useMutation({
    mutationFn: createOrganization,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['procurement-orgs'] }),
  });
  const fromProfile = useMutation({
    mutationFn: createOrganizationFromProfile,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['procurement-orgs'] }),
  });
  const user = useAppSelector((s) => s.auth.user);
  const profileCaps = user?.role ? ROLE_CAPABILITIES[user.role] : undefined;
  const staff = !!user?.role && ADMIN_ROLES.includes(user.role);
  const buyers = orgs.data?.filter((o) => o.capabilities.includes('BUYER')) ?? [];

  if (orgs.isLoading) return <Container sx={{ py: 4 }}><CircularProgress /></Container>;
  if (orgs.isError) {
    return <Container sx={{ py: 4 }}><Alert severity="error">{apiErrorMessage(orgs.error, 'Procurement is unavailable.')}</Alert></Container>;
  }

  if (!hasOrgs) {
    return (
      <Container maxWidth="sm" sx={{ py: 4 }}>
        <Typography variant="h4" gutterBottom>Procurement</Typography>
        <Card data-testid="procurement-onboarding">
          <CardContent>
            <Typography variant="h6" gutterBottom>Set up your organization</Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              Buy materials from suppliers with RFQs and purchase orders, or sell to buyers here as a supplier.
              You can add colleagues once it exists.
            </Typography>
            {profileCaps && (
              <Box sx={{ mb: 3 }} data-testid="from-profile">
                <Alert severity="info" sx={{ mb: 1 }}>
                  Your account already trades as a {profileCaps.map((c) => CAPABILITY_LABELS[c].toLowerCase()).join(' and ')}.
                  Set up your organization from it{profileCaps.includes('SUPPLIER') ? ' — your published material rates become your catalogue' : ''}.
                </Alert>
                <Button variant="contained" disabled={fromProfile.isPending} onClick={() => fromProfile.mutate()}>
                  Set up from my profile
                </Button>
                {fromProfile.isError && (
                  <Alert severity="error" sx={{ mt: 1 }}>{apiErrorMessage(fromProfile.error, 'That did not work.')}</Alert>
                )}
                <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>Or register a different one:</Typography>
              </Box>
            )}
            <OrganizationForm submitLabel="Create organization" pending={firstOrg.isPending} error={firstOrg.error}
              onSubmit={(i) => firstOrg.mutate(i)} />
          </CardContent>
        </Card>
        {staff && <Box sx={{ mt: 3 }}><MigrationCard /></Box>}
      </Container>
    );
  }

  return (
    <Container maxWidth="lg" sx={{ py: 4 }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2, flexWrap: 'wrap', rowGap: 1 }}>
        <Typography variant="h4">Procurement</Typography>
        {buyers.length > 0 && <Button variant="contained" onClick={() => setRaising(true)}>New RFQ</Button>}
      </Stack>
      <Tabs value={tab} onChange={(_, v) => setParams({ tab: v })} sx={{ mb: 2 }} variant="scrollable">
        <Tab value="rfqs" label="RFQs" />
        <Tab value="orders" label="Purchase orders" />
        <Tab value="prices" label="Prices & contracts" />
        <Tab value="organizations" label="Organizations" />
      </Tabs>

      {tab === 'rfqs' && (
        <Box sx={{ overflowX: 'auto' }}>
          {rfqs.isLoading && <CircularProgress size={24} />}
          {rfqs.data?.length === 0 && <Typography color="text.secondary">No RFQs yet.</Typography>}
          {!!rfqs.data?.length && (
            <Table size="small" data-testid="rfq-table">
              <TableHead>
                <TableRow>
                  <TableCell>RFQ</TableCell><TableCell>Title</TableCell><TableCell>Buyer</TableCell>
                  <TableCell>Needed by</TableCell><TableCell>Status</TableCell><TableCell>You</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {rfqs.data.map((r) => (
                  <TableRow key={r.id} hover sx={{ cursor: 'pointer' }} onClick={() => navigate(`/procurement/rfqs/${r.id}`)}>
                    <TableCell>{r.number}</TableCell>
                    <TableCell>
                      {r.title}
                      {r.roles.includes('BUYER') && (
                        <Typography variant="caption" color="text.secondary" display="block">
                          {r.quotations} quotation{r.quotations === 1 ? '' : 's'}
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell>{r.buyer.name}</TableCell>
                    <TableCell>{r.neededBy ? formatDate(r.neededBy) : '—'}</TableCell>
                    <TableCell><Chip size="small" color={RFQ_STATUS_COLOR[r.status]} label={r.status.toLowerCase()} /></TableCell>
                    <TableCell><RoleChips roles={r.roles} /></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </Box>
      )}

      {tab === 'orders' && (
        <Box sx={{ overflowX: 'auto' }}>
          {orders.isLoading && <CircularProgress size={24} />}
          {orders.data?.length === 0 && <Typography color="text.secondary">No purchase orders yet.</Typography>}
          {!!orders.data?.length && (
            <Table size="small" data-testid="po-table">
              <TableHead>
                <TableRow>
                  <TableCell>Order</TableCell><TableCell>Buyer</TableCell><TableCell>Supplier</TableCell>
                  <TableCell align="right">Total</TableCell><TableCell>Status</TableCell><TableCell>You</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {orders.data.map((p) => (
                  <TableRow key={p.id} hover sx={{ cursor: 'pointer' }} onClick={() => navigate(`/procurement/orders/${p.id}`)}>
                    <TableCell>{p.number}</TableCell>
                    <TableCell>{p.buyer.name}</TableCell>
                    <TableCell>{p.supplier.name}</TableCell>
                    <TableCell align="right">{formatMoney(p.total)}</TableCell>
                    <TableCell><Chip size="small" color={PO_STATUS_COLOR[p.status]} label={PO_STATUS_LABELS[p.status]} /></TableCell>
                    <TableCell><RoleChips roles={p.roles} /></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </Box>
      )}

      {tab === 'prices' && <PriceListsPanel organizations={orgs.data ?? []} />}
      {tab === 'organizations' && (
        <>
          <OrganizationsPanel organizations={orgs.data ?? []} />
          {staff && <Box sx={{ mt: 3 }}><MigrationCard /></Box>}
        </>
      )}

      {raising && (
        <NewRfqDialog buyers={buyers} onClose={() => setRaising(false)} onCreated={(rfq) => {
          setRaising(false);
          queryClient.invalidateQueries({ queryKey: ['procurement-rfqs'] });
          navigate(`/procurement/rfqs/${rfq.id}`);
        }} />
      )}
    </Container>
  );
};

export default ProcurementPage;
