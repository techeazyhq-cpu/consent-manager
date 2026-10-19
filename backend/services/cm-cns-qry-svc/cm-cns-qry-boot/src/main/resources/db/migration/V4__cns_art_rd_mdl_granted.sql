-- Partial consent (ADR-026): the categories a principal granted, which may be fewer than the request asked for.
-- Null until consent is given. Every consent given before this was for all that was asked.
alter table cns_qry.cns_art_rd_mdl
    add column granted_d_cats jsonb;

update cns_qry.cns_art_rd_mdl
set granted_d_cats = d_cats
where cns_art_id is not null;
