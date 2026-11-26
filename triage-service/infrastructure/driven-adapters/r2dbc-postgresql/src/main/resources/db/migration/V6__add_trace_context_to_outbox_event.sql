-- W3C trace context of the request that produced the event, so its consumers continue the same trace. NULL when the
-- change ran outside a trace (an old row, or tracing disabled). The payload of the event does not change.
ALTER TABLE outbox_event ADD COLUMN traceparent VARCHAR(55);
