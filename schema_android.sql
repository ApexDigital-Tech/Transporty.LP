-- SQL Schema for Android App "Rutas Minibús La Paz" on Supabase

-- 1. Create Drivers Table
CREATE TABLE IF NOT EXISTS drivers (
    phone TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    license_plate TEXT NOT NULL,
    status TEXT NOT NULL, -- "En Ruta", "Fuera de Servicio", "Descanso"
    current_route TEXT NOT NULL,
    earnings INTEGER NOT NULL DEFAULT 0,
    points INTEGER NOT NULL DEFAULT 0,
    last_updated BIGINT NOT NULL DEFAULT (extract(epoch from now()) * 1000)::bigint,
    tenant_id TEXT NOT NULL DEFAULT 'sindicato_central'
);

-- 2. Create Shifts Table
CREATE TABLE IF NOT EXISTS shifts (
    id SERIAL PRIMARY KEY,
    driver_phone TEXT REFERENCES drivers(phone) ON DELETE CASCADE,
    route_code TEXT NOT NULL,
    start_time BIGINT NOT NULL,
    end_time BIGINT NOT NULL DEFAULT 0,
    earnings INTEGER NOT NULL DEFAULT 0,
    points INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL, -- "Activo", "Completado"
    tenant_id TEXT NOT NULL DEFAULT 'sindicato_central'
);

-- Enable RLS (Row Level Security)
ALTER TABLE drivers ENABLE ROW LEVEL SECURITY;
ALTER TABLE shifts ENABLE ROW LEVEL SECURITY;

-- Create Policies for Multi-Tenancy (Filter by tenant_id)
-- Note: In a production SaaS, these policies would use the authenticated user's organization claims.
-- For the MVP, we allow all reads/writes but ensure queries filter by tenant_id on the client.

CREATE POLICY "Allow public read of drivers" ON drivers FOR SELECT USING (true);
CREATE POLICY "Allow public insert of drivers" ON drivers FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow public update of drivers" ON drivers FOR UPDATE USING (true) WITH CHECK (true);
CREATE POLICY "Allow public delete of drivers" ON drivers FOR DELETE USING (true);

CREATE POLICY "Allow public read of shifts" ON shifts FOR SELECT USING (true);
CREATE POLICY "Allow public insert of shifts" ON shifts FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow public update of shifts" ON shifts FOR UPDATE USING (true) WITH CHECK (true);
CREATE POLICY "Allow public delete of shifts" ON shifts FOR DELETE USING (true);

-- Enable Realtime Replication for Realtime synchronization
ALTER PUBLICATION supabase_realtime ADD TABLE drivers;
ALTER PUBLICATION supabase_realtime ADD TABLE shifts;
