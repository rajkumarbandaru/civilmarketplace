-- ============================================================================
-- User Service - Supplier material price list
--
-- Suppliers publish a rate per material per city; estimates read the low/high
-- range back out, with the supplier user ID behind each end so a quoted figure
-- can be traced to a real listing.
-- ============================================================================

CREATE TABLE material_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(200) NOT NULL,
    category VARCHAR(100),
    -- Matches the MaterialUnit enum. Kept as a string so a new unit is a code
    -- change, not a data migration.
    unit VARCHAR(20) NOT NULL,
    specification VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY idx_material_slug (slug),
    INDEX idx_material_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE supplier_material_prices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    supplier_user_id BIGINT NOT NULL,
    material_item_id BIGINT NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    city VARCHAR(100) NOT NULL,
    brand VARCHAR(150),
    min_order_quantity DECIMAL(12,2),
    delivery_included BOOLEAN NOT NULL DEFAULT FALSE,
    notes VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    valid_until TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- One live rate per supplier, material and city: without this a single
    -- supplier can occupy both ends of a range and make it look competitive.
    UNIQUE KEY uk_supplier_material_city (supplier_user_id, material_item_id, city),
    INDEX idx_smp_supplier (supplier_user_id),
    INDEX idx_smp_material (material_item_id),
    INDEX idx_smp_city (city),
    CONSTRAINT fk_smp_material FOREIGN KEY (material_item_id)
        REFERENCES material_items (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- The shared catalogue. Suppliers quote against these rows rather than typing
-- their own names, which is what makes two suppliers' rates comparable.
INSERT INTO material_items (name, slug, category, unit, specification) VALUES
('OPC 43 Grade Cement', 'cement-opc-43', 'Cement', 'BAG', '50 kg bag, IS 8112'),
('OPC 53 Grade Cement', 'cement-opc-53', 'Cement', 'BAG', '50 kg bag, IS 12269'),
('PPC Cement', 'cement-ppc', 'Cement', 'BAG', '50 kg bag, IS 1489'),
('White Cement', 'cement-white', 'Cement', 'KG', 'For finishing and joints'),
('River Sand', 'sand-river', 'Aggregates', 'CUBIC_FEET', 'Screened, for concrete and plaster'),
('M-Sand (Manufactured Sand)', 'sand-manufactured', 'Aggregates', 'CUBIC_FEET', 'Crushed stone sand, IS 383'),
('P-Sand (Plastering Sand)', 'sand-plastering', 'Aggregates', 'CUBIC_FEET', 'Fine grade for plaster'),
('20 mm Coarse Aggregate', 'aggregate-20mm', 'Aggregates', 'CUBIC_FEET', 'Crushed stone, IS 383'),
('12 mm Coarse Aggregate', 'aggregate-12mm', 'Aggregates', 'CUBIC_FEET', 'Crushed stone, IS 383'),
('40 mm Coarse Aggregate', 'aggregate-40mm', 'Aggregates', 'CUBIC_FEET', 'For PCC and soling'),
('TMT Steel Bar 8 mm', 'steel-tmt-8mm', 'Steel', 'KG', 'Fe 500 / Fe 500D, IS 1786'),
('TMT Steel Bar 10 mm', 'steel-tmt-10mm', 'Steel', 'KG', 'Fe 500 / Fe 500D, IS 1786'),
('TMT Steel Bar 12 mm', 'steel-tmt-12mm', 'Steel', 'KG', 'Fe 500 / Fe 500D, IS 1786'),
('TMT Steel Bar 16 mm', 'steel-tmt-16mm', 'Steel', 'KG', 'Fe 500 / Fe 500D, IS 1786'),
('TMT Steel Bar 20 mm', 'steel-tmt-20mm', 'Steel', 'KG', 'Fe 500 / Fe 500D, IS 1786'),
('Binding Wire', 'steel-binding-wire', 'Steel', 'KG', 'Annealed, 18 gauge'),
('MS Structural Steel', 'steel-ms-structural', 'Steel', 'KG', 'Angles, channels and beams'),
('Red Clay Brick', 'brick-red-clay', 'Masonry', 'NUMBER', 'Standard 230 x 110 x 75 mm'),
('Fly Ash Brick', 'brick-fly-ash', 'Masonry', 'NUMBER', 'Standard 230 x 110 x 75 mm'),
('AAC Block', 'block-aac', 'Masonry', 'NUMBER', '600 x 200 x 200 mm autoclaved block'),
('Solid Concrete Block', 'block-solid-concrete', 'Masonry', 'NUMBER', '400 x 200 x 200 mm'),
('Hollow Concrete Block', 'block-hollow-concrete', 'Masonry', 'NUMBER', '400 x 200 x 200 mm'),
('Ready Mix Concrete M20', 'rmc-m20', 'Concrete', 'CUBIC_METRE', 'Design mix, pump delivered'),
('Ready Mix Concrete M25', 'rmc-m25', 'Concrete', 'CUBIC_METRE', 'Design mix, pump delivered'),
('Ready Mix Concrete M30', 'rmc-m30', 'Concrete', 'CUBIC_METRE', 'Design mix, pump delivered'),
('Vitrified Floor Tile', 'tile-vitrified', 'Finishes', 'SQUARE_FEET', 'Double charged, 600 x 600 mm'),
('Ceramic Wall Tile', 'tile-ceramic-wall', 'Finishes', 'SQUARE_FEET', 'Glazed, for wet areas'),
('Granite Slab', 'stone-granite', 'Finishes', 'SQUARE_FEET', 'Polished, 18 mm'),
('Marble Slab', 'stone-marble', 'Finishes', 'SQUARE_FEET', 'Polished, 18 mm'),
('Tile Adhesive', 'tile-adhesive', 'Finishes', 'BAG', '20 kg bag'),
('Wall Putty', 'putty-wall', 'Painting', 'BAG', '20 kg bag, white cement based'),
('Primer', 'paint-primer', 'Painting', 'LITRE', 'Interior or exterior wall primer'),
('Interior Emulsion Paint', 'paint-interior-emulsion', 'Painting', 'LITRE', 'Washable acrylic emulsion'),
('Exterior Emulsion Paint', 'paint-exterior-emulsion', 'Painting', 'LITRE', 'Weatherproof acrylic emulsion'),
('Enamel Paint', 'paint-enamel', 'Painting', 'LITRE', 'For wood and metal'),
('Waterproofing Compound', 'waterproofing-compound', 'Waterproofing', 'KG', 'Integral or coating type'),
('APP Membrane', 'waterproofing-app-membrane', 'Waterproofing', 'SQUARE_METRE', 'Torch applied, 3 mm'),
('CPVC Pipe 1 inch', 'plumbing-cpvc-25mm', 'Plumbing', 'RUNNING_METRE', 'SDR 11, hot and cold water'),
('UPVC Pipe 4 inch', 'plumbing-upvc-110mm', 'Plumbing', 'RUNNING_METRE', 'Soil, waste and rainwater'),
('PVC Pipe 2 inch', 'plumbing-pvc-50mm', 'Plumbing', 'RUNNING_METRE', 'Waste water'),
('Water Tank 1000 L', 'plumbing-water-tank-1000l', 'Plumbing', 'NUMBER', 'Triple layer, food grade'),
('Copper Wire 1.5 sq.mm', 'electrical-wire-1-5', 'Electrical', 'RUNNING_METRE', 'FR PVC insulated, lighting'),
('Copper Wire 2.5 sq.mm', 'electrical-wire-2-5', 'Electrical', 'RUNNING_METRE', 'FR PVC insulated, power'),
('PVC Conduit 25 mm', 'electrical-conduit-25mm', 'Electrical', 'RUNNING_METRE', 'ISI concealed conduit'),
('Modular Switch Board', 'electrical-switch-board', 'Electrical', 'NUMBER', '6 to 8 module, with plate'),
('Flush Door 32 mm', 'door-flush-32mm', 'Doors and Windows', 'SQUARE_FEET', 'Solid core, BWP grade'),
('Teak Wood Door Frame', 'door-frame-teak', 'Doors and Windows', 'RUNNING_METRE', 'Seasoned hardwood'),
('UPVC Window', 'window-upvc', 'Doors and Windows', 'SQUARE_FEET', 'Sliding, with mesh and glass'),
('Aluminium Window', 'window-aluminium', 'Doors and Windows', 'SQUARE_FEET', 'Powder coated, sliding'),
('MS Grill', 'grill-ms', 'Doors and Windows', 'SQUARE_FEET', 'Fabricated and painted');
