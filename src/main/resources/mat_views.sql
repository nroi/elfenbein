select
ns.nspname as mat_view_schema,
relname as mat_view
from
pg_class pgc
inner join pg_namespace ns on
pgc.relnamespace = ns.oid
where
pgc.relkind = 'm'