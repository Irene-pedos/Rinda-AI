-- Demo data for the dev profile only. Fixed ids from 9001 make this safe to re-run.
-- Everything here is fictional and is_demo = TRUE.
-- The polygons are placeholder locations, not farms, so these plots are data_status = 'TO_CONFIRM'.
-- Planting dates, sums insured and premiums stay NULL until the team confirms them.
-- Replace the plots with real ones when docs/OPEN_QUESTIONS.md question 1 is answered.

INSERT INTO cooperatives (id, name, district, sector, is_demo) VALUES
    (9001, 'Demo Cooperative', 'TO_CONFIRM', NULL, TRUE)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO users (id, phone, full_name, role, language, is_demo) VALUES
    (9001, '+250780000001', 'Demo Farmer One', 'FARMER',  'en', TRUE),
    (9002, '+250780000002', 'Demo Farmer Two', 'FARMER',  'en', TRUE),
    (9003, '+250780000003', 'Demo Agent',      'AGENT',   'en', TRUE),
    (9004, '+250780000004', 'Demo Officer',    'OFFICER', 'en', TRUE),
    (9005, '+250780000005', 'Demo Admin',      'ADMIN',   'en', TRUE)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO farmers (user_id, cooperative_id) VALUES
    (9001, 9001),
    (9002, 9001)
ON DUPLICATE KEY UPDATE user_id = user_id;

INSERT INTO agents (user_id, cooperative_id, approved_by) VALUES
    (9003, 9001, 9005)
ON DUPLICATE KEY UPDATE user_id = user_id;

INSERT INTO seasons (id, code, type, start_date, end_date, data_status, is_demo) VALUES
    (9001, 'DEMO', 'A', NULL, NULL, 'TO_CONFIRM', TRUE)
ON DUPLICATE KEY UPDATE id = id;

SET @p1 = '{"type":"Polygon","coordinates":[[[30.1000,-1.9500],[30.1020,-1.9500],[30.1020,-1.9480],[30.1000,-1.9480],[30.1000,-1.9500]]]}';
SET @p2 = '{"type":"Polygon","coordinates":[[[30.1030,-1.9500],[30.1050,-1.9500],[30.1050,-1.9480],[30.1030,-1.9480],[30.1030,-1.9500]]]}';
SET @p3 = '{"type":"Polygon","coordinates":[[[30.1060,-1.9500],[30.1080,-1.9500],[30.1080,-1.9480],[30.1060,-1.9480],[30.1060,-1.9500]]]}';
SET @p4 = '{"type":"Polygon","coordinates":[[[30.1090,-1.9500],[30.1110,-1.9500],[30.1110,-1.9480],[30.1090,-1.9480],[30.1090,-1.9500]]]}';

INSERT INTO plots (id, farmer_id, name, boundary, geojson, area_ha, status, verified_by, data_status, is_demo) VALUES
    (9001, 9001, 'Demo plot 1', ST_GeomFromGeoJSON(@p1), @p1, ROUND(ST_Area(ST_GeomFromGeoJSON(@p1)) / 10000, 4), 'ACTIVE', 9003, 'TO_CONFIRM', TRUE),
    (9002, 9001, 'Demo plot 2', ST_GeomFromGeoJSON(@p2), @p2, ROUND(ST_Area(ST_GeomFromGeoJSON(@p2)) / 10000, 4), 'ACTIVE', 9003, 'TO_CONFIRM', TRUE),
    (9003, 9001, 'Demo plot 3', ST_GeomFromGeoJSON(@p3), @p3, ROUND(ST_Area(ST_GeomFromGeoJSON(@p3)) / 10000, 4), 'ACTIVE', 9003, 'TO_CONFIRM', TRUE),
    (9004, 9002, 'Demo plot 4', ST_GeomFromGeoJSON(@p4), @p4, ROUND(ST_Area(ST_GeomFromGeoJSON(@p4)) / 10000, 4), 'PENDING_VERIFICATION', NULL, 'TO_CONFIRM', TRUE)
ON DUPLICATE KEY UPDATE id = plots.id;

INSERT INTO enrollments (id, plot_id, season_id, status, is_demo) VALUES
    (9001, 9001, 9001, 'COVERED', TRUE),
    (9002, 9002, 9001, 'COVERED', TRUE),
    (9003, 9003, 9001, 'COVERED', TRUE)
ON DUPLICATE KEY UPDATE id = enrollments.id;
