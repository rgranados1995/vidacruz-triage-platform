-- The operation report (RF-05) selects the patients registered in a range of dates.
CREATE INDEX patient_registered_at_idx ON patient (registered_at);
