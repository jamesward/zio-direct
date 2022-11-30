package zio.direct

import zio._
import zio.direct.core.util.debug.PrintMac
import java.sql.SQLException
import java.io.IOException
import zio.direct.core.metaprog.Collect
import zio.direct.core.metaprog.Verify
import javax.sql.DataSource
import zio.direct.Dsl.Params
import java.sql.Connection
import zio.Exit.{Failure, Success}
import zio.stream.ZStream
import zio.direct.examples.RunNow

object Example {

  // TODO zio-direct test with scope

  // def funC(): Unit = {
  //   val out =
  //     PrintMac.passthrough(defer {
  //       val (a, b) = (run(defer(1)), run(defer(2)))
  //       a + b
  //     })

  //   val outRun =
  //     zio.Unsafe.unsafe { implicit unsafe =>
  //       zio.Runtime.default.unsafe.run(out).getOrThrow()
  //     }
  //   println("====== RESULT: " + outRun)
  // }

  // def funD(): Unit = {
  //   val i = defer(1)
  //   val j = defer(2)

  //   PrintMac(defer {
  //       val v = run(i)
  //       v + run(j)
  //     })

  //   val out =
  //     defer {
  //       val v = run(i)
  //       v + run(j)
  //     }

  //   val outRun =
  //     zio.Unsafe.unsafe { implicit unsafe =>
  //       zio.Runtime.default.unsafe.run(out).getOrThrow()
  //     }
  //   println("====== RESULT: " + outRun)
  // }

  // def funE(): Unit = {
  //   val out =
  //     defer {
  //       def a(i: Int, s: String) = i + s.toInt
  //       run(defer(a(1, "2"))) + a(0, "1")
  //     }

  //   val outRun =
  //     zio.Unsafe.unsafe { implicit unsafe =>
  //       zio.Runtime.default.unsafe.run(out).getOrThrow()
  //     }
  //   println("====== RESULT: " + outRun)
  // }

  // def funF(): Unit = {
  //   val out = (defer {
  //     def a = defer("foo")
  //     run(a) + "bar"
  //   })

  //   val outRun =
  //     zio.Unsafe.unsafe { implicit unsafe =>
  //       zio.Runtime.default.unsafe.run(out).getOrThrow()
  //     }
  //   println("====== RESULT: " + outRun)
  // }

  // def funG(): Unit = {
  //   // PrintMac(defer {
  //   //   def blah(b: String) = run(defer(b))
  //   //   blah("foo") + "bar"
  //   // })

  //   val out = (defer {
  //     def blah(b: String)(c: String) = run(defer(b + c))
  //     blah("foo")("bar") + "baz"
  //   })

  //   val outRun =
  //     zio.Unsafe.unsafe { implicit unsafe =>
  //       zio.Runtime.default.unsafe.run(out).getOrThrow()
  //     }
  //   println("====== RESULT: " + outRun)
  // }
  //
  //

  // // // // // // // ..

  // def funH(): Unit = {

  //   // Correct issue but error is misleading
  //   // val out = (defer {
  //   //   if (run({
  //   //     for {
  //   //       env <- ZIO.service[Config]
  //   //       value <- ZIO.succeed(env.value)
  //   //     } yield (value)
  //   //   }) == "blah")
  //   //     "foo"
  //   //   else
  //   //     "barr"
  //   // })

  //   // val out =
  //   //   defer.info {
  //   //     val (x, y) = (run(ZIO.succeed("foo")), run(ZIO.succeed("bar")))
  //   //     val (x1, y1) = (run(ZIO.succeed("foo2" + x)), run(ZIO.succeed("bar2" + y)))
  //   //     x + x1 + y + y1
  //   //   }

  //   // Make a test for this
  //   // val out =
  //   //   defer.verbose {
  //   //     val (x, y) = (run(ZIO.succeed("foo")), run(ZIO.succeed("bar")))
  //   //     val config = run(ZIO.service[Config])
  //   //     x + config.value + y
  //   //   }

  //   // Noisy exception
  //   // val out =
  //   //   defer.info {
  //   //     val tup = (run(ZIO.succeed("foo")), run(ZIO.succeed("bar")))
  //   //     val configValue =
  //   //       run(ZIO.service[Config]) match {
  //   //         case Config(value) => value
  //   //       }
  //   //     tup._1 + config.value + tup._2
  //   //   }

