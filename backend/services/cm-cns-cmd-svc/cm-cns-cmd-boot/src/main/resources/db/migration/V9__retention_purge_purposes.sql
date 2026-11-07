-- The retention purge (V8) also removes a closed fiduciary's purposes once no consent request is left. A published
-- purpose version is otherwise immutable; the purge says so for its own transaction, and everything else is still refused.
create or replace function cns_cmd.prps_ver_immutable() returns trigger language plpgsql as $$
begin
    if old.sts = 'PUBLISHED' then
        if tg_op = 'DELETE' and current_setting('cm.retention_purge', true) = 'on' then
            return old;
        end if;
        raise exception 'purpose version % is published and can''t be changed', old.id;
    end if;
    return case when tg_op = 'DELETE' then old else new end;
end $$;
