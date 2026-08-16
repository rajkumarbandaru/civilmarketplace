/**
 * The catalogue's *fallback* copy, plus the pure helpers that work over any catalogue.
 *
 * The live catalogue now comes from the API (`/api/v1/catalogue`, backed by booking-service and
 * editable by admins under Admin → Services). What stays here is the list the site shipped with,
 * used only when that call fails — a services page that renders nothing because one request timed
 * out is worse than one showing a slightly stale list — and the search/filter helpers, which are
 * plain functions over whichever list they are handed.
 *
 * Icons are Material-UI *names*, not elements, so this module stays plain data and can be imported
 * anywhere without pulling JSX along. `DynamicIcon` resolves them.
 */

export interface ServiceEntry {
  /** URL-safe id. The booking route and the deep links are built from this, so it must be stable. */
  slug: string;
  title: string;
  category: ServiceCategory;
  icon: string;
  price: string;
  rating: number;
  reviews: number;
  /** Trade names people actually type ("rebar", "jcb"), fed into search. */
  aliases?: string[];
  /** Optional artwork shown on the card in place of the icon. */
  mediaUrl?: string | null;
  mediaType?: 'IMAGE' | 'VIDEO' | 'ANIMATION' | null;
}

/**
 * Just a name. It was a closed union of the thirteen categories the site shipped with, which meant
 * a category an admin adds could not be typed at all — the whole point of making the catalogue
 * editable is that this list is not knowable at compile time.
 */
export type ServiceCategory = string;

export const FALLBACK_CATEGORIES: ServiceCategory[] = [
  'Architecture',
  'Engineering',
  'Survey',
  'Design',
  'Construction',
  'Services',
  'Materials',
  'Equipment',
  'Vehicles',
  'Logistics',
  'Labour',
  'Management',
  'Training',
];

/** `Materials` → `materials`. Used for both category and service deep links. */
export const slugify = (value: string): string =>
  value
    .toLowerCase()
    .replace(/&/g, 'and')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '');

const PROFESSIONAL_SERVICES: Omit<ServiceEntry, 'slug'>[] = [
  { title: '3D Modeling', category: 'Design', icon: 'DesignServices', price: '₹900/hr', rating: 4.6, reviews: 178 },
  { title: 'Architecture Design', category: 'Architecture', icon: 'Architecture', price: '₹800/hr', rating: 4.9, reviews: 189 },
  { title: 'BIM Modeling', category: 'Engineering', icon: 'Engineering', price: '₹1500/hr', rating: 4.9, reviews: 112 },
  { title: 'Building Construction', category: 'Construction', icon: 'Construction', price: 'Quote', rating: 4.5, reviews: 67 },
  { title: 'Contractor Services', category: 'Construction', icon: 'AssignmentTurnedIn', price: 'Quote', rating: 4.5, reviews: 154 },
  { title: 'Daily Wage Labour', category: 'Labour', icon: 'Groups', price: '₹650/day', rating: 4.2, reviews: 738 },
  { title: 'Drone Survey', category: 'Survey', icon: 'Map', price: '₹5000/visit', rating: 4.7, reviews: 234 },
  { title: 'Earthquake Design', category: 'Engineering', icon: 'Engineering', price: '₹1200/hr', rating: 4.8, reviews: 89 },
  { title: 'Electrical Work', category: 'Services', icon: 'ElectricalServices', price: '₹400/hr', rating: 4.4, reviews: 423 },
  { title: 'Elevation Design', category: 'Architecture', icon: 'Architecture', price: '₹600/hr', rating: 4.5, reviews: 256 },
  { title: 'GIS Mapping', category: 'Survey', icon: 'Map', price: '₹4000/visit', rating: 4.6, reviews: 76 },
  { title: 'House Planning', category: 'Architecture', icon: 'Home', price: '₹500/hr', rating: 4.8, reviews: 234 },
  { title: 'Interior Design', category: 'Design', icon: 'DesignServices', price: '₹600/hr', rating: 4.8, reviews: 312 },
  { title: 'Land Survey', category: 'Survey', icon: 'Map', price: '₹3000/visit', rating: 4.6, reviews: 98 },
  { title: 'Plumbing Services', category: 'Services', icon: 'WaterDrop', price: '₹350/hr', rating: 4.3, reviews: 567 },
  { title: 'Project Management', category: 'Management', icon: 'Work', price: 'Quote', rating: 4.8, reviews: 94 },
  { title: 'Renovation', category: 'Construction', icon: 'Construction', price: '₹500/hr', rating: 4.4, reviews: 345 },
  { title: 'Site Supervision', category: 'Engineering', icon: 'SupervisorAccount', price: '₹1800/day', rating: 4.7, reviews: 121 },
  { title: 'Skill & Safety Training', category: 'Training', icon: 'School', price: '₹1200/course', rating: 4.7, reviews: 208 },
  { title: 'Skilled Labour', category: 'Labour', icon: 'HandymanOutlined', price: '₹900/day', rating: 4.6, reviews: 612 },
  { title: 'Structural Engineering', category: 'Engineering', icon: 'Engineering', price: '₹1000/hr', rating: 4.7, reviews: 156 },
  { title: 'Transport & Logistics', category: 'Logistics', icon: 'LocalShipping', price: '₹2500/trip', rating: 4.3, reviews: 201 },
  { title: 'Villa Planning', category: 'Architecture', icon: 'Home', price: '₹700/hr', rating: 4.9, reviews: 145 },
];

