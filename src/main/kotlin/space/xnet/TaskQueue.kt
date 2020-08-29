package space.xnet

import java.util.concurrent.*


interface Priority {
    // Choose lower priority values for important tasks, larger values for less important tasks.
    val priority: Int
}

sealed class TaskAction <out T: Priority> : Priority
object NoTasksLeft : TaskAction<Nothing>() {
    override val priority: Int = Int.MAX_VALUE
}

class TaskToExecute <T: Priority> (val task: T) : TaskAction<T>() {
    override val priority: Int = task.priority
    override fun toString(): String = "TaskToExecute($task)"
}

sealed class TaskDescription <T: Priority>(val tasks: List<T>)

class Dependency <T: Priority> (val providerTasks: List<T>, val dependentTask: T) :
    TaskDescription<T>(providerTasks.plus(dependentTask))
class Task <T: Priority> (task: T) : TaskDescription<T>(listOf(task))

class ExpectDependencies <T: Priority, U> (private val tasks: List<Task<T>>) {
    fun dependencies(vararg dependencies: Dependency<T>): TaskQueue<T, U> = TaskQueue(tasks.plus(dependencies))
}

fun <T: Priority, U> tasksFromList(tasks: List<T>) = ExpectDependencies<T, U>(tasks.map { Task(it) })
fun <T: Priority, U> tasks(vararg tasks: T) = tasksFromList<T, U>(tasks.toList())

fun <T: Priority> T.dependsOn(vararg providerTasks: T) = Dependency(providerTasks.toList(), this)
fun <T: Priority> T.task() = Task(this)

class TaskQueue <T: Priority, U> (taskDescriptions: List<TaskDescription<T>>) {

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

    private fun tasksWithoutDependencies(): List<T> = allTasks.filter { task ->
        val taskIdx = task.toIdx()
        (0 until numTasks).none { providerIdx ->
            graph[providerIdx][taskIdx]
        }
    }

    private val tasksWithoutDependencies = run {
        val priorityQueue = PriorityBlockingQueue<TaskAction<T>>(numTasks + 1) { a: TaskAction<T>, b: TaskAction<T> ->
            a.priority.compareTo(b.priority)
        }

        priorityQueue.apply {
            this.addAll(tasksWithoutDependencies().map { TaskToExecute(it) })
        }
    }

    private fun markCompleted(task: T) = synchronized(this) {
        val previousTasksWithoutDependencies = tasksWithoutDependencies()
        for (i in 0 until numTasks) {
            graph[task.toIdx()][i] = false
        }
        val newTasksWithoutDependencies = tasksWithoutDependencies().minus(previousTasksWithoutDependencies)
        for (taskWithoutDependency in newTasksWithoutDependencies) {
            tasksWithoutDependencies.add(TaskToExecute(taskWithoutDependency))
        }
        val newTasksDescription = if (newTasksWithoutDependencies.isEmpty()) {
            "none"
        } else {
            newTasksWithoutDependencies.joinToString(",")
        }
        println("Task completed: $task. Tasks unlocked: $newTasksDescription")

        unfinishedTasks.remove(task)
    }

    interface Submit {
        fun submit(block: () -> Unit)
        fun waitUntilFree()
    }

    class MultiThreaded(parallelism: Int): Submit {
        private val pool = ForkJoinPool(parallelism)
        private val semaphore = Semaphore(parallelism)

        override fun submit(block: () -> Unit) {
            semaphore.acquire()
            pool.submit {
                block()
                semaphore.release()
            }
        }

        override fun waitUntilFree() {
            semaphore.acquire()
            semaphore.release()
        }
    }

    class SingleThreaded : Submit {
        override fun submit(block: () -> Unit) = block()
        override fun waitUntilFree() = Unit
    }

    fun runSequential(block: (T) -> U): List<U> = runParallel(parallelism = 1, block = block)

    fun runParallel(parallelism: Int, block: (T) -> U): List<U> {

        val results = ConcurrentLinkedQueue<U>()
        val threadPool = when (parallelism) {
            1 -> SingleThreaded()
            else -> MultiThreaded(parallelism)
        }

        while (unfinishedTasks.isNotEmpty()) {
            when (val newTask = tasksWithoutDependencies.take()) {
                is TaskToExecute<T> -> {
                    threadPool.submit {
                        val result = block(newTask.task)
                        results.add(result)
                        markCompleted(newTask.task)
                        if (unfinishedTasks.isEmpty()) {
                            tasksWithoutDependencies.add(NoTasksLeft)
                        }
                    }
                }
                is NoTasksLeft -> {
                    // Nothing to do.
                }
            }
            // Waiting for some threads to finish is required so that priorities are considered when scheduling the
            // threads. For instance, consider the following case: We have 101 tasks (task1, task2, … task101).
            // All tasks have priority 10, except for task 101, which has priority 0. There is only one dependency:
            // task101 depends on task 1. If we were to put tasks1 until task100 onto the thread pool, than we would
            // have to wait for the first 100 tasks to finish, although task101 should be prioritized before those
            // tasks.
            // Generally speaking, when parallelism factor is set to n, we don't want to submit more than n threads,
            // so that high priority tasks that are dependent on other tasks get the chance to be submitted before
            // low priority tasks with no dependencies.
            threadPool.waitUntilFree()
        }

        return results.toList()
    }
}

fun <T: Priority, U> taskQueue (taskDescriptions: List<TaskDescription<T>>) = TaskQueue<T, U>(taskDescriptions)
