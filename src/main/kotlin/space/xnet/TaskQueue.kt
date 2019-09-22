package space.xnet

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ForkJoinPool

private const val NUM_THREADS = 2

interface Task {
    operator fun invoke()
}

sealed class TaskAction
object NoTasksLeft : TaskAction()
class TaskToExecute <T: Task> (val task: T) : TaskAction()

sealed class TaskDescription <T: Task>(val tasks: List<T>)

class Dependency <T: Task> (val providerTasks: List<T>, val dependentTask: T) : TaskDescription<T>(providerTasks.plus(dependentTask))
class IndependentTask <T: Task> (task: T) : TaskDescription<T>(listOf(task))

fun <T: Task> T.dependsOn(vararg providerTasks: T) = Dependency(providerTasks.toList(), this)
fun <T: Task> T.independent() = IndependentTask(this)


class TaskQueue <T: Task> (taskDescriptions: List<TaskDescription<T>>) {

    private val allTasks = taskDescriptions.flatMap {
        it.tasks
    }.distinct()

    private val numTasks = allTasks.size

    private val unfinishedTasks = ConcurrentLinkedQueue(allTasks)

    private fun Task.toIdx() = allTasks.indexOf(this)

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

    private val tasksWithoutDependencies = ArrayBlockingQueue<TaskAction>(numTasks + 1).apply {
        this.addAll(tasksWithoutDependencies().map { TaskToExecute(it) })
    }

    private fun markCompleted(task: Task) {
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

    fun runAll() {
        val pool = ForkJoinPool(NUM_THREADS)

        while (unfinishedTasks.isNotEmpty()) {
            when (val newTask = tasksWithoutDependencies.take()) {
                is TaskToExecute<*> -> {
                    pool.submit {
                        newTask.task()
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

fun <T: Task> taskQueue (vararg taskDescriptions: TaskDescription<T>) = TaskQueue(taskDescriptions.toList())