/**
 * Materials supply, A to Z.
 *
 * The catalogue previously carried a single 'Material Supply' row standing in for the entire
 * materials trade, which is unbookable in practice — nobody orders "materials", they order TMT bars
 * or 53-grade cement. Each line here is a category a supplier actually quotes against, so a search
 * for "cement" or "tiles" lands on something real instead of on one generic card.
 */
const MATERIAL_SERVICES: Omit<ServiceEntry, 'slug'>[] = [
  { title: 'Adhesives & Sealants', category: 'Materials', icon: 'Opacity', price: 'Quote', rating: 4.3, reviews: 96 },
  { title: 'Admixtures & Additives', category: 'Materials', icon: 'Science', price: 'Quote', rating: 4.4, reviews: 71 },
  { title: 'Aggregates & Crushed Stone', category: 'Materials', icon: 'Grain', price: '₹1100/ton', rating: 4.4, reviews: 184 },
  { title: 'Aluminium Sections', category: 'Materials', icon: 'ViewWeek', price: '₹280/kg', rating: 4.5, reviews: 118 },
  { title: 'Bitumen & Asphalt', category: 'Materials', icon: 'Layers', price: 'Quote', rating: 4.2, reviews: 63 },
  { title: 'Bricks & Blocks', category: 'Materials', icon: 'ViewModule', price: '₹9/piece', rating: 4.5, reviews: 421 },
  { title: 'Cement', category: 'Materials', icon: 'Inventory', price: '₹390/bag', rating: 4.7, reviews: 892 },
  { title: 'Ceramic & Vitrified Tiles', category: 'Materials', icon: 'GridOn', price: '₹65/sqft', rating: 4.6, reviews: 512 },
  { title: 'Concrete (Ready Mix)', category: 'Materials', icon: 'LocalShipping', price: '₹4800/cum', rating: 4.6, reviews: 267 },
  { title: 'Doors & Windows', category: 'Materials', icon: 'MeetingRoom', price: 'Quote', rating: 4.5, reviews: 338 },
  { title: 'Electrical Fittings & Switches', category: 'Materials', icon: 'ElectricalServices', price: 'Quote', rating: 4.4, reviews: 402 },
  { title: 'Elevators & Lifts', category: 'Materials', icon: 'Elevator', price: 'Quote', rating: 4.6, reviews: 58 },
  { title: 'False Ceiling Materials', category: 'Materials', icon: 'Dashboard', price: '₹85/sqft', rating: 4.3, reviews: 176 },
  { title: 'Flooring Materials', category: 'Materials', icon: 'GridView', price: '₹120/sqft', rating: 4.6, reviews: 389 },
  { title: 'Glass & Glazing', category: 'Materials', icon: 'Window', price: '₹190/sqft', rating: 4.4, reviews: 143 },
  { title: 'Granite & Marble', category: 'Materials', icon: 'Diamond', price: '₹180/sqft', rating: 4.7, reviews: 445 },
  { title: 'Gypsum & Plaster', category: 'Materials', icon: 'FormatPaint', price: '₹340/bag', rating: 4.3, reviews: 129 },
  { title: 'Hardware & Fasteners', category: 'Materials', icon: 'Hardware', price: 'Quote', rating: 4.4, reviews: 287 },
  { title: 'Insulation Materials', category: 'Materials', icon: 'AcUnit', price: 'Quote', rating: 4.2, reviews: 74 },
  { title: 'Iron & TMT Steel Bars', category: 'Materials', icon: 'Straighten', price: '₹58/kg', rating: 4.8, reviews: 731 },
  { title: 'Kitchen Fittings & Modular', category: 'Materials', icon: 'Kitchen', price: 'Quote', rating: 4.5, reviews: 214 },
  { title: 'Lighting Fixtures', category: 'Materials', icon: 'Lightbulb', price: 'Quote', rating: 4.4, reviews: 261 },
  { title: 'Lime & Mortar', category: 'Materials', icon: 'Blender', price: '₹260/bag', rating: 4.1, reviews: 82 },
  { title: 'Paints & Coatings', category: 'Materials', icon: 'FormatColorFill', price: '₹280/litre', rating: 4.6, reviews: 623 },
  { title: 'Pipes & Fittings', category: 'Materials', icon: 'Plumbing', price: 'Quote', rating: 4.5, reviews: 356 },
  { title: 'Plywood & Timber', category: 'Materials', icon: 'Forest', price: '₹95/sqft', rating: 4.5, reviews: 298 },
  { title: 'Precast Concrete Products', category: 'Materials', icon: 'Foundation', price: 'Quote', rating: 4.4, reviews: 91 },
  { title: 'PVC & UPVC Panels', category: 'Materials', icon: 'ViewQuilt', price: '₹75/sqft', rating: 4.2, reviews: 137 },
  { title: 'Railings & Grills', category: 'Materials', icon: 'Fence', price: 'Quote', rating: 4.3, reviews: 165 },
  { title: 'Roofing Sheets', category: 'Materials', icon: 'Roofing', price: '₹340/sqm', rating: 4.4, reviews: 203 },
  { title: 'Sand & Filling Material', category: 'Materials', icon: 'Waves', price: '₹1600/ton', rating: 4.3, reviews: 376 },
  { title: 'Sanitaryware & Bath Fittings', category: 'Materials', icon: 'Bathtub', price: 'Quote', rating: 4.5, reviews: 419 },
  { title: 'Scaffolding & Shuttering', category: 'Materials', icon: 'Carpenter', price: '₹22/sqft', rating: 4.3, reviews: 148 },
  { title: 'Solar Panels & Systems', category: 'Materials', icon: 'SolarPower', price: 'Quote', rating: 4.6, reviews: 112 },
  { title: 'Stone & Cladding', category: 'Materials', icon: 'Terrain', price: '₹140/sqft', rating: 4.4, reviews: 187 },
  { title: 'Waterproofing Materials', category: 'Materials', icon: 'WaterDrop', price: 'Quote', rating: 4.5, reviews: 241 },
  { title: 'Wire & Cables', category: 'Materials', icon: 'Cable', price: 'Quote', rating: 4.5, reviews: 305 },
];

