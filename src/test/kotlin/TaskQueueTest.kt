import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import space.xnet.Priority
import space.xnet.dependsOn
import space.xnet.tasks
import kotlin.random.Random

private class MyTask(val number: Int, override val priority: Int = 0) : Priority {
    override fun toString() = "MyTask($number, $priority)"
}

class TaskQueueTest : StringSpec({

    "live test" {

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

        val taskQueue = tasks<MyTask, Unit>(
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

    "the priority is considered" {

        val task1 = MyTask(1, priority = 4)
        val task2 = MyTask(2, priority = 0)
        val task3 = MyTask(3, priority = 3)
        val task4 = MyTask(4, priority = 0)
        val task5 = MyTask(5, priority = 1)
        val task6 = MyTask(6, priority = 5)

        val taskQueue = tasks<MyTask, MyTask>(
            task1,
            task2,
            task3,
            task4,
            task5,
            task6
        ).dependencies(
            task4.dependsOn(task2, task3),
            task5.dependsOn(task2, task3),
            task6.dependsOn(task2, task3)
        )

        val taskOrderSequential = taskQueue.runSequential { it }
        val expectedOrder = listOf(
            task2,
            task3,
            task4,
            task5,
            task1,
            task6
        )
        taskOrderSequential shouldBe expectedOrder
    }
})
