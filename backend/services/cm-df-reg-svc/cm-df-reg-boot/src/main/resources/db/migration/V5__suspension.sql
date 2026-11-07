-- A fiduciary stopped by platform staff keeps its rows and says why (ADR-064).
alter table df_reg.df add column suspended_at timestamptz;
alter table df_reg.df add column suspension_reason varchar(500);
