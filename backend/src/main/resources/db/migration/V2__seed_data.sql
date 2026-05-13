-- ============================================================
-- V2: Seed Data — Dark Stores, Products & Local Events
-- ============================================================

-- ---- Dark Stores ----
INSERT INTO dark_stores (id, name, region, latitude, longitude) VALUES
('S001', 'North Hub Alpha',    'North', 28.7041,  77.1025),
('S002', 'South Hub Prime',    'South', 12.9716,  77.5946),
('S003', 'East Hub Central',   'East',  22.5726,  88.3639),
('S004', 'West Hub Express',   'West',  19.0760,  72.8777),
('S005', 'North Hub Beta',     'North', 28.4595,  77.0266);

-- ---- Products ----
INSERT INTO products (id, name, category, unit_price, reorder_threshold, reorder_quantity) VALUES
-- Groceries
('P0001', 'Premium Basmati Rice 5kg',   'Groceries',   33.50,  80, 300),
('P0006', 'Cold-Press Olive Oil 1L',    'Groceries',   76.83,  60, 250),
('P0010', 'Organic Whole Milk 1L',      'Groceries',   97.09,  90, 350),
('P0016', 'Instant Noodles Pack x5',    'Groceries',   98.18,  70, 400),
('P0018', 'Mineral Water 500ml x12',    'Groceries',   16.73, 100, 500),

-- Electronics
('P0005', 'USB-C Charging Cable 2m',    'Electronics', 73.64,  40, 150),
('P0009', 'Wireless Earbuds Pro',       'Electronics', 20.74,  30, 100),
('P0012', 'Phone Screen Protector',     'Electronics', 90.38,  50, 200),
('P0014', 'Portable Power Bank 10K',    'Electronics', 79.03,  35, 120),
('P0021', 'Smart LED Bulb E27',         'Electronics', 21.90,  60, 220),

-- Clothing
('P0002', 'Cotton Basic Tee S-XL',      'Clothing',    63.01,  45, 180),
('P0008', 'Athletic Socks 3-Pack',      'Clothing',    97.99,  55, 220),
('P0013', 'Casual Denim Shorts',        'Clothing',    43.60,  30, 100),
('P0015', 'Lightweight Hoodie M',       'Clothing',    92.99,  25,  80),
('P0019', 'Running Cap UV50+',          'Clothing',    73.28,  40, 150),

-- Toys
('P0003', 'Puzzle Set 500pcs',          'Toys',        27.99,  35, 120),
('P0004', 'Remote Control Car 1:18',    'Toys',        32.72,  25,  80),
('P0007', 'Building Blocks 100pcs',     'Toys',        34.16,  30, 100),
('P0017', 'Watercolour Paint Set',      'Toys',        21.07,  40, 150),
('P0020', 'Board Game Strategy',        'Toys',        30.24,  20,  60),

-- Furniture
('P0011', 'Foldable Study Desk',        'Furniture',   58.53,  15,  40),
('P0013b','Ergonomic Chair Cushion',    'Furniture',   61.81,  20,  60);

-- ---- Local Events (Static Seed) ----
-- These represent events that drive demand spikes in specific regions
INSERT INTO local_events (event_name, region, event_date, event_type, demand_multiplier, affected_categories) VALUES
-- National Holidays
('Republic Day',            'North', '2024-01-26', 'HOLIDAY',  1.40, NULL),
('Republic Day',            'South', '2024-01-26', 'HOLIDAY',  1.40, NULL),
('Republic Day',            'East',  '2024-01-26', 'HOLIDAY',  1.40, NULL),
('Republic Day',            'West',  '2024-01-26', 'HOLIDAY',  1.40, NULL),
('Independence Day',        'North', '2024-08-15', 'HOLIDAY',  1.45, NULL),
('Independence Day',        'South', '2024-08-15', 'HOLIDAY',  1.45, NULL),
('Independence Day',        'East',  '2024-08-15', 'HOLIDAY',  1.45, NULL),
('Independence Day',        'West',  '2024-08-15', 'HOLIDAY',  1.45, NULL),
('Diwali',                  'North', '2024-11-01', 'FESTIVAL', 1.80, 'Electronics,Clothing,Toys'),
('Diwali',                  'South', '2024-11-01', 'FESTIVAL', 1.80, 'Electronics,Clothing,Toys'),
('Diwali',                  'East',  '2024-11-01', 'FESTIVAL', 1.80, 'Electronics,Clothing,Toys'),
('Diwali',                  'West',  '2024-11-01', 'FESTIVAL', 1.80, 'Electronics,Clothing,Toys'),

-- Sports Events
('IPL Final - Delhi',       'North', '2024-05-26', 'SPORTS',   1.55, 'Groceries'),
('Champions Trophy Match',  'South', '2024-03-09', 'SPORTS',   1.35, 'Groceries,Electronics'),
('Pro Kabaddi - Mumbai',    'West',  '2024-09-15', 'SPORTS',   1.30, 'Groceries'),

-- Concerts / Entertainment
('Arijit Singh Live',       'North', '2024-02-17', 'CONCERT',  1.25, 'Groceries'),
('EDM Festival Bangalore',  'South', '2024-12-28', 'CONCERT',  1.35, 'Groceries,Electronics'),

-- Flash Sales / Promotions
('Big Billion Day',         'North', '2024-10-08', 'SALE',     2.00, NULL),
('Big Billion Day',         'South', '2024-10-08', 'SALE',     2.00, NULL),
('Big Billion Day',         'East',  '2024-10-08', 'SALE',     2.00, NULL),
('Big Billion Day',         'West',  '2024-10-08', 'SALE',     2.00, NULL),
('End of Season Sale',      'North', '2024-06-30', 'SALE',     1.60, 'Clothing,Toys'),
('End of Season Sale',      'South', '2024-06-30', 'SALE',     1.60, 'Clothing,Toys'),

-- Rainy Season (supply disruption drives panic buying)
('Monsoon Onset',           'South', '2024-06-01', 'FESTIVAL', 1.20, 'Groceries'),
('Monsoon Onset',           'East',  '2024-06-10', 'FESTIVAL', 1.20, 'Groceries'),
('Pre-Monsoon Surge',       'West',  '2024-05-25', 'FESTIVAL', 1.15, 'Groceries');
