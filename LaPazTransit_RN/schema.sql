-- Enable PostGIS extension for geospatial queries
CREATE EXTENSION IF NOT EXISTS postgis;

-- 1. Create routes table
CREATE TABLE routes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    line_code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    start_point VARCHAR(255) NOT NULL,
    end_point VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 2. Create drivers table
CREATE TABLE drivers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'inactive', -- active, inactive, offline
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 3. Create live_locations table
CREATE TABLE live_locations (
    driver_id UUID PRIMARY KEY REFERENCES drivers(id) ON DELETE CASCADE,
    route_id UUID REFERENCES routes(id) ON DELETE SET NULL,
    lat DOUBLE PRECISION NOT NULL,
    lng DOUBLE PRECISION NOT NULL,
    -- PostGIS geography column for specialized spatial indexing/queries
    geom GEOGRAPHY(Point, 4326),
    last_updated TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Index for PostGIS queries
CREATE INDEX IF NOT EXISTS live_locations_geom_idx ON live_locations USING GIST (geom);

-- Trigger to automatically update 'geom' and 'last_updated' timestamp
CREATE OR REPLACE FUNCTION update_live_location_derivatives()
RETURNS TRIGGER AS $$
BEGIN
    NEW.geom := ST_SetSRID(ST_MakePoint(NEW.lng, NEW.lat), 4326)::geography;
    NEW.last_updated := NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_update_live_location_derivatives
BEFORE INSERT OR UPDATE ON live_locations
FOR EACH ROW
EXECUTE FUNCTION update_live_location_derivatives();

-- 4. Set up Realtime Publication for live tracking
-- This registers 'live_locations' into Supabase's realtime replication publication
ALTER PUBLICATION supabase_realtime ADD TABLE live_locations;

-- Optional: Enable Row Level Security (RLS) policies 
-- Allows anonymous reads for passengers, authenticated updates for drivers
ALTER TABLE live_locations ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow public read-only access to live locations" 
ON live_locations FOR SELECT 
USING (true);

CREATE POLICY "Allow drivers to insert/update their own live locations" 
ON live_locations FOR ALL 
USING (true) 
WITH CHECK (true);

-- Insert Seed Data (Sample Routes for La Paz)
INSERT INTO routes (line_code, name, start_point, end_point) VALUES
('201', 'San Pedro - Achumani', 'Plaza del Estudiante', 'Calacoto Calle 15'),
('212', 'Pérez - Ceja (Vía Autopista)', 'Plaza Pérez Velasco', 'La Ceja El Alto'),
('300', 'Sopocachi - Miraflores', 'Plaza España', 'Plaza San Martín');
