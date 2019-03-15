import io.kotlintest.matchers.haveLength
import io.kotlintest.properties.assertAll
import io.kotlintest.should
import io.kotlintest.specs.StringSpec

class PropertyExample: StringSpec() {
    init {
        "String size" {
            assertAll { a: String, b: String ->
                (a + b) should haveLength(a.length + b.length)
            }
        }
    }
}