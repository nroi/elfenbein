import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import space.xnet.PgPassEntry
import space.xnet.parsePgPass
import java.lang.IllegalArgumentException
import kotlin.test.assertFailsWith


class PgPassTest : StringSpec({

    "pgpass parsing" {
        val testFileContent = "localhost:5432:mydatabase:myusername:mypassword"
        val entry = PgPassEntry(
            "localhost",
            "5432",
            "mydatabase",
            "myusername",
            "mypassword")
        parsePgPass(listOf(testFileContent)) shouldBe listOf(entry)
    }

    "parsing should not fail due to leading or trailing newlines" {
        val testFileContent1 = "localhost:5432:mydatabase:myusername:mypassword\n"
        val testFileContent2 = "localhost:5432:mydatabase:myusername:mypassword\n\n\n"
        val testFileContent3 = "\nlocalhost:5432:mydatabase:myusername:mypassword\n\n\n"
        val entry = PgPassEntry(
            "localhost",
            "5432",
            "mydatabase",
            "myusername",
            "mypassword")
        parsePgPass(listOf(testFileContent1)) shouldBe listOf(entry)
        parsePgPass(listOf(testFileContent2)) shouldBe listOf(entry)
        parsePgPass(listOf(testFileContent3)) shouldBe listOf(entry)
    }

    "parsing should fail for malformed entries" {
        assertFailsWith<IllegalArgumentException> {
            val testFileContent = "localhost:5432:mydatabase:myusername:mypassword:::"
            parsePgPass(listOf(testFileContent))
        }
    }

})