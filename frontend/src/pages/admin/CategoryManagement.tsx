import React, { useState, useEffect } from 'react';
import {
  Box, Card, Typography, Button, Table, TableBody, TableCell, TableContainer,
  TableHead, TableRow, Chip, IconButton, Dialog, DialogTitle, DialogContent,
  DialogActions, TextField, Switch, FormControlLabel, Grid, Skeleton, Snackbar, Alert,
} from '@mui/material';
import {
  Add, Edit, Delete, DragIndicator, Home, Engineering, Architecture, Map,
  DesignServices, Construction, ElectricalServices, WaterDrop,
} from '@mui/icons-material';
import { categoryApi, AdminCategory, CreateCategoryRequest, UpdateCategoryRequest } from '../../services/adminApi';

const iconMap: Record<string, React.ReactNode> = {
  'Home': <Home />, 'Engineering': <Engineering />, 'Architecture': <Architecture />,
  'Map': <Map />, 'DesignServices': <DesignServices />, 'Construction': <Construction />,
  'ElectricalServices': <ElectricalServices />, 'WaterDrop': <WaterDrop />,
};

const CategoryManagement: React.FC = () => {
  const [categories, setCategories] = useState<AdminCategory[]>([]);
  const [loading, setLoading] = useState(true);
  const [openDialog, setOpenDialog] = useState(false);
  const [editingCategory, setEditingCategory] = useState<AdminCategory | null>(null);
  const [formData, setFormData] = useState<CreateCategoryRequest>({ name: '', slug: '', description: '', sortOrder: 0 });
  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: 'success' | 'error' }>({ open: false, message: '', severity: 'success' });

  useEffect(() => {
    fetchCategories();
  }, []);

  const fetchCategories = async () => {
    try {
      setLoading(true);
      const response = await categoryApi.getCategories();
      const data = response.data;
      if (Array.isArray(data.data)) {
        setCategories(data.data);
      } else {
        setCategories([]);
      }
    } catch (err) {
      console.error('Failed to fetch categories:', err);
      setSnackbar({ open: true, message: 'Failed to load categories', severity: 'error' });
    } finally {
      setLoading(false);
    }
  };

  const handleEdit = (category: AdminCategory) => {
    setEditingCategory(category);
    setFormData({
      name: category.name,
      slug: category.slug,
      description: category.description || '',
      sortOrder: category.sortOrder,
    });
    setOpenDialog(true);
  };

  const handleAdd = () => {
    setEditingCategory(null);
    setFormData({ name: '', slug: '', description: '', sortOrder: 0 });
    setOpenDialog(true);
  };

  const handleSave = async () => {
    try {
      if (editingCategory) {
        const updateData: UpdateCategoryRequest = {
          name: formData.name,
          slug: formData.slug,
          description: formData.description,
          sortOrder: formData.sortOrder,
        };
        await categoryApi.updateCategory(editingCategory.id, updateData);
        setSnackbar({ open: true, message: 'Category updated successfully', severity: 'success' });
      } else {
        await categoryApi.createCategory(formData);
        setSnackbar({ open: true, message: 'Category created successfully', severity: 'success' });
      }
      setOpenDialog(false);
      fetchCategories();
    } catch (err) {
      setSnackbar({ open: true, message: 'Failed to save category', severity: 'error' });
    }
  };

  const handleToggleStatus = async (category: AdminCategory) => {
    try {
      await categoryApi.toggleCategoryStatus(category.id);
      setSnackbar({ open: true, message: `Category ${category.active ? 'deactivated' : 'activated'}`, severity: 'success' });
      fetchCategories();
    } catch (err) {
      setSnackbar({ open: true, message: 'Failed to toggle category status', severity: 'error' });
    }
  };

  const handleDelete = async (category: AdminCategory) => {
    try {
      await categoryApi.deleteCategory(category.id);
      setSnackbar({ open: true, message: 'Category deleted successfully', severity: 'success' });
      fetchCategories();
    } catch (err) {
      setSnackbar({ open: true, message: 'Failed to delete category', severity: 'error' });
    }
  };

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 800, mb: 0.5 }}>Service Categories</Typography>
          <Typography variant="body2" sx={{ color: '#64748b' }}>Manage your service categories and subcategories</Typography>
        </Box>
        <Button variant="contained" startIcon={<Add />} onClick={handleAdd} sx={{ borderRadius: 2 }}>Add Category</Button>
      </Box>

      <Card sx={{ borderRadius: 3, overflow: 'hidden' }}>
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow sx={{ bgcolor: '#f8fafc' }}>
                <TableCell sx={{ fontWeight: 700, width: 40 }}></TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Category</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Slug</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Sort Order</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Services</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Status</TableCell>
                <TableCell sx={{ fontWeight: 700 }} align="right">Actions</TableCell>
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
              ) : categories.length > 0 ? (
                categories.map((category) => (
                  <TableRow key={category.id} hover>
                    <TableCell><DragIndicator sx={{ color: '#94a3b8', cursor: 'grab', fontSize: 20 }} /></TableCell>
                    <TableCell>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                        <Box sx={{ color: '#667eea', display: 'flex' }}>
                          {iconMap[category.icon || ''] || <Engineering />}
                        </Box>
                        <Box>
                          <Typography variant="body2" sx={{ fontWeight: 600 }}>{category.name}</Typography>
                          {category.parentName && (
                            <Typography variant="caption" sx={{ color: '#94a3b8' }}>Parent: {category.parentName}</Typography>
                          )}
                        </Box>
                      </Box>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontFamily: 'monospace', color: '#64748b' }}>{category.slug}</Typography>
                    </TableCell>
                    <TableCell>
                      <Chip label={category.sortOrder} size="small" variant="outlined" sx={{ fontWeight: 600 }} />
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>{category.servicesCount}</Typography>
                    </TableCell>
                    <TableCell>
                      <Switch checked={category.active} size="small" color="primary" onChange={() => handleToggleStatus(category)} />
                    </TableCell>
                    <TableCell align="right">
                      <IconButton size="small" onClick={() => handleEdit(category)} sx={{ mr: 1 }}>
                        <Edit fontSize="small" />
                      </IconButton>
                      <IconButton size="small" color="error" onClick={() => handleDelete(category)}>
                        <Delete fontSize="small" />
                      </IconButton>
                    </TableCell>
                  </TableRow>
                ))
              ) : (
                <TableRow>
                  <TableCell colSpan={7} align="center" sx={{ py: 4 }}>
                    <Typography variant="body2" sx={{ color: '#94a3b8' }}>No categories found</Typography>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>

      <Dialog open={openDialog} onClose={() => setOpenDialog(false)} maxWidth="sm" fullWidth PaperProps={{ sx: { borderRadius: 3 } }}>
        <DialogTitle sx={{ fontWeight: 700 }}>{editingCategory ? 'Edit Category' : 'Add Category'}</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 1 }}>
            <Grid item xs={6}>
              <TextField fullWidth label="Category Name" value={formData.name}
                onChange={(e) => { setFormData({ ...formData, name: e.target.value });
                  if (!editingCategory) setFormData(f => ({ ...f, slug: e.target.value.toLowerCase().replace(/\s+/g, '-') })); }}
                size="small" />
            </Grid>
            <Grid item xs={6}>
              <TextField fullWidth label="Slug" value={formData.slug}
                onChange={(e) => setFormData({ ...formData, slug: e.target.value })}
                size="small" placeholder="e.g., house-planning" />
            </Grid>
            <Grid item xs={12}>
              <TextField fullWidth label="Description" multiline rows={3} value={formData.description || ''}
                onChange={(e) => setFormData({ ...formData, description: e.target.value })} size="small" />
            </Grid>
            <Grid item xs={6}>
              <TextField fullWidth label="Sort Order" type="number" value={formData.sortOrder || 0}
                onChange={(e) => setFormData({ ...formData, sortOrder: parseInt(e.target.value) || 0 })} size="small" />
            </Grid>
            <Grid item xs={6}>
              <FormControlLabel control={<Switch defaultChecked />} label="Active" sx={{ mt: 1 }} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions sx={{ p: 3 }}>
          <Button onClick={() => setOpenDialog(false)} sx={{ color: '#64748b' }}>Cancel</Button>
          <Button variant="contained" onClick={handleSave}>{editingCategory ? 'Update' : 'Create'}</Button>
        </DialogActions>
      </Dialog>

      <Snackbar open={snackbar.open} autoHideDuration={4000} onClose={() => setSnackbar({ ...snackbar, open: false })}>
        <Alert severity={snackbar.severity} sx={{ borderRadius: 2 }}>{snackbar.message}</Alert>
      </Snackbar>
    </Box>
  );
};

export default CategoryManagement;
