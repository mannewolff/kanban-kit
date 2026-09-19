ALTER TABLE project ADD COLUMN dashboard_participation boolean NOT NULL DEFAULT false;

CREATE TABLE night_run_disruption_ack (
  night_run_id    bigint PRIMARY KEY REFERENCES night_run (id) ON DELETE CASCADE,
  acknowledged_by bigint REFERENCES app_user (id) ON DELETE SET NULL,
  acknowledged_at timestamptz(6) NOT NULL
);
