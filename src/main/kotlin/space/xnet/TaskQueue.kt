package space.xnet

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ForkJoinPool

sealed class TaskAction <out T>
object NoTasksLeft : TaskAction<Nothing>()
class TaskToExecute <T> (val task: T) : TaskAction<T>()

sealed class TaskDescription <T>(val tasks: List<T>)

class Dependency <T> (val providerTasks: List<T>, val dependentTask: T) : TaskDescription<T>(providerTasks.plus(dependentTask))
class Task <T> (task: T) : TaskDescription<T>(listOf(task))

class ExpectDependencies <T> (val tasks: List<Task<T>>) {
    fun dependencies(vararg dependencies: Dependency<T>): TaskQueue<T> {
        return TaskQueue(tasks.plus(dependencies))
    }
}

fun <T> T.tasks(tasks: List<T>) = ExpectDependencies(tasks.map { Task(it) })
fun <T> T.tasks(vararg tasks: T) = this.tasks(tasks.toList())

fun <T> T.dependsOn(vararg providerTasks: T) = Dependency(providerTasks.toList(), this)
fun <T> T.task() = Task(this)

class TaskQueue <T> (taskDescriptions: List<TaskDescription<T>>) {

    private val allTasks = taskDescriptions.flatMap {
        it.tasks
    }.distinct()

    private val numTasks = allTasks.size

    private val unfinishedTasks = ConcurrentLinkedQueue(allTasks)

    private fun T.toIdx() = allTasks.indexOf(this)

    private val dependencies = taskDescriptions.filterIsInstance<Dependency<T>>()

    private val graph = array2dOfBoolean(numTasks, numTasks).apply {
        for (dependency in dependencies) {
            for (providerTask in dependency.providerTasks) {
                this[providerTask.toIdx()][dependency.dependentTask.toIdx()] = true
            }
        }
    }

    private fun tasksWithoutDependencies() = allTasks.filter { task ->
        val taskIdx = task.toIdx()
        (0 until numTasks).none { providerIdx ->
            graph[providerIdx][taskIdx]
        }
    }

    private val tasksWithoutDependencies = ArrayBlockingQueue<TaskAction<T>>(numTasks + 1).apply {
        this.addAll(tasksWithoutDependencies().map { TaskToExecute(it) })
    }

    private fun markCompleted(task: T) {
        val previousTasksWithoutDependencies = tasksWithoutDependencies()
        for (i in 0 until numTasks) {
            graph[task.toIdx()][i] = false
        }
        val newTasksWithoutDependencies = tasksWithoutDependencies().minus(previousTasksWithoutDependencies)
        for (taskWithoutDependency in newTasksWithoutDependencies) {
            tasksWithoutDependencies.add(TaskToExecute(taskWithoutDependency))
        }
        val newTasksDescription = if (newTasksWithoutDependencies.isEmpty())
            "none"
        else
            newTasksWithoutDependencies.joinToString(",")
        println("Task completed: $task. Tasks unlocked: $newTasksDescription")

        unfinishedTasks.remove(task)
    }

    fun runParallel(parallelism: Int, block: (T) -> Unit) {
        val pool = ForkJoinPool(parallelism)

        while (unfinishedTasks.isNotEmpty()) {
            when (val newTask = tasksWithoutDependencies.take()) {
                is TaskToExecute<T> -> {
                    pool.submit {
                        block(newTask.task)
                        markCompleted(newTask.task)
                        if (unfinishedTasks.isEmpty()) {
                            tasksWithoutDependencies.add(NoTasksLeft)
                        }
                    }
                }
                is NoTasksLeft -> {
                    // nothing to do.
                }
            }
        }
    }
}

fun <T> taskQueue (vararg taskDescriptions: TaskDescription<T>) = TaskQueue(taskDescriptions.toList())
fun <T> taskQueue (taskDescriptions: List<TaskDescription<T>>) = TaskQueue(taskDescriptions)
