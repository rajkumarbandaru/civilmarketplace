import { ContentItem, ContentSection } from '../services/siteContentApi';

/**
 * The copy the site ships with.
 *
 * This is what renders when the content service cannot be reached — a landing page that is blank
 * because one request timed out is worse than one showing copy an admin has since edited. It is a
 * mirror of the seed rows in `V10__site_content.sql`; when a default changes, change both.
 *
 * Ids are negative so nothing can confuse a fallback row with a real one.
 */

let nextId = -1;

const item = (fields: Partial<ContentItem>): ContentItem => ({
  id: nextId--,
  title: null,
  subtitle: null,
  body: null,
  icon: null,
  imageUrl: null,
  linkUrl: null,
  badge: null,
  sortOrder: 0,
  enabled: true,
  ...fields,
});

const section = (fields: Partial<ContentSection> & { sectionKey: string; pageKey: string }): ContentSection => ({
  id: nextId--,
  title: null,
  subtitle: null,
  body: null,
  imageUrl: null,
  linkLabel: null,
  linkUrl: null,
  columnIndex: 0,
  sortOrder: 0,
  enabled: true,
  systemOwned: true,
  items: [],
  ...fields,
});

/** Footer link groups, kept as data so the fallback and the seed stay easy to compare. */
const footerGroup = (
  sectionKey: string,
  title: string,
  columnIndex: number,
  sortOrder: number,
  links: [string, string | null][]
): ContentSection =>
  section({
    pageKey: 'FOOTER',
    sectionKey,
    title,
    columnIndex,
    sortOrder,
    items: links.map(([label, url], idx) =>
      item({ title: label, linkUrl: url, sortOrder: (idx + 1) * 10 })
    ),
  });

