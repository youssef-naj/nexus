-- The dashboard's recent activity: one organization's request events, newest first
CREATE INDEX ix_request_events_org_time ON request_events (organization_id, occurred_at DESC);
