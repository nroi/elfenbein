# Elfenbein

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
