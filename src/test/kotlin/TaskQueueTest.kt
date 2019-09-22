import io.kotlintest.specs.StringSpec
import space.xnet.*
import kotlin.random.Random

private class MyTask(val number: Int) {
    override fun toString() = "MyTask($number)"
}

class TaskQueueTest : StringSpec({

    "dependencies" {

        val task1 = MyTask(1)
        val task2 = MyTask(2)
        val task3 = MyTask(3)
        val task4 = MyTask(4)
        val task5 = MyTask(5)
        val task6 = MyTask(6)
        val task7 = MyTask(7)
        val task8 = MyTask(8)
        val task9 = MyTask(9)
        val task10 = MyTask(10)

        val taskQueue = tasks(
            task1,
            task2,
            task3,
            task4,
            task5,
            task6,
            task7,
            task8,
            task9,
            task10
        ).dependencies(
            task5.dependsOn(task1, task2),
            task6.dependsOn(task2, task4),
            task7.dependsOn(task1, task5),
            task8.dependsOn(task5),
            task9.dependsOn(task8, task3, task6)
        )

        taskQueue.runParallel(2) { task ->
            println("Hello from $task")
            Thread.sleep(Random.nextLong(2000, 8000))
            println("Goodbye from $task")
        }
    }
})