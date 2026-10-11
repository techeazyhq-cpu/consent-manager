-- Platform staff list and count a fiduciary's consent requests (CnsArtRdMdlRepoPort.pageByDfId, countByStsForDfId).
create index idx_cns_art_rd_mdl_df_id_created_at on cns_qry.cns_art_rd_mdl (df_id, created_at desc);
