package space.xnet

import java.sql.DriverManager


fun main(args: Array<String>) {

    val result = getPgPassEntryFromEnv()!!
    val sslMode = System.getenv("PGSSLMODE")
    connect(result.toJdbcUrl(sslMode), result.user, result.password)
}

fun connect(url: String, user: String, password: String) {

    val matViewsDependenciesQuery = Thread.currentThread().contextClassLoader
        .getResource("mat_views_dependencies.sql")
        .readText()

    val matViewsQuery = Thread.currentThread().contextClassLoader.getResource("mat_views.sql").readText()

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
        println("dependencies: " + kahn)
    }

}