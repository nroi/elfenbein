select source_ns.nspname          as source_schema,
       source_mat_view.relname    as source_mat_view,
       coalesce(s1.priority, 100) as source_priority,
       destination_ns.nspname     as dependent_schema,
       dependent_mat_view.relname as dependent_mat_view,
       coalesce(s2.priority, 100) as dependent_priority
from pg_depend
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
         left join elfenbein_settings s1 on
        s1.schema = source_ns.nspname
        and s1.mat_view = source_mat_view.relname
         left join elfenbein_settings s2 on
        s2.schema = destination_ns.nspname
        and s2.mat_view = dependent_mat_view.relname
where dependent_mat_view.relkind = 'm'
  and source_mat_view.relkind = 'm'
  and pg_attribute.attnum > 0
group by source_mat_view, source_schema, source_priority, dependent_mat_view, dependent_schema, dependent_priority
order by source_mat_view, source_schema, source_priority, dependent_mat_view, dependent_schema, dependent_priority
