import React from 'react';
import { useNavigate } from 'react-router-dom';
import { Box, Container, Typography, Link, IconButton, Divider } from '@mui/material';
import DynamicIcon from './DynamicIcon';
import { usePageSections, useSection } from '../hooks/useSiteContent';
import { ContentSection, resolveMediaUrl } from '../services/siteContentApi';

/**
 * The footer, rendered from the content service rather than from literals.
 *
 * Every heading, link, link target, social icon and the brand blurb are rows a Super Admin edits
 * in the console — the footer used to be a hardcoded list, which is why fixing a typo or pointing
 * a link somewhere else meant a redeploy.
 *
 * The two structural rows are addressed by key: `footer.brand` is the wordmark block on the left,
 * `footer.legal` is the copyright line. Everything else is a link group, packed into columns by
 * its `columnIndex` so no column runs twice as long as its neighbours.
 */

const BRAND_KEY = 'footer.brand';
const LEGAL_KEY = 'footer.legal';

const Footer: React.FC = () => {
  const navigate = useNavigate();
  const footerSections = usePageSections('FOOTER');
  const brand = useSection(BRAND_KEY);
  const legal = useSection(LEGAL_KEY);

  /**
   * In-app paths go through the router — a plain `<a href>` would reload the whole SPA just to
   * move between two of its own pages. Anything absolute is an external site and opens in a new
   * tab; an item with no target is not a link at all.
   */
  const follow = (url: string | null) => {
    if (!url) return;
    if (/^(https?:)?\/\//i.test(url) || url.startsWith('mailto:') || url.startsWith('tel:')) {
      window.open(url, '_blank', 'noopener,noreferrer');
      return;
    }
    navigate(url);
  };

  // Link groups only: the brand block and the legal line render in their own places below.
  const groups = footerSections.filter(
    (section) => section.sectionKey !== BRAND_KEY && section.sectionKey !== LEGAL_KEY
  );

  // Grouped by the column an admin assigned. Sparse or out-of-order indices are fine — the keys
  // are sorted and rendered in sequence, so deleting a whole column closes the gap rather than
  // leaving a hole.
  const columns: ContentSection[][] = Object.values(
    groups.reduce<Record<number, ContentSection[]>>((acc, section) => {
      (acc[section.columnIndex] ??= []).push(section);
      return acc;
    }, {})
  );

  const logo = resolveMediaUrl(brand?.imageUrl);
  const legalLine = (legal?.body ?? '').replace('{year}', String(new Date().getFullYear()));

  return (
    <Box
      component="footer"
      sx={{
        background: '#1e293b',
        color: '#cbd5e1',
        pt: 8,
        pb: 4,
        mt: 'auto',
      }}
    >
      <Container maxWidth="xl">
        {/* A grid rather than a 12-column Grid: the link columns each need room for their longest
            label ("Register as Material Supplier") on one or two lines, which 1.8/12 never gave
            them. `minmax(0, 1fr)` keeps every column the same width and lets them wrap onto a
            second row on narrow viewports instead of squeezing. */}
        <Box
          sx={{
            display: 'grid',
            gap: { xs: 4, md: 5 },
            alignItems: 'start',
            gridTemplateColumns: {
              xs: 'repeat(2, minmax(0, 1fr))',
              sm: 'repeat(3, minmax(0, 1fr))',
              md: 'repeat(4, minmax(0, 1fr))',
              lg: 'minmax(0, 1.4fr) repeat(5, minmax(0, 1fr))',
            },
          }}
        >
          {/* Brand */}
          {brand && (
            <Box sx={{ gridColumn: { xs: '1 / -1', lg: 'auto' }, maxWidth: 340 }}>
              {logo ? (
                <Box
                  component="img"
                  src={logo}
                  alt={brand.title ?? 'Logo'}
                  sx={{ height: 40, maxWidth: '100%', objectFit: 'contain', mb: 2, display: 'block' }}
                />
              ) : (
                <Typography
                  variant="h5"
                  sx={{
                    fontWeight: 800,
                    background: (t) =>
                      `linear-gradient(135deg, ${t.palette.primary.main} 0%, ${t.palette.secondary.main} 100%)`,
                    WebkitBackgroundClip: 'text',
                    WebkitTextFillColor: 'transparent',
                    mb: 2,
                    fontFamily: "'Poppins', sans-serif",
                  }}
                >
                  {brand.title}
                </Typography>
              )}
              {brand.body && (
                <Typography variant="body2" sx={{ mb: 3, lineHeight: 1.7 }}>
                  {brand.body}
                </Typography>
              )}
              <Box sx={{ display: 'flex', gap: 1 }}>
                {brand.items.map((social) => (
                  <IconButton
                    key={social.id}
                    size="small"
                    aria-label={social.title ?? social.icon ?? 'social'}
                    onClick={() => follow(social.linkUrl)}
                    sx={{
                      color: '#94a3b8',
                      '&:hover': { color: 'primary.main', background: (t) => t.palette.primary.main + '1a' },
                    }}
                  >
                    <DynamicIcon name={social.icon ?? 'Link'} fontSize="small" />
                  </IconButton>
                ))}
              </Box>
            </Box>
          )}

          {/* Link columns: each is its own stack, so a column holding two groups keeps a uniform
              gap between them regardless of how long its neighbours are. */}
          {columns.map((column) => (
            <Box
              key={column[0].sectionKey}
              sx={{ display: 'flex', flexDirection: 'column', gap: 4, minWidth: 0 }}
            >
              {column.map((section) => (
                <Box key={section.sectionKey}>
                  <Typography
                    variant="subtitle2"
                    sx={{
                      color: '#fff',
                      fontWeight: 600,
                      mb: 2,
                      textTransform: 'uppercase',
                      letterSpacing: 0.6,
                      fontSize: '0.8125rem',
                      lineHeight: 1.4,
                    }}
                  >
                    {section.title}
                  </Typography>
                  {section.items.map((link) => (
                    <Link
                      key={link.id}
                      component="button"
                      type="button"
                      onClick={() => follow(link.linkUrl)}
                      underline="none"
                      sx={{
                        display: 'block',
                        textAlign: 'left',
                        background: 'none',
                        border: 'none',
                        p: 0,
                        color: '#94a3b8',
                        mb: 1.25,
                        fontSize: '0.875rem',
                        lineHeight: 1.5,
                        fontFamily: 'inherit',
                        cursor: link.linkUrl ? 'pointer' : 'default',
                        transition: 'color 0.2s',
                        '&:hover': { color: link.linkUrl ? 'primary.main' : '#94a3b8' },
                      }}
                    >
                      {link.title}
                    </Link>
                  ))}
                </Box>
              ))}
            </Box>
          ))}
        </Box>

        <Divider sx={{ mt: 6, mb: 3, borderColor: 'rgba(255,255,255,0.1)' }} />

        <Box
          sx={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: 2,
            textAlign: { xs: 'center', sm: 'left' },
          }}
        >
          <Typography variant="body2" sx={{ color: '#64748b' }}>
            {legalLine}
          </Typography>
          {legal?.subtitle && (
            <Typography variant="body2" sx={{ color: '#64748b' }}>
              {legal.subtitle}
            </Typography>
          )}
        </Box>
      </Container>
    </Box>
  );
};

export default Footer;
