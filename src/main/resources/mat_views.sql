select ns.nspname                                                      as mat_view_schema,
       relname                                                         as mat_view,
       coalesce(s.priority, 100)                                       as priority,
       extract(epoch from coalesce(s.refresh_timeout, '3h'::interval)) as refresh_timeout
from pg_class pgc
         inner join pg_namespace ns on
    pgc.relnamespace = ns.oid
         left join elfenbein_settings s on s.schema = ns.nspname and s.mat_view = relname
where pgc.relkind = 'm'
