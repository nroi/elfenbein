package space.xnet

import java.io.File
import java.lang.IllegalArgumentException

data class PgPassEntry(val hostname: String,
                       val port: String,
                       val database: String,
                       val user: String,
                       val password: String) {

    fun toJdbcUrl(sslMode: String?): String {
        val suffix = if (sslMode != null) "?sslmode=$sslMode" else ""
        return "jdbc:postgresql://$hostname:$port/$database$suffix"
    }
}


data class MaterializedView(val schema: String, val name: String) {
    fun getStatement(): String {
        return "refresh materialized view concurrently $schema.$name"
    }

    fun getFallbackStatement(): String {
        return "refresh materialized view $schema.$name"
    }
}


fun parsePgPass(lines: List<String>): List<PgPassEntry> {

    fun fromLine(line: String): PgPassEntry {

        val entries = line.trim().split(":")
        if (entries.size != 5) {
            throw IllegalArgumentException()
        }
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


fun getPgPassEntryFromEnv(): PgPassEntry? {
    val host = System.getenv("PGHOST") ?: "localhost"
    val port = System.getenv("PGPORT") ?: "5432"
    val database = System.getenv("PGDATABASE") ?: "postgres"
    val user = System.getenv("PGUSER") ?: "postgres"
    val homeDir = System.getenv("HOME")!!

    val lines = File(homeDir, ".pgpass").readLines()

    return parsePgPass(lines).find { pgPassEntry ->
        val pgPassEntryFromEnv = PgPassEntry(host, port, database, user, pgPassEntry.password)
        pgPassEntry == pgPassEntryFromEnv
    }
}