  //   // val out =
  //   //   defer.info {
  //   //     val tup = (run(ZIO.succeed("foo")), run(ZIO.succeed("bar")))
  //   //     val configValue =
  //   //       run(ZIO.service[Config]) match {
  //   //         case Config(value) => run(ZIO.succeed(value))
  //   //       }
  //   //     tup._1 + configValue + tup._2
  //   //   }

  //   val out =
  //     defer {
  //       def a(i: Int, s: String) = run(defer(i)) + s.toInt
  //       a(1, "2") + run(defer(1))
  //     }

  //   val provided = out //.provide(ZLayer.succeed(Config("x")))

  //   val outRun =
  //     zio.Unsafe.unsafe { implicit unsafe =>
  //       zio.Runtime.default.unsafe.run(provided).getOrThrow()
  //     }
  //   println("====== RESULT: " + outRun)
  // }

  // def funH(): Unit = { //
  //   val out =
  //     defer.verbose {
  //       val msg =
  //         defer(
  //           try {
  //             //run(ZIO.attempt("foo"))
  //             run(ZIO.attempt { throw new IOException("blah") })
  //           } catch {
  //             case e: IOException => e.getMessage()
  //           } finally {
  //             println("============ Recovering ========")
  //           }
  //         )
  //       val msgResult = run(msg)

  //       run(ZIO.succeed(msgResult))
  //     }

  //   // Bug: moving currTime right before starTime makes a "forward reference"

  //   def currTime(): Double = java.lang.System.currentTimeMillis() //

  //   // val out =
  //   //   defer.verbose {
  //   //     val a = ZIO.sleep(10.seconds).fork.run
  //   //     val b = ZIO.sleep(2.seconds).fork.run
  //   //     lazy val startTime = currTime()
  //   //     ZIO.sleep(4.seconds)
  //   //     zio.Console.printLine(s"Started waiting: ${(currTime() - startTime)/1000d}").run
  //   //     val aResult = run(a.join)
  //   //     zio.Console.printLine(s"A completed: ${(currTime() - startTime)/1000d}").run
  //   //     val bResult = run(b.join)
  //   //     zio.Console.printLine(s"B completed: ${(currTime() - startTime)/1000d}").run
  //   //     (aResult, bResult)
  //   //   }

  //   // def currTime(): Double = java.lang.System.currentTimeMillis()
  //   // val out =
  //   //   defer.verbose {
  //   //     val startTime = currTime()
  //   //     val a = run(ZIO.collectAllPar(Chunk(ZIO.sleep(10.seconds), ZIO.sleep(2.seconds))))
  //   //     run(zio.Console.printLine(s"Completed in: ${(currTime() - startTime)/1000d}"))
  //   //     a
  //   //   }

  //   // val out =
  //   //   defer.info {
  //   //     (run(ZIO.sleep(5.seconds)), run(ZIO.sleep(10.seconds)))
  //   //   }

  //   val outRun =
  //     zio.Unsafe.unsafe { implicit unsafe =>
  //       zio.Runtime.default.unsafe.run(out).getOrThrow()
  //     }
  //   println("====== RESULT: " + outRun)
  // }

  // while ({ val v = run(i.get) - 2; println(v); v } > 0) { /

//   def funJ() = {

//     val out =
//       defer.verbose {
//         var i = 10
//         (run(ZIO.succeed({ i = i - 1 })), run(ZIO.succeed({ i = i - 2 })))
//       }

// // (TODO this should also give a warning)
// // emphasize "for effect systems"
// // lazy val supported?

//       // defer.verbose {
//       //   var i = run(ZIO.succeed(10))
//       //   while (run(ZIO.succeed(i - 2)) >= 0) {
//       //     println(s"Currently: $i")
//       //     run(ZIO.succeed { i = i -1 } )
//       //   }
//       // }
//       //
//       // defer.verbose {
//       //   var i = Ref.make(10).run
//       //   while (i.get.run - 2 >= 0) {
//       //     println(s"======= Currently: ${i.get.run} ===========")
//       //     i.getAndUpdate(i => i - 1).run
//       //   }
//       // }

//     val outRun =
//       zio.Unsafe.unsafe { implicit unsafe =>
//         zio.Runtime.default.unsafe.run(out).getOrThrow()
//       }
//     println("====== RESULT: " + outRun)
//   }

  // def funH() = { //
  //     val out =
  //     defer.verbose {
  //       val x = 123
  //       (run(ZIO.succeed(x)), {
  //         val a = run(ZIO.succeed(888))
  //         a
  //       }, run(ZIO.succeed(456)))
  //     }

