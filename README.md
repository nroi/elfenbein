# Elfenbein

Elfenbein helps you to keep all materialized views in a postgresql database up to date. In particular, it
* Refreshes your materialized views in the correct order, with the transitive dependencies of a materialized view being refreshed before the materialized view itself. For instance, if materialized view `B` depends on materialized view `A` (i.e., `B` reads data from `A`), then `A` will be refreshed before `B`.
* Gives you an overview of all your materialized views, all dependencies between them, the last time they have been refreshed and the estimated time required to refresh a materialized view with its transitive dependencies.

## Motivation
Lots of materialized views in a database, especially in data warehouses. Just refreshing all of them every 24 hours may be adequate
in some scenarios, but leaves room for improvement in other scenarios. Especially for materialized views which only take a view
minutes to refresh, you may want to refresh them more frequently.

## Ideas

* `REFRESH` all materialized views in topological order.
* Log start and completion time for refresh process to gain information and make more sensible decisions in the future.
* Allow to automatically adjust the refresh frequency for materialized views: the longer it takes to refresh them,
the lower the frequency.
* Web-GUI that allows users to make further adjustments, such as:
  * Define time slots where the database needs to run smoothly: no non-concurrent refreshs should happen during those timeslots.
  * Define thresholds: materialized views must not take more than x minutes to refresh. If they haven't successfully refreshed within this threshold, the refresh-process is aborted, an error is logged and a notification is sent.
  * set the refresh time and refresh thresholds for a specific materialized view manually.
 
 ## Unsolved problems so far
 
 * Even if a materialized view takes only a few minutes to refresh, you may not want to refresh it at a given time frame because non-concurrent refreshs can not happen concurrently with `SELECT`s on the same materialized view. Which means that your refresh process can annoy users who want to fetch data, create reports, etc. But not all materialized views can be refreshed `CONCURRENTLY`. Proposed solutions include:
   * Let the user define two time slots: production and maintenance. non-concurrent refresh happens only during maintenance.
   * During production, the refresh threshold is lower than during maintenance (meaning we could abort a refresh after just a few seconds).
   * During production, only non-concurrent refreshs may happen.

## Caveats
Please note that postgresql does not provide a method to query the last `REFRESH` time of a materialized view. Elfenbein will therefore always store the refresh times itself after it has refreshed a materialized view. Obviously, if you execute a `REFRESH` operation outside of elfenbein, then elfenbein has no way of knowing so and therefore show outdated refresh times.
