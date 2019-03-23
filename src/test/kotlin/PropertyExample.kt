import io.kotlintest.matchers.numerics.shouldBeLessThan
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import space.xnet.Graph
import space.xnet.Node


class PropertyExample : StringSpec({
    "kahn should work" {
        val n1 = Node(listOf(), "n1")
        val n2 = Node(listOf(n1), "n2")
        val n3 = Node(listOf(n1), "n3")
        val n4 = Node(listOf(n2, n3), "n4")
        val graph = Graph(listOf(n1, n2, n3, n4))
        val sorted = graph.kahn()
        sorted.size shouldBe 4
        sorted.indexOf(n1) shouldBeLessThan sorted.indexOf(n2)
        sorted.indexOf(n1) shouldBeLessThan sorted.indexOf(n3)
        sorted.indexOf(n2) shouldBeLessThan sorted.indexOf(n4)
        sorted.indexOf(n3) shouldBeLessThan sorted.indexOf(n4)
    }
})