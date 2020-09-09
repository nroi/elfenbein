package space.xnet

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.SQLException

private const val NUM_THREADS = 1

private const val DEFAULT_PGSSLMODE = "prefer"

class Command: CliktCommand() {
    private val schema by option(metavar="SCHEMA", help="refresh only materialized views in the given schema")

    override fun run() {
        val result = getPgPassEntryFromEnv()
        val sslMode = System.getenv("PGSSLMODE") ?: DEFAULT_PGSSLMODE
        refresh(result.toJdbcUrl(sslMode), result.user, result.password, schema)
    }
}


fun main(args: Array<String>) {
    Command().main(args)
}

fun refresh(url: String, user: String, password: String, onlySchema: String?) {

    val matViewsDependenciesQuery = Thread.currentThread().contextClassLoader
        .getResource("mat_views_dependencies.sql")!!
        .readText()

    val matViewsQuery = Thread.currentThread().contextClassLoader
        .getResource("mat_views.sql")!!
        .readText()

    val views = mutableListOf<MaterializedView>()

    val connection = DriverManager.getConnection(url, user, password)
    val taskDescriptions = connection.use { usedConnection ->
        usedConnection.autoCommit = false
        fun insertSettings() {
            val settingsTableStatement = usedConnection.prepareStatement(getSettingsTableStatement())
            settingsTableStatement.execute()
            usedConnection.commit()
        }

        insertSettings()

        val matViewsStatement = usedConnection.prepareStatement(matViewsQuery)
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

        val dependenciesStatement = usedConnection.prepareStatement(matViewsDependenciesQuery)
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
                refreshTimeoutSeconds = sourceRefreshTimeout
            )
            val destination = MaterializedView(
                schema = destinationSchema,
                name = destinationName,
                refreshTimeoutSeconds = destinationRefreshTimeout,
                priority = destinationPriority
            )
            taskDescriptions.add(destination.dependsOn(source))
        }

        taskDescriptions
    }

    taskQueue<MaterializedView, Unit>(taskDescriptions).runParallel(NUM_THREADS) { matView ->
        val threadConnection = DriverManager.getConnection(url, user, password)
        threadConnection.use { usedConnection ->
            usedConnection.autoCommit = false
            if (onlySchema != null && matView.schema != onlySchema) {
                println("Skip materialized view ${matView.schema}.${matView.name}")
            } else {
                val statementString = matView.getRefreshStatement()
                val timeoutStatement = usedConnection.prepareStatement(matView.getTimeoutStatement())
                val initialRefreshStatement = usedConnection.prepareStatement(statementString)
                val logTableStatement = usedConnection.prepareStatement(matView.getDurationLogTableStatement())
                val durationLogStatement = usedConnection.prepareStatement(matView.getDurationLogStatement())
                val insertSettingsStatement = usedConnection.prepareStatement(matView.insertSettingsStatement())
                fun insertSettings() {
                    insertSettingsStatement.execute()
                    usedConnection.commit()
                }
                fun refreshAndLog(refreshStatement: PreparedStatement) {
                    timeoutStatement.execute()
                    refreshStatement.execute()
                    logTableStatement.execute()
                    durationLogStatement.execute()
                    usedConnection.commit()
                }
                println(statementString)
                try {
                    insertSettings()
                    refreshAndLog(initialRefreshStatement)
                } catch (e: SQLException) {
                    usedConnection.rollback()
                    if (e.message?.toLowerCase()?.contains("concurrently") == true) {
                        println(e.message)
                        val fallbackStatementString = matView.getRefreshFallbackStatement()
                        val fallbackRefreshStatement = usedConnection.prepareStatement(fallbackStatementString)
                        println("attempt to refresh non-concurrently")
                        println(fallbackStatementString)
                        try {
                            refreshAndLog(fallbackRefreshStatement)
                        } catch (e: SQLException) {
                            usedConnection.rollback()
                            e.printStackTrace()
                        }
                    } else {
                        usedConnection.rollback()
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}
