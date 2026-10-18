-- The notice the fiduciary wrote, shown to the person beside each consent so they can read what they're agreeing to
-- (DPDPA s.5, GDPR art. 7). Rows projected before this have none; the person still sees the purpose and data.
alter table cns_qry.cns_art_rd_mdl
    add column ntc_title varchar(255),
    add column ntc_text  text;
