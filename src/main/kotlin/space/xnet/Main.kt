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
        val statement = usedConnection.prepareStatement(matViewsQuery)
        val resultSet = statement.executeQuery()
        while (resultSet.next()) {
            val schema = resultSet.getString(1)
            val name = resultSet.getString(2)
            views.add(MaterializedView(schema, name))
        }
    }

    val graph = array2dOfBoolean(views.size, views.size)

    connection.use { usedConnection ->
        val statement = usedConnection.prepareStatement(matViewsDependenciesQuery)
        val resultSet = statement.executeQuery()
        while (resultSet.next()) {
            val sourceSchema = resultSet.getString(1)
            val sourceName = resultSet.getString(2)
            val destinationSchema = resultSet.getString(3)
            val destinationName = resultSet.getString(4)
            val source = MaterializedView(sourceSchema, sourceName)
            val destination = MaterializedView(destinationSchema, destinationName)
            val idxSource = views.indexOf(source)
            val idxDestination = views.indexOf(destination)
            graph[idxSource][idxDestination] = true
        }
    }


    println("views: " + views)
}