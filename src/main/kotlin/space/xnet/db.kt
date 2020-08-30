package space.xnet

import java.io.File

data class PgPassEntry(
    val hostname: String,
    val port: String,
    val database: String,
    val user: String,
    val password: String
) {

    fun toJdbcUrl(sslMode: String?): String {
        val applicationName = "elfenbein"
        val suffix = if (sslMode != null) {
            "?sslmode=$sslMode&ApplicationName=$applicationName"
        } else {
            "?ApplicationName=$applicationName"
        }
        return "jdbc:postgresql://$hostname:$port/$database$suffix"
    }
}


data class MaterializedView(val schema: String,
                            val name: String,
                            val refreshTimeoutSeconds: Int,
                            override val priority: Int) : Priority {

    fun getTimeoutStatement(): String =
        "set local statement_timeout to '${refreshTimeoutSeconds}s'"

    fun getRefreshStatement(): String =
        "refresh materialized view concurrently $schema.$name"

    fun getRefreshFallbackStatement(): String =
        "refresh materialized view $schema.$name"

    fun getDurationLogTableStatement(): String =
        "create table if not exists $schema.mat_view_refresh_times (" +
                "id bigserial," +
                "schema text not null," +
                "mat_view text not null," +
                "refresh_start timestamptz not null," +
                "refresh_end timestamptz not null," +
                "duration interval not null);"

    fun getDurationLogStatement(): String =
        "insert into $schema.mat_view_refresh_times (schema, mat_view, refresh_start, refresh_end, duration) " +
                "values  ('$schema', '$name', now(), clock_timestamp(), clock_timestamp() - now());"

    fun insertSettingsStatement(): String = """
        |insert into public.elfenbein_settings (schema, mat_view, priority, refresh_timeout, created_at)
        |values ('$schema', '$name', default, default, default)
        |on conflict do nothing;
    """.trimMargin()
}

fun getSettingsTableStatement(): String = """
        |create table if not exists public.elfenbein_settings (
        |   schema text not null,
        |   mat_view text not null,
        |   priority int4 not null default 100,
        |   refresh_timeout interval not null default '3 hours'::interval,
        |   created_at timestamptz not null default now(),
        |   primary key (schema, mat_view)
        |);
    """.trimMargin()

fun parsePgPass(lines: List<String>): List<PgPassEntry> {

    fun fromLine(line: String): PgPassEntry {

        val entries = line.trim().split(":")
        require(entries.size == 5)
        return PgPassEntry(
            entries[0],
            entries[1],
            entries[2],
            entries[3],
            entries[4]
        )
    }

    return lines.map { fromLine(it) }
}


fun getPgPassEntryFromEnv(): PgPassEntry {
    val host = System.getenv("PGHOST") ?: "localhost"
    val port = System.getenv("PGPORT") ?: "5432"
    val database = System.getenv("PGDATABASE") ?: "postgres"
    val user = System.getenv("PGUSER") ?: "postgres"

    return when (val password: String? = System.getenv("PGPASSWORD")) {
        null -> {
            val homeDir = System.getenv("HOME")!!
            val lines = File(homeDir, ".pgpass").readLines()

            parsePgPass(lines).find { pgPassEntry ->
                val pgPassEntryFromEnv = PgPassEntry(host, port, database, user, pgPassEntry.password)
                pgPassEntry == pgPassEntryFromEnv
            }!!
        }
        else -> PgPassEntry(host, port, database, user, password)
    }

}

