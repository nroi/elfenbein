package space.xnet

import java.sql.DriverManager


fun main(args: Array<String>) {

    val result = getPgPassEntryFromEnv()!!
    val sslMode = System.getenv("PGSSLMODE")
    connect(result.toJdbcUrl(sslMode), result.user, result.password)
}

fun connect(url: String, user: String, password: String) {

    val connection = DriverManager.getConnection(url, user, password)
    connection.use { usedConnection ->
        val statement = usedConnection.prepareStatement("select 3")
        val resultSet = statement.executeQuery()
        while (resultSet.next()) {
            val result = resultSet.getObject(1)
            println("result: $result")
        }
    }
}