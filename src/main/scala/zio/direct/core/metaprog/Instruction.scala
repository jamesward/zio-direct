package zio.direct.core.metaprog

import scala.quoted._
import zio.direct.core.util.Format
import zio.direct.Dsl.Params

case class Instructions(info: InfoBehavior, collect: Collect, verify: Verify) {
  // For debugging purposes, check if there is any visibility setting enabled
  // to know whether to print various ad-hoc things.
  def anyVis = info != InfoBehavior.Silent
}

sealed trait InfoBehavior {
  def showComputedType: Boolean
  def showComputedTypeDetail: Boolean
  def showDeconstructed: Boolean
  def showReconstructed: Boolean
  def showReconstructedTree: Boolean
}
object InfoBehavior {
  case object Silent extends InfoBehavior {
    val showComputedType = false
    val showComputedTypeDetail = false
    val showDeconstructed = false
    val showReconstructed = false
    val showReconstructedTree = false
  }
  case object Info extends InfoBehavior {
    val showComputedType = true
    val showComputedTypeDetail = false
    val showDeconstructed = false
    val showReconstructed = true
    val showReconstructedTree = false
  }
  case object Verbose extends InfoBehavior {
    val showComputedType = true
    val showComputedTypeDetail = true
    val showDeconstructed = true
    val showReconstructed = true
    val showReconstructedTree = false
  }
  case object VerboseTree extends InfoBehavior {
    val showComputedType = true
    val showComputedTypeDetail = true
    val showDeconstructed = true
    val showReconstructed = true
    val showReconstructedTree = true
  }
}

sealed trait Verify
object Verify {
  case object Strict extends Verify
  case object Lenient extends Verify
  case object None extends Verify
}

sealed trait Collect
object Collect {
  case object Sequence extends Collect
  case object Parallel extends Collect
}

object Unliftables {
  def unliftCollect(collect: Expr[Collect])(using Quotes) =
    Implicits.unliftCollect.unliftOrfail(collect)

  def unliftVerify(verify: Expr[Verify])(using Quotes) =
    Implicits.unliftVerify.unliftOrfail(verify)

  def unliftInfoBehavior(info: Expr[InfoBehavior])(using Quotes) =
    Implicits.unliftInfoBehavior.unliftOrfail(info)

  def unliftParams(info: Expr[Params])(using Quotes) =
    Implicits.unliftParams.unliftOrfail(info)

  private object Implicits {
    extension [T](expr: Expr[T])(using unlifter: Unlifter[T], q: Quotes)
      def fromExpr = unlifter.unliftOrfail(expr)

    trait Unlifter[T] extends FromExpr[T]:
      def tpe: Quotes ?=> Type[T]
      def unlift: Quotes ?=> PartialFunction[Expr[T], T]
      def unapply(v: Expr[T])(using Quotes): Option[T] =
        unlift.lift(v)
      def unliftOrfail(v: Expr[T])(using Quotes) =
        import quotes.reflect._
        unlift.lift(v).getOrElse {
          report.errorAndAbort(s"Could not unlift the expression ${Format.Expr(v)} into the type: ${Format.Type(tpe)}")
        }

    given unliftCollect: Unlifter[Collect] with {
      def tpe = Type.of[Collect]
      def unlift =
        case '{ Collect.Sequence } => Collect.Sequence
        case '{ Collect.Parallel } => Collect.Parallel
    }

    given unliftVerify: Unlifter[Verify] with {
      def tpe = Type.of[Verify]
      def unlift =
        case '{ Verify.Strict }  => Verify.Strict
        case '{ Verify.Lenient } => Verify.Lenient
        case '{ Verify.None }    => Verify.None
    }

    given unliftInfoBehavior: Unlifter[InfoBehavior] with {
      def tpe = Type.of[InfoBehavior]
      def unlift =
        case '{ InfoBehavior.Info }        => InfoBehavior.Info
        case '{ InfoBehavior.Silent }      => InfoBehavior.Silent
        case '{ InfoBehavior.Verbose }     => InfoBehavior.Verbose
        case '{ InfoBehavior.VerboseTree } => InfoBehavior.VerboseTree
    }

    given unliftParams: Unlifter[Params] with {
      def tpe = Type.of[Params]
      def unlift =
        case '{ Params($collect, $verify) } =>
          Params(collect.fromExpr, verify.fromExpr)
        case '{ Params(($collect: Collect)) } =>
          Params(collect.fromExpr, Verify.Strict)
        case '{ Params(($verify: Verify)) } =>
          Params(Collect.Sequence, verify.fromExpr)
        case '{ Params.apply() } =>
          Params(Collect.Sequence, Verify.Strict)
    }
  }
}