/**
 * Plant, machinery and tools, A to Z — hire or with operator.
 *
 * Small tools are listed alongside the heavy plant on purpose. A site that needs a needle vibrator
 * or a bar bender for two days has a real, bookable requirement, and a catalogue that only carries
 * excavators and cranes silently tells those users the platform is not for them. Cheap to list,
 * and the ones people search for most often are frequently the smallest.
 */
const EQUIPMENT_SERVICES: Omit<ServiceEntry, 'slug'>[] = [
  { title: 'Air Compressor', category: 'Equipment', icon: 'Air', price: '₹1200/day', rating: 4.3, reviews: 142 },
  { title: 'Angle Grinder', category: 'Equipment', icon: 'Build', price: '₹250/day', rating: 4.2, reviews: 231 },
  { title: 'Asphalt Paver', category: 'Equipment', icon: 'Construction', price: '₹18000/day', rating: 4.5, reviews: 37 },
  { title: 'Backhoe Loader (JCB)', category: 'Equipment', icon: 'Agriculture', price: '₹6500/day', rating: 4.7, reviews: 486 },
  { title: 'Bar Bending Machine', category: 'Equipment', icon: 'Straighten', price: '₹900/day', rating: 4.5, reviews: 178 },
  { title: 'Bar Cutting Machine', category: 'Equipment', icon: 'ContentCut', price: '₹850/day', rating: 4.5, reviews: 164 },
  { title: 'Batching Plant', category: 'Equipment', icon: 'Factory', price: 'Quote', rating: 4.6, reviews: 52 },
  { title: 'Boom Lift', category: 'Equipment', icon: 'Height', price: '₹5500/day', rating: 4.4, reviews: 96 },
  { title: 'Bulldozer', category: 'Equipment', icon: 'Agriculture', price: '₹12000/day', rating: 4.6, reviews: 118 },
  { title: 'Chain Pulley Block', category: 'Equipment', icon: 'Link', price: '₹300/day', rating: 4.2, reviews: 87 },
  { title: 'Concrete Mixer', category: 'Equipment', icon: 'Blender', price: '₹1400/day', rating: 4.5, reviews: 392 },
  { title: 'Concrete Pump', category: 'Equipment', icon: 'Plumbing', price: '₹9500/day', rating: 4.6, reviews: 128 },
  { title: 'Core Cutting Machine', category: 'Equipment', icon: 'RadioButtonUnchecked', price: '₹1800/day', rating: 4.3, reviews: 74 },
  { title: 'Crawler Crane', category: 'Equipment', icon: 'PrecisionManufacturing', price: 'Quote', rating: 4.6, reviews: 41 },
  { title: 'Cube Testing Machine', category: 'Equipment', icon: 'Science', price: 'Quote', rating: 4.4, reviews: 39 },
  { title: 'Dewatering Pump', category: 'Equipment', icon: 'WaterDrop', price: '₹700/day', rating: 4.4, reviews: 203 },
  { title: 'Drilling Rig', category: 'Equipment', icon: 'Hardware', price: 'Quote', rating: 4.5, reviews: 68 },
  { title: 'Excavator', category: 'Equipment', icon: 'Agriculture', price: '₹8500/day', rating: 4.7, reviews: 421 },
  { title: 'Forklift', category: 'Equipment', icon: 'Warehouse', price: '₹3200/day', rating: 4.4, reviews: 157 },
  { title: 'Generator (DG Set)', category: 'Equipment', icon: 'ElectricBolt', price: '₹2200/day', rating: 4.5, reviews: 314 },
  { title: 'Hand Tools & Consumables', category: 'Equipment', icon: 'Handyman', price: 'Quote', rating: 4.3, reviews: 458 },
  { title: 'Hydra Crane', category: 'Equipment', icon: 'PrecisionManufacturing', price: '₹7000/day', rating: 4.5, reviews: 189 },
  { title: 'Jackhammer / Breaker', category: 'Equipment', icon: 'Construction', price: '₹1100/day', rating: 4.4, reviews: 267 },
  { title: 'Ladders & Step Platforms', category: 'Equipment', icon: 'Stairs', price: '₹180/day', rating: 4.1, reviews: 176 },
  { title: 'Motor Grader', category: 'Equipment', icon: 'Agriculture', price: '₹11000/day', rating: 4.5, reviews: 63 },
  { title: 'Needle Vibrator', category: 'Equipment', icon: 'Vibration', price: '₹450/day', rating: 4.5, reviews: 341 },
  { title: 'Piling Rig', category: 'Equipment', icon: 'Foundation', price: 'Quote', rating: 4.6, reviews: 57 },
  { title: 'Plate Compactor', category: 'Equipment', icon: 'Layers', price: '₹950/day', rating: 4.4, reviews: 224 },
  { title: 'Power Drill & Breaker Set', category: 'Equipment', icon: 'Build', price: '₹350/day', rating: 4.3, reviews: 289 },
  { title: 'Power Trowel', category: 'Equipment', icon: 'Brush', price: '₹1300/day', rating: 4.3, reviews: 98 },
  { title: 'Road Roller', category: 'Equipment', icon: 'Agriculture', price: '₹7500/day', rating: 4.6, reviews: 176 },
  { title: 'Safety Equipment & PPE', category: 'Equipment', icon: 'HealthAndSafety', price: 'Quote', rating: 4.6, reviews: 512 },
  { title: 'Scissor Lift', category: 'Equipment', icon: 'Height', price: '₹4200/day', rating: 4.4, reviews: 84 },
  { title: 'Shotcrete Machine', category: 'Equipment', icon: 'Colorize', price: 'Quote', rating: 4.3, reviews: 31 },
  { title: 'Skid Steer Loader', category: 'Equipment', icon: 'Agriculture', price: '₹5800/day', rating: 4.4, reviews: 72 },
  { title: 'Stone Crusher', category: 'Equipment', icon: 'Grain', price: 'Quote', rating: 4.4, reviews: 46 },
  { title: 'Survey Instruments (Total Station)', category: 'Equipment', icon: 'Straighten', price: '₹2500/day', rating: 4.7, reviews: 193 },
  { title: 'Telehandler', category: 'Equipment', icon: 'Warehouse', price: '₹6800/day', rating: 4.4, reviews: 58 },
  { title: 'Tower Crane', category: 'Equipment', icon: 'PrecisionManufacturing', price: 'Quote', rating: 4.7, reviews: 89 },
  { title: 'Trencher', category: 'Equipment', icon: 'Construction', price: '₹4800/day', rating: 4.3, reviews: 44 },
  { title: 'Welding Machine', category: 'Equipment', icon: 'LocalFireDepartment', price: '₹800/day', rating: 4.5, reviews: 276 },
  { title: 'Winch & Hoist', category: 'Equipment', icon: 'Link', price: '₹1500/day', rating: 4.3, reviews: 112 },
];

