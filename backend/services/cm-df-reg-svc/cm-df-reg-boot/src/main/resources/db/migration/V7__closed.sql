-- The time a fiduciary was closed after its wind-down (ADR-064).
alter table df_reg.df add column closed_at timestamptz;
