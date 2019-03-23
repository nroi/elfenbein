package space.xnet

import java.lang.IllegalArgumentException

data class PgPassEntry(val hostname: String,
                       val port: Int,
                       val database: String,
                       val username: String,
                       val password: String) {
}

fun parsePgPass(pgPass: String): List<PgPassEntry> {

    fun fromLine(line: String): PgPassEntry {

        val entries = line.split(":")
        if (entries.size != 5) {
            throw IllegalArgumentException()
        }
        return PgPassEntry(
            entries[0],
            Integer.valueOf(entries[1]),
            entries[2],
            entries[3],
            entries[4]
        )
    }

    return pgPass.trim().split("\n").map { fromLine(it) }
}
