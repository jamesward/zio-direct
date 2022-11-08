package zio.asyncawait.core.norm

import zio.asyncawait.core.metaprog.Model
import scala.quoted._
import zio.asyncawait.core.metaprog.Embedder._
import zio.ZIO
import zio.asyncawait.core.metaprog.ModelPrinting
import zio.Chunk
import zio.asyncawait.core.util.ComputeTotalZioType
import zio.asyncawait.core.util.Format

trait ModelReconstructor {
  self: Model with ModelPrinting =>

  implicit val macroQuotes: Quotes
  import macroQuotes.reflect._


  protected object Reconstruct {
    private def computeSymbolType(valSymbol: Option[Symbol], alternativeSource: Term) =
      valSymbol match
        case Some(oldSymbol) =>
          oldSymbol.termRef.widenTermRefByName.asType
        case None =>
          alternativeSource.tpe.asType match
            case '[ZIO[r, e, a]] => Type.of[a]

    private def compressBlock(accum: List[Statement] = List(), block: IR.Block): (List[Statement], Term) =
      block.tail match
        case nextBlock: IR.Block =>
          compressBlock(block.head +: accum, nextBlock)
        case otherMonad =>
          (block.head +: accum, apply(otherMonad).asTerm)

    def apply(ir: IR): Expr[ZIO[?, ?, ?]] = {
      ir match
        case IR.Pure(code) => ZioApply(code)

        case IR.FlatMap(monad, valSymbol, body) => {
          val monadExpr = apply(monad)
          val bodyExpr = apply(body)
          def symbolType = computeSymbolType(valSymbol, monadExpr.asTerm)
          // Symbol type needs to be the same as the A-parameter of the ZIO, if not it's an error
          // should possibly introduce an asserition for that
          // Also:
          // TODO synthesize + eta-expand the lambda manually so it's ame is based on the previous symbol name
          symbolType match
            case '[t] =>
              '{ $monadExpr.asInstanceOf[ZIO[?, ?, t]].flatMap((v: t) =>
                ${
                  replaceSymbolInBodyMaybe(using macroQuotes)(bodyExpr.asTerm)(valSymbol, ('v).asTerm).asExprOf[ZIO[?, ?, ?]]
                }
              ) } //.asInstanceOf[ZIO[?, ?, ?]]
        }

        // Pull out the value from IR.Pure and use it directly in the mapping
        case IR.Map(monad, valSymbol, IR.Pure(body)) =>
          val monadExpr = apply(monad)
          def symbolType = computeSymbolType(valSymbol, monadExpr.asTerm)
          symbolType match
          // TODO check that 'a' is the same as 't' here?
          case '[t] =>
            '{ $monadExpr.asInstanceOf[ZIO[?, ?, t]].map((v: t) =>
              ${replaceSymbolInBodyMaybe(using macroQuotes)(body)(valSymbol, ('v).asTerm).asExpr}
            ) } // .asInstanceOf[ZIO[?, ?, ?]] // since the body has it's own term not specifying that here
            // TODO any ownership changes needed in the body?

        case IR.Monad(code) => code.asExprOf[ZIO[?, ?, ?]]

        case block: IR.Block =>
          val (stmts, term) = compressBlock(List(), block)
          //println(s"----------- Block Stmts: ${stmts.map(_.show)}")
          Block(stmts, term).asExprOf[ZIO[?, ?, ?]]

        case IR.Match(scrutinee, caseDefs) =>
          scrutinee match
            case value: IR.Monadic =>
              val monadExpr = apply(value)
              // use the symbol instead of the monad as the scrutinee because the monad needs to be flatmapped first
              // (Note don't think you need to change ownership of caseDef.rhs to the new symbol but possible.)
              val matchSymbol = Symbol.newVal(Symbol.spliceOwner, "matchVar", monadExpr.asTerm.tpe, Flags.EmptyFlags, Symbol.noSymbol)
              val newCaseDefs =
                caseDefs.map { caseDef =>
                  val bodyExpr = apply(caseDef.rhs)
                  CaseDef(caseDef.pattern, caseDef.guard, bodyExpr.asTerm)
                }
              // even if the content of the match is pure we lifted it into a monad. If we want to optimize we
              // change the IR.CaseDef.rhs to be IR.Pure as well as IR.Monadic and handle both cases
              val newMatch = Match(Ref(matchSymbol), newCaseDefs)
              // We can synthesize the monadExpr.flatMap call from the monad at this point but I would rather pass it to the FlatMap case to take care of
              apply(IR.FlatMap(IR.Monad(monadExpr.asTerm), matchSymbol, IR.Monad(newMatch)))
            case IR.Pure(termValue) =>
              val newCaseDefs =
                caseDefs.map { caseDef =>
                  val bodyExpr = apply(caseDef.rhs)
                  CaseDef(caseDef.pattern, caseDef.guard, bodyExpr.asTerm)
                }
              val newMatch = Match(termValue, newCaseDefs)
              // recall that the expressions in the case defs need all be ZIO instances (i.e. monadic) we we can
              // treat the whole thing as a ZIO (i.e. monadic) expression
              newMatch.asExprOf[ZIO[?, ?, ?]]

        case IR.If(cond, ifTrue, ifFalse) =>
          enum ConditionState:
            case BothPure(ifTrue: Term, ifFalse: Term)
            case BothMonadic(ifTrue: IR.Monadic, ifFalse: IR.Monadic)

          val conditionState =
            (ifTrue, ifFalse) match {
              case (IR.Pure(a), IR.Pure(b)) => ConditionState.BothPure(a, b)
              case (IR.Pure(a), b: IR.Monadic) => ConditionState.BothMonadic(IR.Monad(ZioApply(a).asTerm), b)
              case (a: IR.Monadic, IR.Pure(b)) => ConditionState.BothMonadic(a, IR.Monad(ZioApply(b).asTerm))
              case (a: IR.Monadic, b: IR.Monadic) => ConditionState.BothMonadic(a, b)
            }

          cond match {
            case m: IR.Monadic => {
              val sym = Symbol.newVal(Symbol.spliceOwner, "ifVar", TypeRepr.of[Boolean], Flags.EmptyFlags, Symbol.noSymbol)
              conditionState match {
                // For example: if(await(something)) await(foo) else await(bar)
                // => something.map(ifVar => (foo, bar) /*replace-to-ifVar*/)
                // Note that in this case we embed foo, bar into the if-statement. They are ZIO-values which is why we need a flatMap
                case ConditionState.BothMonadic(ifTrue, ifFalse) =>
                  val ifTrueTerm = apply(ifTrue).asTerm
                  val ifFalseTerm = apply(ifFalse).asTerm
                  apply(IR.FlatMap(m, sym, IR.Monad(If(Ref(sym), ifTrueTerm, ifFalseTerm))))
                // For example: if(await(something)) "foo" else "bar"
                case ConditionState.BothPure(ifTrue, ifFalse) =>
                  apply(IR.Map(m, sym, IR.Pure(If(Ref(sym), ifTrue, ifFalse))))
              }
            }
            case IR.Pure(value) => {
              conditionState match {
                case ConditionState.BothMonadic(ifTrue, ifFalse) =>
                    val ifTrueTerm = apply(ifTrue).asTerm
                    val ifFalseTerm = apply(ifFalse).asTerm
                    ZioApply(If(value, ifTrueTerm, ifFalseTerm))
                case ConditionState.BothPure(ifTrue, ifFalse) =>
                  val ifStatement = If(value, ifTrue, ifFalse)
                  ZioApply(ifStatement)
              }
            }
          }

        case expr @ IR.And(left, right) =>
          (left, right) match {
            case (a: IR.Monadic, b: IR.Monadic) =>
              '{ ${apply(a)}.flatMap { case true => ${apply(b)}; case false => ${ZioApply.False} } }
            case (a: IR.Monadic, IR.Pure(b)) =>
              '{ ${apply(a)}.map { case true => ${b.asExpr}; case false => ${ZioApply.False} } }
            case (IR.Pure(a), b: IR.Monadic) =>
              '{ if (${a.asExprOf[Boolean]}) ${apply(b)} else ZIO.succeed(${ZioApply.False}) }
            // case Pure/Pure is taken care by in the transformer on a higher-level via the PureTree case. Still, handle them just in case
            case (IR.Pure(a), IR.Pure(b)) =>
              '{ ZIO.succeed(${a.asExprOf[Boolean]} && ${b.asExprOf[Boolean]}) }
            case _ => report.errorAndAbort(s"Invalid boolean variable combination:\n${mprint(expr)}")
          }

        case expr @ IR.Or(left, right) =>
          (left, right) match {
            case (a: IR.Monadic, b: IR.Monadic) =>
              '{ ${apply(a)}.flatMap { case true => ${ZioApply.True}; case false => ${apply(b)}  } }
            case (a: IR.Monadic, IR.Pure(b)) =>
              '{ ${apply(a)}.map { case true => ${ZioApply.True}; case false => ${b.asExpr} } }
            case (IR.Pure(a), b: IR.Monadic) =>
              '{ if (${a.asExprOf[Boolean]}) ${ZioApply.True} else ${apply(b)} }
            // case Pure/Pure is taken care by in the transformer on a higher-level via the PureTree case. Still, handle them just in case
            case (IR.Pure(a), IR.Pure(b)) =>
              '{ ZIO.succeed(${a.asExprOf[Boolean]} || ${b.asExprOf[Boolean]}) }
            case _ => report.errorAndAbort(s"Invalid boolean variable combination:\n${mprint(expr)}")
          }

        case IR.Parallel(unlifts, newTree) =>
          unlifts.toList match {
            case List() =>
              //println("=========== No Unlifts ==========")
              '{ ZIO.succeed(${newTree.asExpr}) }

            /*
            For a expression (in a single block-line) that has one await in the middle of things e.g.
            { await(foo) + bar }
            Needs to turn into something like:
            { await(foo).map(fooVal => fooVal + bar) }
            When thinking about types, it looks something like:
            { (await(foo:Task[t]):t + bar):r }
            { await(foo:t):Task[t].map[r](fooVal:t => (fooVal + bar):r) }
            */
            case List((monad, name)) =>
              val out =
              (monad.tpe.asType, newTree.tpe.asType) match
                case ('[ZIO[x, y, t]], '[r]) =>
                  val mtpe = MethodType(List("sm"))(_ => List(TypeRepr.of[t]), _ => TypeRepr.of[r])
                  val lam =
                    Lambda(Symbol.spliceOwner, mtpe, {
                        case (methSym, List(sm: Term)) =>
                          replaceSymbolIn(newTree)(name, sm).changeOwner(methSym)
                        case _ =>
                          report.errorAndAbort(s"Invalid lambda created for: ${Format.Tree(monad)}.flatMap of ${Format.Tree(newTree)}. This should not be possible.")
                      }
                    )
                  apply(IR.Monad('{ ${monad.asExprOf[ZIO[?, ?, t]]}.map[r](${lam.asExprOf[t => r]}) }.asTerm))
              //println("=========== Single unlift: ==========\n" + Format.Term(out.get.code))
              out

            case unlifts =>
              val unliftTriples =
                unlifts.map(
                  (term, name) => {
                    val tpe =
                      term.tpe.asType match
                        case '[ZIO[x, y, t]] => TypeRepr.of[t]
                    (term, name, tpe)
                })
              val (terms, names, types) = unliftTriples.unzip3
              val termsExpr = Expr.ofList(terms.map(_.asExprOf[ZIO[?, ?, ?]]))
              val collect = '{ ZIO.collectAll(Chunk.from($termsExpr)) }
              def makeVariables(iterator: Expr[Iterator[?]]) =
                unliftTriples.map((monad, symbol, tpe) =>
                    tpe.asType match {
                      case '[t] =>
                        ValDef(symbol, Some('{ $iterator.next().asInstanceOf[t] }.asTerm))
                    }
                )

              val totalType = ComputeTotalZioType.valueOf(terms: _*)
              val output =
                totalType.asType match
                  case '[t] =>
                    '{
                      $collect.map(terms => {
                        val iter = terms.iterator
                        ${ Block(makeVariables('iter), newTree).asExpr }
                      }).asInstanceOf[ZIO[?, ?, t]]
                    }

              val out = apply(IR.Monad(output.asTerm))
              // println(s"============ Computed Output: ${Format.TypeRepr(output.asTerm.tpe)}")
              // println("=========== Multiple unlift: ==========\n" + Format.Expr(output))
              out
          }
    }
  }

}