/**
 * Site vehicles, A to Z — with driver unless noted at booking.
 *
 * Split from Equipment rather than folded into it: what you book differs (a vehicle is hired by
 * trip or tonnage, plant by the day), and someone looking for a transit mixer should not have to
 * scroll past forty machines to find it.
 */
const VEHICLE_SERVICES: Omit<ServiceEntry, 'slug'>[] = [
  { title: 'Boom Truck', category: 'Vehicles', icon: 'FireTruck', price: '₹6500/day', rating: 4.4, reviews: 67 },
  { title: 'Bulk Cement Tanker', category: 'Vehicles', icon: 'LocalShipping', price: 'Quote', rating: 4.4, reviews: 53 },
  { title: 'Concrete Transit Mixer', category: 'Vehicles', icon: 'LocalShipping', price: '₹5200/trip', rating: 4.6, reviews: 238 },
  { title: 'Dumper Truck', category: 'Vehicles', icon: 'LocalShipping', price: '₹4200/day', rating: 4.5, reviews: 291 },
  { title: 'Flatbed Trailer', category: 'Vehicles', icon: 'LocalShipping', price: 'Quote', rating: 4.3, reviews: 84 },
  { title: 'Hyva Tipper', category: 'Vehicles', icon: 'LocalShipping', price: '₹4800/day', rating: 4.5, reviews: 176 },
  { title: 'Lowbed Trailer', category: 'Vehicles', icon: 'LocalShipping', price: 'Quote', rating: 4.4, reviews: 61 },
  { title: 'Mini Truck (Tata Ace)', category: 'Vehicles', icon: 'AirportShuttle', price: '₹1800/day', rating: 4.4, reviews: 342 },
  { title: 'Multi-Axle Trailer', category: 'Vehicles', icon: 'LocalShipping', price: 'Quote', rating: 4.3, reviews: 49 },
  { title: 'Pickup Truck', category: 'Vehicles', icon: 'AirportShuttle', price: '₹2200/day', rating: 4.4, reviews: 218 },
  { title: 'Tractor with Trolley', category: 'Vehicles', icon: 'Agriculture', price: '₹2600/day', rating: 4.5, reviews: 264 },
  { title: 'Truck (6-Wheeler)', category: 'Vehicles', icon: 'LocalShipping', price: '₹3800/day', rating: 4.4, reviews: 187 },
  { title: 'Truck (10-Wheeler)', category: 'Vehicles', icon: 'LocalShipping', price: '₹5400/day', rating: 4.4, reviews: 143 },
  { title: 'Water Tanker', category: 'Vehicles', icon: 'WaterDrop', price: '₹2400/trip', rating: 4.5, reviews: 396 },
];
// Declared above FALLBACK_SERVICES because that list attaches these to its entries as it is
// built: a `const` referenced before its own initialiser has run is a temporal-dead-zone
// error at module load, which took the whole catalogue down rather than just the aliases.


