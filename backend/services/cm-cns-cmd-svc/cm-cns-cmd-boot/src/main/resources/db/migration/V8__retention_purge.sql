-- A closed fiduciary's records are deleted when their retention has passed (ADR-064). The consent events and receipts are
-- append-only; a purge is the one deletion allowed, and says so for its own transaction with
-- set_config('cm.retention_purge', 'on', true). Updates, and deletions without that, are still refused.
create or replace function cns_cmd.append_only() returns trigger language plpgsql as $$
begin
    if tg_op = 'DELETE' and current_setting('cm.retention_purge', true) = 'on' then
        return old;
    end if;
    raise exception '% is append-only: rows are added, never changed or removed', tg_table_name;
end $$;