export const FALLBACK_CONTENT: ContentSection[] = [
  section({
    pageKey: 'HOME',
    sectionKey: 'home.hero',
    sortOrder: 10,
    // The **wrapped** word renders in the accent colour.
    title: 'Book Civil Engineering **Professionals** Instantly',
    subtitle:
      'From architects and structural engineers to surveyors and contractors — find and book trusted civil engineering experts near you, on demand.',
    body: "India's #1 Civil Engineering Platform",
    linkLabel: 'Search',
    linkUrl: '/services',
    items: [
      item({ title: 'Verified Professionals', icon: 'Security', sortOrder: 10 }),
      item({ title: 'Secure Payments', icon: 'Security', sortOrder: 20 }),
      item({ title: '24/7 Support', icon: 'Security', sortOrder: 30 }),
    ],
  }),
  section({
    pageKey: 'HOME',
    sectionKey: 'home.stats',
    sortOrder: 20,
    items: [
      item({ title: '10,000+', subtitle: 'Professionals', icon: 'People', sortOrder: 10 }),
      item({ title: '50,000+', subtitle: 'Projects Completed', icon: 'Verified', sortOrder: 20 }),
      item({ title: '4.8/5', subtitle: 'Average Rating', icon: 'Star', sortOrder: 30 }),
      item({ title: '100+', subtitle: 'Cities Covered', icon: 'Speed', sortOrder: 40 }),
    ],
  }),
  section({
    pageKey: 'HOME',
    sectionKey: 'home.how_it_works',
    sortOrder: 30,
    title: 'How It Works',
    subtitle: 'Get your civil engineering work done in three simple steps',
    items: [
      item({
        badge: '01',
        title: 'Describe Your Project',
        body: 'Tell us what you need — from house plans to structural analysis',
        sortOrder: 10,
      }),
      item({
        badge: '02',
        title: 'Get Matched with Experts',
        body: 'We connect you with verified professionals in your area',
        sortOrder: 20,
      }),
      item({
        badge: '03',
        title: 'Book & Track',
        body: 'Book instantly and track progress in real-time',
        sortOrder: 30,
      }),
    ],
  }),
  section({
    pageKey: 'HOME',
    sectionKey: 'home.services',
    sortOrder: 40,
    title: 'Our Services',
    subtitle: 'Comprehensive civil engineering services for all your construction needs',
  }),
  section({
    pageKey: 'HOME',
    sectionKey: 'home.cta',
    sortOrder: 50,
    title: 'Ready to Start Your Project?',
    subtitle: 'Join thousands of satisfied customers who found the perfect civil engineering professional',
    items: [
      item({ title: 'Get Started Free', linkUrl: '/register', sortOrder: 10 }),
      item({ title: 'Browse Services', linkUrl: '/services', sortOrder: 20 }),
    ],
  }),
  section({ pageKey: 'GLOBAL', sectionKey: 'global.brand', title: 'CivEngMarket', linkUrl: '/', sortOrder: 10 }),
  section({
    pageKey: 'FOOTER',
    sectionKey: 'footer.brand',
    sortOrder: 10,
    title: 'CivEngMarket',
    body: "India's #1 platform for booking civil engineering professionals. Connecting customers with trusted architects, engineers, surveyors, and construction experts.",
    items: [
      item({ title: 'Facebook', icon: 'Facebook', linkUrl: 'https://facebook.com', sortOrder: 10 }),
      item({ title: 'Twitter', icon: 'Twitter', linkUrl: 'https://twitter.com', sortOrder: 20 }),
      item({ title: 'Instagram', icon: 'Instagram', linkUrl: 'https://instagram.com', sortOrder: 30 }),
      item({ title: 'LinkedIn', icon: 'LinkedIn', linkUrl: 'https://linkedin.com', sortOrder: 40 }),
      item({ title: 'YouTube', icon: 'YouTube', linkUrl: 'https://youtube.com', sortOrder: 50 }),
    ],
  }),
  footerGroup('footer.design', 'Design & Planning', 1, 10, [
    ['House Planning', '/services/architecture?q=House%20Planning'],
    ['Villa Planning', '/services/architecture?q=Villa%20Planning'],
    ['Architecture Design', '/services/architecture?q=Architecture%20Design'],
    ['Elevation Design', '/services/architecture?q=Elevation%20Design'],
    ['Interior Design', '/services/design?q=Interior%20Design'],
    ['3D Modeling', '/services/design?q=3D%20Modeling'],
  ]),
  footerGroup('footer.survey', 'Survey & Engineering', 1, 20, [
    ['Structural Engineering', '/services/engineering?q=Structural%20Engineering'],
    ['Earthquake Design', '/services/engineering?q=Earthquake%20Design'],
    ['BIM Modeling', '/services/engineering?q=BIM%20Modeling'],
    ['Land Survey', '/services/survey?q=Land%20Survey'],
    ['Drone Survey', '/services/survey?q=Drone%20Survey'],
    ['GIS Mapping', '/services/survey?q=GIS%20Mapping'],
  ]),
  footerGroup('footer.construction', 'Construction', 2, 10, [
    ['Building Construction', '/services/construction?q=Building%20Construction'],
    ['Renovation', '/services/construction?q=Renovation'],
    ['Electrical Work', '/services/construction?q=Electrical%20Work'],
    ['Plumbing Services', '/services/construction?q=Plumbing%20Services'],
    ['Contractor Services', '/services/construction?q=Contractor%20Services'],
    ['Site Supervision', '/services/construction?q=Site%20Supervision'],
    ['Project Management', '/services/construction?q=Project%20Management'],
  ]),
  footerGroup('footer.marketplace', 'Marketplace', 2, 20, [
    ['Material Supply', '/services/materials'],
    ['Equipment Rental', '/services/equipment'],
    ['Transport & Logistics', '/services/transport'],
    ['Skilled Labour', '/services/labour'],
    ['Daily Wage Labour', '/services/labour'],
    ['Skill & Safety Training', '/services/training'],
    ['Request a Quote (RFQ)', '/services'],
  ]),
  footerGroup('footer.materials', 'Materials', 3, 10, [
    ['Cement', '/services/materials?q=Cement'],
    ['Iron & TMT Steel Bars', '/services/materials?q=Iron%20%26%20TMT%20Steel%20Bars'],
    ['Bricks & Blocks', '/services/materials?q=Bricks%20%26%20Blocks'],
    ['Sand & Filling Material', '/services/materials?q=Sand%20%26%20Filling%20Material'],
    ['Aggregates & Crushed Stone', '/services/materials?q=Aggregates%20%26%20Crushed%20Stone'],
    ['Concrete (Ready Mix)', '/services/materials?q=Concrete'],
    ['Ceramic & Vitrified Tiles', '/services/materials?q=Ceramic%20%26%20Vitrified%20Tiles'],
    ['Paints & Coatings', '/services/materials?q=Paints%20%26%20Coatings'],
    ['Pipes & Fittings', '/services/materials?q=Pipes%20%26%20Fittings'],
    ['Sanitaryware & Bath Fittings', '/services/materials?q=Sanitaryware'],
    ['All Materials (A–Z)', '/services/materials'],
  ]),
  footerGroup('footer.professionals', 'For Professionals', 4, 10, [
    ['Register as Worker', '/register?role=WORKER'],
    ['Register as Engineer', '/register?role=ENGINEER'],
    ['Register as Architect', '/register?role=ARCHITECT'],
    ['Register as Surveyor', '/register?role=SURVEYOR'],
    ['Register as Contractor', '/register?role=CONTRACTOR'],
    ['Register as Material Supplier', '/register?role=MATERIAL_SUPPLIER'],
    ['Register as Equipment Supplier', '/register?role=EQUIPMENT_SUPPLIER'],
    ['Register as Transport Provider', '/register?role=TRANSPORT_PROVIDER'],
    ['Partner Program', '/register'],
    ['Earnings', '/register'],
  ]),
  footerGroup('footer.company', 'Company', 5, 10, [
    ['About Us', null],
    ['Careers', null],
    ['Blog', null],
    ['Press', null],
    ['Contact Us', null],
  ]),
  footerGroup('footer.support', 'Support', 5, 20, [
    ['Help Center', '/support'],
    ['Raise a Ticket', '/support'],
    ['Safety Guidelines', null],
    ['Dispute Resolution', null],
    ['Terms of Service', null],
    ['Privacy Policy', null],
    ['Refund Policy', null],
  ]),
  section({
    pageKey: 'FOOTER',
    sectionKey: 'footer.legal',
    sortOrder: 90,
    body: '© {year} Civil Engineering Marketplace. All rights reserved.',
    subtitle: 'Made with ❤️ for civil engineering professionals',
  }),
];