/**
 * Trade names and abbreviations people actually type, mapped to catalogue slugs.
 *
 * Without these, search only works if you already know the catalogue's own wording: someone
 * looking for "rebar", "sariya" or "jcb" gets nothing, even though the platform lists exactly what
 * they want under a more formal title. This is the gap between what a catalogue is called and what
 * a site engineer calls it.
 */
const ALIASES: Record<string, string[]> = {
  'iron-and-tmt-steel-bars': ['steel', 'rebar', 'tmt', 'sariya', 'saria', 'reinforcement', 'bars'],
  cement: ['opc', 'ppc', 'grade', 'bag', 'concrete'],
  'backhoe-loader-jcb': ['jcb', 'digger', 'loader', 'earthmover'],
  excavator: ['poclain', 'digger', 'earthmover', 'hitachi'],
  'concrete-ready-mix': ['rmc', 'readymix', 'transit'],
  'concrete-transit-mixer': ['rmc', 'mixer', 'transit'],
  'aggregates-and-crushed-stone': ['jelly', 'kapchi', 'metal', 'gitti', 'ballast'],
  'sand-and-filling-material': ['reti', 'river sand', 'msand', 'm-sand'],
  'bricks-and-blocks': ['aac', 'block', 'clay', 'itta'],
  'ceramic-and-vitrified-tiles': ['tile', 'flooring', 'vitrified'],
  'granite-and-marble': ['stone', 'countertop', 'slab'],
  'paints-and-coatings': ['paint', 'primer', 'emulsion', 'distemper'],
  'pipes-and-fittings': ['pvc', 'cpvc', 'upvc', 'plumbing'],
  'sanitaryware-and-bath-fittings': ['toilet', 'washbasin', 'cp fittings', 'bathroom'],
  'wire-and-cables': ['wiring', 'cable', 'electrical'],
  'plywood-and-timber': ['wood', 'ply', 'lumber'],
  'roofing-sheets': ['shed', 'tin', 'gi sheet', 'roof'],
  'generator-dg-set': ['genset', 'dg', 'generator'],
  'survey-instruments-total-station': ['total station', 'theodolite', 'dumpy level', 'survey'],
  'needle-vibrator': ['vibrator', 'compaction'],
  'road-roller': ['roller', 'compactor'],
  'tower-crane': ['crane'],
  'hydra-crane': ['crane', 'hydra'],
  'safety-equipment-and-ppe': ['helmet', 'harness', 'gloves', 'ppe', 'safety'],
  'hand-tools-and-consumables': ['tools', 'trowel', 'hammer', 'spade'],
  'architecture-design': ['architect', 'naksha', 'plan'],
  'structural-engineering': ['structure', 'rcc', 'engineer', 'design'],
  'land-survey': ['surveyor', 'plot', 'measurement'],
  'interior-design': ['interior', 'decorator', 'designer'],
  'skilled-labour': ['mason', 'carpenter', 'fitter', 'mistri', 'welder', 'painter', 'labour'],
  'daily-wage-labour': ['helper', 'coolie', 'labour', 'mazdoor'],
  'contractor-services': ['contractor', 'thekedar', 'builder'],
  'building-construction': ['builder', 'construction', 'contractor'],
  'water-tanker': ['water', 'tanker'],
  'dumper-truck': ['tipper', 'truck', 'dumper'],
  'mini-truck-tata-ace': ['chota hathi', 'tempo', 'ace', 'mini truck'],
};


