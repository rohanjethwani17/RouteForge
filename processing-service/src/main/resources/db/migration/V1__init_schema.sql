-- RouteForge Database Schema
-- Initial schema for vehicle position tracking

-- Vehicle Positions History Table
CREATE TABLE IF NOT EXISTS vehicle_positions_history (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL UNIQUE,
    vehicle_id VARCHAR(50) NOT NULL,
    route_id VARCHAR(50) NOT NULL,
    lat DOUBLE PRECISION NOT NULL,
    lon DOUBLE PRECISION NOT NULL,
    speed_kph DOUBLE PRECISION,
    heading_deg DOUBLE PRECISION,
    ts_epoch_ms BIGINT NOT NULL,
    stop_id VARCHAR(50),
    delay_sec INTEGER,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for efficient querying
CREATE INDEX idx_vehicle_ts ON vehicle_positions_history(vehicle_id, ts_epoch_ms DESC);
CREATE INDEX idx_route_ts ON vehicle_positions_history(route_id, ts_epoch_ms DESC);
CREATE INDEX idx_recorded_at ON vehicle_positions_history(recorded_at DESC);

-- Optional: Routes table for reference data
CREATE TABLE IF NOT EXISTS routes (
    route_id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(200),
    agency VARCHAR(100),
    route_type VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Optional: Stops table for reference data
CREATE TABLE IF NOT EXISTS stops (
    stop_id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(200),
    lat DOUBLE PRECISION,
    lon DOUBLE PRECISION,
    route_id VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (route_id) REFERENCES routes(route_id) ON DELETE SET NULL
);

-- Comments for documentation
COMMENT ON TABLE vehicle_positions_history IS 'Historical vehicle position data from GTFS-RT feeds';
COMMENT ON COLUMN vehicle_positions_history.event_id IS 'Unique event identifier for idempotency';
COMMENT ON COLUMN vehicle_positions_history.ts_epoch_ms IS 'Position timestamp in epoch milliseconds';
COMMENT ON COLUMN vehicle_positions_history.recorded_at IS 'When the position was recorded in our system';
