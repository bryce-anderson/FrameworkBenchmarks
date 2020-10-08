
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.twitter.finagle.{Http, Service, SimpleFilter}
import com.twitter.finagle.stats.{FileAggregatorStatsReceiver, LoadedStatsReceiver}
import com.twitter.finagle.http.{HttpMuxer, Request, Response}
import com.twitter.util.{Await, Duration, Future}
import com.twitter.io.Buf
import java.io.File

object Main extends App {

  val mapper: ObjectMapper = new ObjectMapper().registerModule(DefaultScalaModule)

  val starString: String = "*" * 1024
//  val helloWorld: Buf = Buf.Utf8("Hello, World!")
  val helloWorld: Buf = Buf.Utf8(starString)

  val muxer: HttpMuxer = new HttpMuxer()
    .withHandler("/json", Service.mk { _: Request =>
      val rep = Response()
      rep.content = Buf.ByteArray.Owned(mapper.writeValueAsBytes(Map("message" -> starString)))
      rep.headerMap.setUnsafe("Content-Type", "application/json")

      Future.value(rep)
    })
    .withHandler("/plaintext", Service.mk { _: Request =>
      val rep = Response()
      rep.content = helloWorld
      rep.headerMap.setUnsafe("Content-Type", "text/plain")

      Future.value(rep)
    })

  val serverAndDate: SimpleFilter[Request, Response] =
    new SimpleFilter[Request, Response] with (Response => Response) {

    def apply(rep: Response): Response = {
      rep.headerMap.setUnsafe("Server", "Finagle")
      rep.headerMap.setUnsafe("Date", currentTime())

      rep
    }

    def apply(req: Request, s: Service[Request, Response]): Future[Response] =
      s(req).map(this)
  }

  if (System.getenv("NULL_STATS") == null) {
    val fileStatsReceiver = new FileAggregatorStatsReceiver(
      new File("concat-metrics.json"), Duration.fromSeconds(10))
    LoadedStatsReceiver.self = fileStatsReceiver
  }

  var server = Http.server

  if (System.getenv("NO_HTTP_STATS") == null) {
    server = server.withHttpStats
    println("Using Http stats")
  } else {
    println("Not using Http stats")
  }

  val usedStats = server.params[com.twitter.finagle.param.Stats]
  println("Using stats receiver " + usedStats)

  Await.ready(server
    .withCompressionLevel(0)
    .serve(":1205", serverAndDate.andThen(muxer))
  )
}