/**
 * The whole catalogue, A-Z by title.
 *
 * Sorted once here rather than at each call site: the page re-sorts by rating or popularity on
 * demand, and alphabetical is what it falls back to, so the order has to be right in the data.
 */
export const FALLBACK_SERVICES: ServiceEntry[] = [
  ...PROFESSIONAL_SERVICES,
  ...MATERIAL_SERVICES,
  ...EQUIPMENT_SERVICES,
  ...VEHICLE_SERVICES,
]
  .map((entry) => {
    const slug = slugify(entry.title);
    return { ...entry, slug, aliases: ALIASES[slug] };
  })
  .sort((a, b) => a.title.localeCompare(b.title));

/** The numeric part of a price, or null for 'Quote'. Shared so the page and the slider agree. */
export const numericPrice = (price: string): number | null => {
  const match = price.match(/\d+/);
  return match ? Number(match[0]) : null;
};

/**
 * Upper bound for the price filter, rounded up to a clean step above the dearest item.
 *
 * Derived from the list in hand rather than hard-coded: the slider used to cap at ₹5000, which was
 * fine when the catalogue topped out below that and silently hid every piece of heavy plant the
 * moment machinery was added. Now that admins can add items at any price, a fixed ceiling would go
 * stale the first time one is created above it — a filter the user never touched must not be able
 * to hide rows.
 */
