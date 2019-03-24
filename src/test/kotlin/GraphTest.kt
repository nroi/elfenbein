import io.kotlintest.matchers.numerics.shouldBeLessThan
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import space.xnet.Graph
import space.xnet.Node


class GraphTest : StringSpec({

    fun <T> permute(input: List<T>): List<List<T>> {
        if (input.size == 1) {
            return listOf(input)
        }
        val perms = mutableListOf<List<T>>()
        val toInsert = input[0]
        for (perm in permute(input.drop(1))) {
            for (i in perm.indices) {
                val newPerm = perm.toMutableList()
                newPerm.add(i, toInsert)
                perms.add(newPerm)
            }
        }

        return perms
    }


    "kahn should work" {
        val n1 = Node(setOf(), "n1")
        val n2 = Node(setOf(n1), "n2")
        val n3 = Node(setOf(n1), "n3")
        val n4 = Node(setOf(n2, n3), "n4")

        for (permuted in permute(listOf(n1, n2, n3, n4))) {
            val sorted = Graph(permuted).kahn()
            sorted.size shouldBe 4
            sorted.indexOf(n1) shouldBeLessThan sorted.indexOf(n2)
            sorted.indexOf(n1) shouldBeLessThan sorted.indexOf(n3)
            sorted.indexOf(n2) shouldBeLessThan sorted.indexOf(n4)
            sorted.indexOf(n3) shouldBeLessThan sorted.indexOf(n4)
        }
    }

    "kahn should work for graphs that are not fully connected" {
        val n0 = Node(setOf(), "n0")
        val n1 = Node(setOf(), "n1")
        val n2 = Node(setOf(n1), "n2")
        val n3 = Node(setOf(n1), "n3")
        val n4 = Node(setOf(n2, n3), "n4")
        for (permuted in permute(listOf(n0, n1, n2, n3, n4))) {
            val sorted = Graph(permuted).kahn()
            sorted.size shouldBe 5
            sorted.indexOf(n1) shouldBeLessThan sorted.indexOf(n2)
            sorted.indexOf(n1) shouldBeLessThan sorted.indexOf(n3)
            sorted.indexOf(n2) shouldBeLessThan sorted.indexOf(n4)
            sorted.indexOf(n3) shouldBeLessThan sorted.indexOf(n4)
        }
    }

    "test all getters" {
        val node = Node(setOf(), "a very nice name.").apply {
            payload shouldBe "a very nice name."
            incoming shouldBe setOf()
        }

        Graph(listOf(node)).apply {
            nodes shouldBe listOf(node)
        }

    }
})