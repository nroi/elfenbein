package space.xnet

import java.sql.DriverManager
import java.sql.SQLException


fun main(args: Array<String>) {

    val result = getPgPassEntryFromEnv()!!
    val sslMode = System.getenv("PGSSLMODE")
    connect(result.toJdbcUrl(sslMode), result.user, result.password)
}

fun connect(url: String, user: String, password: String) {

    val matViewsDependenciesQuery = Thread.currentThread().contextClassLoader
        .getResource("mat_views_dependencies.sql")
        .readText()

    val matViewsQuery = Thread.currentThread().contextClassLoader
        .getResource("mat_views.sql")
        .readText()

    val views = mutableListOf<MaterializedView>()

    val connection = DriverManager.getConnection(url, user, password)
    connection.use { usedConnection ->
        val matViewsStatement = usedConnection.prepareStatement(matViewsQuery)
        val matViewsResultSet = matViewsStatement.executeQuery()
        while (matViewsResultSet.next()) {
            val schema = matViewsResultSet.getString(1)
            val name = matViewsResultSet.getString(2)
            views.add(MaterializedView(schema, name))
        }

        val graph = array2dOfBoolean(views.size, views.size)

        val dependenciesStatement = usedConnection.prepareStatement(matViewsDependenciesQuery)
        val dependenciesResultSet = dependenciesStatement.executeQuery()
        while (dependenciesResultSet.next()) {
            val sourceSchema = dependenciesResultSet.getString(1)
            val sourceName = dependenciesResultSet.getString(2)
            val destinationSchema = dependenciesResultSet.getString(3)
            val destinationName = dependenciesResultSet.getString(4)
            val source = MaterializedView(sourceSchema, sourceName)
            val destination = MaterializedView(destinationSchema, destinationName)
            val idxSource = views.indexOf(source)
            val idxDestination = views.indexOf(destination)
            graph[idxSource][idxDestination] = true
        }

        val kahn = kahnFromArray(graph, views)
        for (k in kahn) {
            val statementString = k.payload.getStatement()
            val refreshStatement = usedConnection.prepareStatement(statementString)
            println(statementString)
            try {
                refreshStatement.execute()
            } catch (e: SQLException) {
                if (e.message?.contains("cannot refresh materialized view") == true &&
                    e.message?.contains("concurrently") == true) {
                    println(e.message)
                    val fallbackStatementString = k.payload.getFallbackStatement()
                    val fallbackRefreshStatement = usedConnection.prepareStatement(fallbackStatementString)
                    println("attempt to refresh non-concurrently")
                    println(fallbackStatementString)
                    fallbackRefreshStatement.execute()
                } else {
                    throw e
                }
            }
        }
    }
}
