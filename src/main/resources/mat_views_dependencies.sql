select
    source_ns.nspname as source_schema,
    source_mat_view.relname as source_mat_view,
    destination_ns.nspname as dependent_schema,
    dependent_mat_view.relname as dependent_mat_view
from
    pg_depend
inner join pg_rewrite on
    pg_depend.objid = pg_rewrite.oid
inner join pg_class as dependent_mat_view on
    pg_rewrite.ev_class = dependent_mat_view.oid
inner join pg_class as source_mat_view on
    pg_depend.refobjid = source_mat_view.oid
inner join pg_namespace source_ns on
    source_ns.oid = source_mat_view.relnamespace
inner join pg_namespace destination_ns on
    destination_ns.oid = dependent_mat_view.relnamespace
inner join pg_attribute on
    pg_depend.refobjid = pg_attribute.attrelid
    and pg_depend.refobjsubid = pg_attribute.attnum
where
    dependent_mat_view.relkind = 'm'
    and source_mat_view.relkind = 'm'
    and pg_attribute.attnum > 0
group by source_mat_view, source_schema, dependent_mat_view, dependent_schema
order by source_mat_view, source_schema, dependent_mat_view, dependent_schema