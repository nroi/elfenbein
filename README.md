# Elfenbein

Elfenbein helps you to keep all materialized views in a PostgreSQL database up to date. In particular, it
refreshes your materialized views in the correct order, with the transitive dependencies of a materialized view being refreshed before the materialized view itself. For instance, if materialized view `B` depends on materialized view `A`, then `A` will be refreshed before `B`.


## Motivation
You may find elfenbein useful if you have lots of materialized views in a database, especially in a data warehouse. Just refreshing all of them in any arbitrary order every 24 hours may be adequate in some scenarios, but leaves room for improvement in other scenarios.

## Currently implemented features
* Detect dependencies between materialized views in order to do a `REFRESH` on all materialized views in topological order.
* Define a priorities for materialized views so that a given materialized view can be refreshed before another materialized view (if the dependencies allow doing so).
* Parallelization: You can choose to refresh multiple materialized views at the same time. You may find this useful if refreshing a
materialized view sometimes takes a very long time, e.g. due to locks, while other materialized views are not prone to locks and can be
refreshed very quickly. In those cases, parallelization can avoid that materialized views that can be refreshed quickly won't have too wait
too  long for other refreshs.
* Logs start and completion time of each materialized view.

## Ideas / Future work

* Start and completion time is already logged in the database. This information could be used to make more sensible decisions: For instance, if the dependency graph
allows more than one materialized view to be refreshed next, we could choose the materialized view that finishes most quickly.
* Allow to automatically adjust the refresh frequency for materialized views: the longer it takes to refresh them,
the lower the frequency.
* Allow users to make further adjustments, such as:
  * Define time slots where the database needs to run smoothly: no non-concurrent refreshs should happen during those timeslots.
  * Define thresholds: materialized views must not take more than x minutes to refresh. If they haven't successfully refreshed within this threshold, the refresh-process is aborted, an error is logged.
  * set the refresh time and refresh thresholds for a specific materialized view manually.
  * Users should be able to make those adjustments by simply updating tables. After all, elfenbein-users are most likely SQL-savvy 

 ## Unsolved problems so far

 * Even if a materialized view takes only a few minutes to refresh, you may not want to refresh it at a given time frame because non-concurrent refreshs cannot happen concurrently with `SELECT`s on the same materialized view. Which means that your refresh process can annoy users who want to fetch data, create reports, etc. But not all materialized views can be refreshed `CONCURRENTLY`. Proposed solutions include:
   * Let the user define two time slots: production and maintenance. non-concurrent refresh happens only during maintenance.
   * During production, the refresh threshold is lower than during maintenance (meaning we could abort a refresh after just a few seconds).
   * During production, only non-concurrent refreshs may happen.

## Caveats
Please note that PostgreSQL does not provide a method to query the last `REFRESH` time of a materialized view. Elfenbein will therefore always store the refresh times itself after it has refreshed a materialized view. Obviously, if you execute a `REFRESH` operation outside of elfenbein, then elfenbein has no way of knowing so and therefore shows outdated refresh times.

## Build

Elfenbein is written in Kotlin, a JVM language, and can be built with gradle. Run:
```bash
./gradlew build
```
to build a jar file.

## Usage

Notice that elfenbein creates new tables in your database. In particular, the table `mat_view_refresh_times` is used to log the duration
of refreshing the materialized views, and `elfenbein_settings` is used to associate materialized views with a `priority` value so that
materialized views with a lower value are prioritized before those with a larger value.

Elfenbein uses the same environment variables used by `libpq`, in particular: `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD` and `PGSSLMODE`.
If you prefer not to set `PGPASSWORD` due to security reasons, you can also use a [.pgpass](https://www.postgresql.org/docs/12/libpq-pgpass.html) file.

Set the environment variables and start elfenbein, for example:

```bash
PGHOST=localhost PGPORT=5432 PGDATABASE=postgres PGUSER=postgres PGPASSWORD=postgres PGSSLMODE=disable java -jar build/libs/elfenbein-1.0-SNAPSHOT.jar
```

Elfenbein will then immediately start to refresh the materialized views in your database.
