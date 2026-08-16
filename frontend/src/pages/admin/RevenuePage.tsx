import React, { useState, useEffect } from 'react';
import {
  Box, Card, CardContent, Typography, Grid, Chip, Avatar, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, Button, LinearProgress, Skeleton,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import {
  AccountBalanceWallet, TrendingUp, Receipt, Download, Payment, MoneyOff,
} from '@mui/icons-material';
import { revenueApi, RevenueData, RevenueSummary, MonthlyRevenue, BreakdownItem, Transaction } from '../../services/adminApi';
import { SortableTableCell, useTableSort } from '../../components/admin/SortableTable';

const formatCurrency = (amount: number) => {
  if (amount >= 10000000) return `₹${(amount / 10000000).toFixed(2)}Cr`;
  if (amount >= 100000) return `₹${(amount / 100000).toFixed(1)}L`;
  if (amount >= 1000) return `₹${(amount / 1000).toFixed(1)}K`;
  return `₹${amount.toLocaleString()}`;
};

const RevenuePage: React.FC = () => {
  // The brand colour is read from the theme, not written in: these cards used the shipped violet
  // literally, so re-theming the platform left them unchanged.
  const theme = useTheme();
  const [loading, setLoading] = useState(true);
  const [data, setData] = useState<RevenueData | null>(null);

  useEffect(() => {
    const fetchRevenue = async () => {
      try {
        setLoading(true);
        const response = await revenueApi.getRevenueData();
        setData(response.data.data);
      } catch (err) {
        console.error('Failed to load revenue data:', err);
      } finally {
        setLoading(false);
      }
    };
    fetchRevenue();
  }, []);

  const summary: RevenueSummary | null = data?.summary || null;
  const monthlyRevenue: MonthlyRevenue[] = data?.monthlyRevenue || [];
  const breakdown: BreakdownItem[] = data?.breakdown?.items || [];
  const transactions: Transaction[] = data?.recentTransactions || [];

  const { sorted, sort, onSort } = useTableSort(transactions, {
    transactionId: (t) => t.transactionId,
    bookingCode: (t) => t.bookingCode,
    customerName: (t) => t.customerName,
    amount: (t) => t.amount ?? 0,
    type: (t) => t.type,
    status: (t) => t.status,
    date: (t) => (t.date ? new Date(t.date) : null),
  }, { key: 'date', direction: 'desc' });

  const maxMonthlyRevenue = Math.max(...monthlyRevenue.map(m => m.revenue), 1);

  const revenueStats = summary ? [
    { label: 'Total Revenue (MTD)', value: formatCurrency(summary.totalRevenueMtd), change: summary.revenueChange, icon: <AccountBalanceWallet />, color: '#10b981' },
    { label: 'Platform Fees', value: formatCurrency(summary.platformFees), change: summary.platformFeePercentage, icon: <TrendingUp />, color: theme.palette.primary.main },
    { label: 'Pending Payouts', value: formatCurrency(summary.pendingPayouts), change: `${summary.pendingPayoutWorkers} workers`, icon: <Payment />, color: '#f59e0b' },
    { label: 'Refunds (MTD)', value: formatCurrency(summary.refundsMtd), change: summary.refundChange, icon: <MoneyOff />, color: '#ef4444' },
  ] : [];

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 800, mb: 0.5 }}>Revenue & Reports</Typography>
          <Typography variant="body2" sx={{ color: '#64748b' }}>Financial overview and revenue analytics</Typography>
        </Box>
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button startIcon={<Download />} variant="outlined" sx={{ borderRadius: 2, borderColor: '#e2e8f0', color: '#475569' }}>Export CSV</Button>
          <Button startIcon={<Download />} variant="contained" sx={{ borderRadius: 2 }}>Export Report</Button>
        </Box>
      </Box>

      <Grid container spacing={3} sx={{ mb: 4 }}>
        {loading ? (
          Array.from({ length: 4 }).map((_, idx) => (
            <Grid item xs={12} sm={6} md={3} key={idx}>
              <Card sx={{ borderRadius: 3 }}><CardContent sx={{ p: 3 }}><Skeleton height={80} /></CardContent></Card>
            </Grid>
          ))
        ) : (
          revenueStats.map((stat, idx) => (
            <Grid item xs={12} sm={6} md={3} key={idx}>
              <Card sx={{ borderRadius: 3 }}>
                <CardContent sx={{ p: 3 }}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 2 }}>
                    <Avatar sx={{ bgcolor: `${stat.color}15`, color: stat.color, width: 44, height: 44 }}>{stat.icon}</Avatar>
                  </Box>
                  <Typography variant="h5" sx={{ fontWeight: 800, mb: 0.5 }}>{stat.value}</Typography>
                  <Typography variant="body2" sx={{ color: '#64748b' }}>{stat.label}</Typography>
                  <Chip label={stat.change} size="small"
                    sx={{ mt: 1, bgcolor: '#ecfdf5', color: stat.change.startsWith('-') ? '#ef4444' : '#10b981', fontWeight: 600 }} />
                </CardContent>
              </Card>
            </Grid>
          ))
        )}
      </Grid>

      <Grid container spacing={3}>
        <Grid item xs={12} md={8}>
          <Card sx={{ borderRadius: 3 }}>
            <Box sx={{ p: 3, borderBottom: '1px solid #e2e8f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Typography variant="h6" sx={{ fontWeight: 700 }}>Monthly Revenue</Typography>
              <Box sx={{ display: 'flex', gap: 2 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                  <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: 'primary.main' }} />
                  <Typography variant="caption" sx={{ color: '#64748b' }}>Revenue</Typography>
                </Box>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                  <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: '#10b981' }} />
                  <Typography variant="caption" sx={{ color: '#64748b' }}>Payouts</Typography>
                </Box>
              </Box>
            </Box>
            <CardContent sx={{ p: 3 }}>
              <Box sx={{ display: 'flex', alignItems: 'flex-end', gap: 1, height: 220 }}>
                {loading ? (
                  Array.from({ length: 12 }).map((_, idx) => (
                    <Box key={idx} sx={{ flex: 1 }}><Skeleton height={80 + Math.random() * 100} /></Box>
                  ))
                ) : (
                  monthlyRevenue.map((month) => (
                    <Box key={month.month} sx={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', height: '100%', justifyContent: 'flex-end' }}>
                      <Box sx={{ width: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 0.3 }}>
                        <Box sx={{ width: '100%', maxWidth: 24, height: `${Math.max((month.payouts / maxMonthlyRevenue) * 100, 3)}%`, bgcolor: '#10b981', borderRadius: '2px 2px 0 0', opacity: 0.7 }} />
                        <Box sx={{ width: '100%', maxWidth: 24, height: `${Math.max((month.revenue / maxMonthlyRevenue) * 100, 5)}%`, bgcolor: 'primary.main', borderRadius: '2px 2px 0 0' }} />
                      </Box>
                      <Typography variant="caption" sx={{ color: '#94a3b8', mt: 0.5, fontSize: '0.6rem' }}>{month.month}</Typography>
                    </Box>
                  ))
                )}
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={4}>
          <Card sx={{ borderRadius: 3, height: '100%' }}>
            <Box sx={{ p: 3, borderBottom: '1px solid #e2e8f0' }}><Typography variant="h6" sx={{ fontWeight: 700 }}>Revenue Breakdown</Typography></Box>
            <CardContent sx={{ p: 3 }}>
              {loading ? (
                Array.from({ length: 4 }).map((_, idx) => (
                  <Box key={idx} sx={{ mb: 3 }}><Skeleton height={40} /><Skeleton height={8} sx={{ mt: 1 }} /></Box>
                ))
              ) : (
                breakdown.map((item, idx) => (
                  <Box key={idx} sx={{ mb: idx < breakdown.length - 1 ? 3 : 0 }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                      <Typography variant="body2" sx={{ fontWeight: 500 }}>{item.label}</Typography>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>{formatCurrency(item.value)}</Typography>
                    </Box>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <LinearProgress variant="determinate" value={item.percentage}
                        sx={{ flex: 1, height: 8, borderRadius: 4, bgcolor: '#e2e8f0',
                          '& .MuiLinearProgress-bar': { borderRadius: 4, bgcolor: item.color } }} />
                      <Typography variant="caption" sx={{ fontWeight: 600, color: '#64748b' }}>{item.percentage}%</Typography>
                    </Box>
                  </Box>
                ))
              )}
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12}>
          <Card sx={{ borderRadius: 3 }}>
            <Box sx={{ p: 3, borderBottom: '1px solid #e2e8f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Typography variant="h6" sx={{ fontWeight: 700 }}>Recent Transactions</Typography>
              <Button size="small" sx={{ color: 'primary.main' }}>View All</Button>
            </Box>
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow sx={{ bgcolor: 'action.hover' }}>
                    <SortableTableCell columnKey="transactionId" sort={sort} onSort={onSort}>Transaction ID</SortableTableCell>
                    <SortableTableCell columnKey="bookingCode" sort={sort} onSort={onSort}>Booking</SortableTableCell>
                    <SortableTableCell columnKey="customerName" sort={sort} onSort={onSort}>Customer</SortableTableCell>
                    <SortableTableCell columnKey="amount" sort={sort} onSort={onSort}>Amount</SortableTableCell>
                    <SortableTableCell columnKey="type" sort={sort} onSort={onSort}>Type</SortableTableCell>
                    <SortableTableCell columnKey="status" sort={sort} onSort={onSort}>Status</SortableTableCell>
                    <SortableTableCell columnKey="date" sort={sort} onSort={onSort}>Date</SortableTableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {loading ? (
                    Array.from({ length: 5 }).map((_, idx) => (
                      <TableRow key={idx}>
                        {Array.from({ length: 7 }).map((_, cidx) => (
                          <TableCell key={cidx}><Skeleton /></TableCell>
                        ))}
                      </TableRow>
                    ))
                  ) : transactions.length > 0 ? (
                    sorted.map((txn, idx) => (
                      <TableRow key={idx} hover>
                        <TableCell><Typography variant="body2" sx={{ fontFamily: 'monospace', fontWeight: 600 }}>{txn.transactionId}</Typography></TableCell>
                        <TableCell><Typography variant="body2" sx={{ fontFamily: 'monospace', color: 'primary.main' }}>{txn.bookingCode}</Typography></TableCell>
                        <TableCell><Typography variant="body2">{txn.customerName}</Typography></TableCell>
                        <TableCell><Typography variant="body2" sx={{ fontWeight: 600 }}>{formatCurrency(txn.amount)}</Typography></TableCell>
                        <TableCell>
                          <Chip label={txn.type} size="small"
                            sx={{ bgcolor: txn.type === 'Refund' ? '#fef2f2' : txn.type === 'Payout' ? '#fffbeb' : '#ecfdf5',
                              color: txn.type === 'Refund' ? '#ef4444' : txn.type === 'Payout' ? '#f59e0b' : '#10b981', fontWeight: 600 }} />
                        </TableCell>
                        <TableCell>
                          <Chip label={txn.status} size="small"
                            sx={{ bgcolor: txn.status === 'Completed' ? '#ecfdf5' : '#fffbeb',
                              color: txn.status === 'Completed' ? '#10b981' : '#f59e0b', fontWeight: 600 }} />
                        </TableCell>
                        <TableCell><Typography variant="body2" sx={{ color: '#64748b' }}>{txn.date}</Typography></TableCell>
                      </TableRow>
                    ))
                  ) : (
                    <TableRow>
                      <TableCell colSpan={7} align="center" sx={{ py: 4 }}>
                        <Typography variant="body2" sx={{ color: '#94a3b8' }}>No transactions found</Typography>
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
};

export default RevenuePage;