  //     val outRun =
  //       zio.Unsafe.unsafe { implicit unsafe =>
  //         zio.Runtime.default.unsafe.run(out).getOrThrow()
  //       }
  //     println("====== RESULT: " + outRun)
  // }

  //   // val out2 =
  //   //   ZIO
  //   //     .succeed[Int](888)
  //   //     //.asInstanceOf[ZIO[_ >: Nothing <: Any, _ >: Nothing <: Any, Int]]
  //   //     .flatMap(((v: Int) => {
  //   //       val a: Int = v
  //   //       ZIO.attempt[Int](1./(0))
  //   //     }))
  //   //     .catchSome[Nothing & Any, Any, Any](
  //   //       (
  //   //           (tryLamParam: Any) =>
  //   //             tryLamParam match {
  //   //               case _ =>
  //   //                 ZIO.succeed[Int](999)
  //   //             }
  //   //       ).asInstanceOf[PartialFunction[Any, ZIO[Any, Throwable, Int]]]
  //   //     )

  //   println("====== RESULT: " + outRun) // //
  // }

  // class FooException extends Exception("foo")
  // def makeEx(): Throwable = new FooException()

  // def main(args: Array[String]): Unit = {
  //   val e = new Exception("blah") // // // //
  //   val out =
  //     defer { //
  //       try {
  //         throw e
  //       } catch {
  //         case `e` => 1
  //         // case _: Throwable => run(defer(2)) // .. ...
  //       } //
  //     }
  //   val outRun =
  //     zio.Unsafe.unsafe { implicit unsafe =>
  //       zio.Runtime.default.unsafe.run(out).getOrThrow()
  //     }
  //   println("====== RESULT: " + outRun)
  // }

  // def printPartialFuncExample(): Unit = {
  //   println("========================Partial Function Lambda=============================")
  //   PrintMac(stuff {
  //     case x: IOException => 123
  //     case y: IllegalArgumentException => 456
  //   })
  // }

  // //   PrintMac(stuff2(
  // //     x => x.length()
  // //   ))
  // // }

  // def stuff(input: PartialFunction[String, Int]): Option[Int] =
  //   input.lift("foo").map(_ + 1)

  // def stuff2(input: Function[String, Int]): Int =
  //   input.apply("foo")

  import ZIO._
  import zio.Console.printLine

  def main(args: Array[String]): Unit = {

    val arr = Array(3, 2, 8, 5, 7, 2, 3, 8, 9, 4, 5, 8, 2, 3, 4, 7, 6, 5, 9, 2, 3, 8, 4, 7, 5, 6, 2, 0, 8, 3)
    RunNow(quicksortDefer(arr))
    println(arr.toList)
// List(0, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 4, 4, 4, 5, 5, 5, 5, 6, 6, 7, 7, 7, 8, 8, 8, 8, 8, 9, 9)

  }

  def quicksortDefer(arr: Array[Int]): ZIO[Any, Nothing, Unit] = {
    def swap(i: Int, j: Int) =
      val temp = arr(i)
      arr(i) = arr(j)
      arr(j) = temp

    def sort(l: Int, r: Int): ZIO[Any, Nothing, Unit] = defer(Params(Verify.Lenient)) {
      val pivot = arr((l + r) / 2)
      val i = Ref.make(l).run
      val j = Ref.make(r).run
      while (i.get.run <= j.get.run)
        while (arr(i.get.run) < pivot) i.getAndUpdate(i => i + 1).run
        while (arr(j.get.run) > pivot) j.getAndUpdate(j => j - 1).run
        if (i.get.run <= j.get.run)
          swap(i.get.run, j.get.run)
          i.getAndUpdate(i => i + 1).run
          j.getAndUpdate(j => j - 1).run

      if (l < j.get.run)
        val jv = j.get.run
        sort(l, jv).run
      if (j.get.run < r)
        val iv = i.get.run
        sort(iv, r).run
    }
    sort(0, arr.length - 1)
  }

  def quicksortImperative(a: Array[Int]): Unit = {
    def swap(i: Int, j: Int): Unit = {
      val t = a(i)
      a(i) = a(j)
      a(j) = t
    }
    def sort(l: Int, r: Int): Unit = {
      val pivot = a((l + r) / 2)
      var i = l
      var j = r
      while (i <= j) {
        while (a(i) < pivot) i += 1
        while (a(j) > pivot) j -= 1
        if (i <= j) {
          swap(i, j)
          i += 1
          j -= 1
        }
      }
      if (l < j) sort(l, j)
      if (j < r) sort(i, r)
    }
    sort(0, a.length - 1)
  }

}