export const priceCeiling = (services: ServiceEntry[]): number => {
  const highest = services.reduce((max, service) => {
    const price = numericPrice(service.price);
    return price !== null && price > max ? price : max;
  }, 0);
  return Math.max(1000, Math.ceil(highest / 1000) * 1000);
};

/** Resolves a URL segment back to a category, case-insensitively. `null` means "show everything". */
export const categoryFromSlug = (
  slug: string | undefined,
  categories: ServiceCategory[],
): ServiceCategory | null => {
  if (!slug) return null;
  return categories.find((category) => slugify(category) === slug.toLowerCase()) ?? null;
};

export const serviceBySlug = (
  slug: string | undefined,
  services: ServiceEntry[],
): ServiceEntry | undefined =>
  slug ? services.find((service) => service.slug === slug.toLowerCase()) : undefined;

/** Everything a service can be matched on, lowercased once at module load. */
interface SearchIndexEntry {
  service: ServiceEntry;
  title: string;
  haystack: string;
}

/**
 * Builds the index for one catalogue.
 *
 * Rebuilt per call rather than computed once at module load: the catalogue now arrives from the API
 * and changes whenever an admin edits it, so an index frozen at import time would keep answering
 * for a list that is no longer on screen. Aliases come off the entry itself — for API rows they are
 * whatever the admin typed, for the fallback rows the `ALIASES` map above.
 */
const buildIndex = (services: ServiceEntry[]): SearchIndexEntry[] =>
  services.map((service) => ({
    service,
    title: service.title.toLowerCase(),
    haystack: [service.title, service.category, ...(service.aliases ?? ALIASES[service.slug] ?? [])]
      .join(' ')
      .toLowerCase(),
  }));

/**
 * Substring search across the whole catalogue, materials and machinery included.
 *
 * "Contains", not "equals", and per word: `steel bars` matches "Iron & TMT Steel Bars" even though
 * neither word starts it, and `tmt` matches through the alias list. Every word must appear
 * somewhere, so extra words narrow rather than widen — otherwise a two-word query returns more
 * than a one-word one, which is the opposite of what typing more is meant to do.
 *
 * Results are ranked so an exact title lands above a title that merely starts with the query,
 * above one that contains it, above an alias-only hit. Without the ranking "cement" would surface
 * every row whose alias mentions concrete before Cement itself.
 */
export const searchServices = (
  query: string,
  services: ServiceEntry[],
  limit = 50,
): ServiceEntry[] => {
  const words = query.toLowerCase().trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return [];

  const scored: { service: ServiceEntry; score: number }[] = [];

  for (const entry of buildIndex(services)) {
    if (!words.every((word) => entry.haystack.includes(word))) continue;

    const whole = query.toLowerCase().trim();
    let score = 1;
    if (entry.title === whole) score = 100;
    else if (entry.title.startsWith(whole)) score = 60;
    else if (entry.title.includes(whole)) score = 40;
    else if (words.every((word) => entry.title.includes(word))) score = 25;

    scored.push({ service: entry.service, score });
  }

  return scored
    .sort((a, b) => b.score - a.score || a.service.title.localeCompare(b.service.title))
    .slice(0, limit)
    .map((row) => row.service);
};
