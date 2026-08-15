/**
 * Roles a visitor can pick when signing up.
 *
 * Mirrors the seeded `roles` table in auth-service
 * (V1__initial_schema.sql) minus the administrative roles, which are only ever
 * granted from the admin console — never self-selected at registration.
 */
export interface SignupRole {
  value: string;
  label: string;
  group: string;
}

export const SIGNUP_ROLES: SignupRole[] = [
  { value: 'CUSTOMER', label: 'Customer', group: 'General' },

  { value: 'CIVIL_ENGINEER', label: 'Civil Engineer', group: 'Professionals' },
  { value: 'STRUCTURAL_ENGINEER', label: 'Structural Engineer', group: 'Professionals' },
  { value: 'SITE_ENGINEER', label: 'Site Engineer', group: 'Professionals' },
  { value: 'ARCHITECT', label: 'Architect', group: 'Professionals' },
  { value: 'INTERIOR_DESIGNER', label: 'Interior Designer', group: 'Professionals' },
  { value: 'EXTERIOR_DESIGNER', label: 'Exterior Designer', group: 'Professionals' },
  { value: 'SURVEYOR', label: 'Surveyor', group: 'Professionals' },

  { value: 'LABOUR_CONTRACTOR', label: 'Labour Contractor', group: 'Trades' },
  { value: 'WORKER', label: 'Worker', group: 'Trades' },
  { value: 'LABOUR', label: 'Labour', group: 'Trades' },
  { value: 'PAINTER', label: 'Painter', group: 'Trades' },
  { value: 'PLUMBER', label: 'Plumber', group: 'Trades' },
  { value: 'ELECTRICIAN', label: 'Electrician', group: 'Trades' },
  { value: 'WELDER', label: 'Welder', group: 'Trades' },
  { value: 'CARPENTER', label: 'Carpenter', group: 'Trades' },
  { value: 'FABRICATOR', label: 'Fabricator', group: 'Trades' },

  { value: 'MATERIAL_SUPPLIER', label: 'Material Supplier', group: 'Suppliers' },
  { value: 'EQUIPMENT_RENTAL', label: 'Equipment Rental Provider', group: 'Suppliers' },
];

export const SIGNUP_ROLE_GROUPS = ['General', 'Professionals', 'Trades', 'Suppliers'];
