-- Retention (ADR-064): a legal hold stops the purge for a fiduciary, and the purge notes when it finished.
alter table df_reg.df add column legal_hold_reason varchar(500);
alter table df_reg.df add column legal_hold_at timestamptz;
alter table df_reg.df add column purged_at timestamptz;
