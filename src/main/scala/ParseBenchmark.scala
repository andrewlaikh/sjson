import org.scalameter.api._
import io.github.free.lock.sjson.JSON

object ParseBenchmark
  extends Bench.LocalTime {
  val sizes = Gen.range("size")(30000, 150000, 30000)
  val sb = new StringBuilder("\"")
  val addString = new StringBuilder("\\uD835\\uDC07\\u\\u\\a\\uDC\\uABC")
  for (i <- 1 to 10000) sb.append(addString)
  sb.append("\"")
  val input = sb.toString()
  val testParse = JSON.parse("\"\\uD835\\uDC07\\u\\u\\a\\uDC\\uABC\"")
  println(s"test parse: $testParse")

  val ranges = for {
    size <- sizes
  } yield 0 until size

  val jsonObject = JSON

  performance of "SJSON" in {
    measure method "Parse" in {
      using(ranges) in {
        r => jsonObject.parse(input);
      }
    }
  }
}