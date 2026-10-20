-- A change that keeps a consent in force (ADR-026 §4) is history too: shortened, a category withdrawn, renewed.
-- Null for a change of status.
alter table cns_qry.cns_hst_rd_mdl
    add column chg varchar(80);
