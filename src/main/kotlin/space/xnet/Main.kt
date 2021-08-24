package space.xnet

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.SQLException

private const val NUM_THREADS = 1

private const val DEFAULT_PGSSLMODE = "prefer"

class Command: CliktCommand() {
    private val schema by option(metavar="SCHEMA", help="refresh only materialized views in the given schema")
    private val skipViews by option(metavar="SKIP").split(",")

    override fun run() {
        val result = getPgPassEntryFromEnv()
        val sslMode = System.getenv("PGSSLMODE") ?: DEFAULT_PGSSLMODE
        refresh(
            url = result.toJdbcUrl(sslMode),
            user = result.user,
            password = result.password,
            onlySchema = schema,
            skipViews = skipViews ?: emptyList()
        )
    }
}


fun main(args: Array<String>) {
    Command().main(args)
}

fun refresh(url: String, user: String, password: String, onlySchema: String?, skipViews: List<String>) {

    val matViewsDependenciesQuery = Thread.currentThread().contextClassLoader
        .getResource("mat_views_dependencies.sql")!!
        .readText()

    val matViewsQuery = Thread.currentThread().contextClassLoader
        .getResource("mat_views.sql")!!
        .readText()

    val views = mutableListOf<MaterializedView>()

    val connection = DriverManager.getConnection(url, user, password)
    val taskDescriptions = run {
        connection.autoCommit = false
        fun insertSettings() {
            val settingsTableStatement = connection.prepareStatement(getSettingsTableStatement())
            settingsTableStatement.execute()
            connection.commit()
        }

        insertSettings()

        val matViewsStatement = connection.prepareStatement(matViewsQuery)
        val matViewsResultSet = matViewsStatement.executeQuery()

        while (matViewsResultSet.next()) {
            val schema = matViewsResultSet.getString(1)
            val name = matViewsResultSet.getString(2)
            val priority = matViewsResultSet.getInt(3)
            val refreshTimeoutSeconds = matViewsResultSet.getInt(4)
            val materializedView = MaterializedView(
                schema = schema,
                name = name,
                priority = priority,
                refreshTimeoutSeconds = refreshTimeoutSeconds
            )
            views.add(materializedView)
        }

        if (views.isEmpty()) {
            println("No materialized views found - nothing to do.")
            return
        }

        val dependenciesStatement = connection.prepareStatement(matViewsDependenciesQuery)
        val dependenciesResultSet = dependenciesStatement.executeQuery()

        val taskDescriptions: MutableList<TaskDescription<MaterializedView>> =
            views.map { it.task() }.toMutableList()

        while (dependenciesResultSet.next()) {
            val sourceSchema = dependenciesResultSet.getString(1)
            val sourceName = dependenciesResultSet.getString(2)
            val sourcePriority = dependenciesResultSet.getInt(3)
            val sourceRefreshTimeout = dependenciesResultSet.getInt(4)
            val destinationSchema = dependenciesResultSet.getString(5)
            val destinationName = dependenciesResultSet.getString(6)
            val destinationPriority = dependenciesResultSet.getInt(7)
            val destinationRefreshTimeout = dependenciesResultSet.getInt(8)
            val source = MaterializedView(
                schema = sourceSchema,
                name = sourceName,
                priority = sourcePriority,
                refreshTimeoutSeconds = sourceRefreshTimeout,
            )
            val destination = MaterializedView(
                schema = destinationSchema,
                name = destinationName,
                refreshTimeoutSeconds = destinationRefreshTimeout,
                priority = destinationPriority,
            )
            taskDescriptions.add(destination.dependsOn(source))
        }

        taskDescriptions
    }

    taskQueue<MaterializedView, Unit>(taskDescriptions).runParallel(NUM_THREADS) { matView ->
        val threadConnection = DriverManager.getConnection(url, user, password)
        threadConnection.autoCommit = false
        val skipSchema = onlySchema != null && matView.schema != onlySchema
        val skipView = skipViews.any { it.equals(matView.name, ignoreCase = true) }
        if (skipSchema || skipView) {
            println("Skip materialized view ${matView.schema}.${matView.name}")
        } else {
            val statementString = matView.getRefreshStatement()
            val timeoutStatement = threadConnection.prepareStatement(matView.getTimeoutStatement())
            val initialRefreshStatement = threadConnection.prepareStatement(statementString)
            val logTableStatement = threadConnection.prepareStatement(matView.getDurationLogTableStatement())
            val durationLogStatement = threadConnection.prepareStatement(matView.getDurationLogStatement())
            val insertSettingsStatement = threadConnection.prepareStatement(matView.insertSettingsStatement())
            fun insertSettings() {
                insertSettingsStatement.execute()
                threadConnection.commit()
            }
            fun refreshAndLog(refreshStatement: PreparedStatement) {
                timeoutStatement.execute()
                refreshStatement.execute()
                logTableStatement.execute()
                durationLogStatement.execute()
                threadConnection.commit()
            }
            println(statementString)
            try {
                insertSettings()
                refreshAndLog(initialRefreshStatement)
            } catch (e: SQLException) {
                threadConnection.rollback()
                if (e.message?.lowercase()?.contains("concurrently") == true) {
                    println(e.message)
                    val fallbackStatementString = matView.getRefreshFallbackStatement()
                    val fallbackRefreshStatement = threadConnection.prepareStatement(fallbackStatementString)
                    println("attempt to refresh non-concurrently")
                    println(fallbackStatementString)
                    try {
                        refreshAndLog(fallbackRefreshStatement)
                    } catch (e: SQLException) {
                        threadConnection.rollback()
                        e.printStackTrace()
                    }
                } else {
                    threadConnection.rollback()
                    e.printStackTrace()
                }
            }
        }
    }
}
