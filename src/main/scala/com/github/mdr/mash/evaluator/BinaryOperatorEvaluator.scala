package com.github.mdr.mash.evaluator

import com.github.mdr.mash.runtime.MashValue
import java.time.Instant
import com.github.mdr.mash.ns.time.MillisecondsClass
import com.github.mdr.mash.utils.PointedRegion
import com.github.mdr.mash.runtime._
import com.github.mdr.mash.ns.time.ChronoUnitClass
import java.time.temporal.TemporalAmount
import java.time.Duration
import com.github.mdr.mash.parser.BinaryOperator
import com.github.mdr.mash.runtime.MashBoolean
import com.github.mdr.mash.parser.AbstractSyntax._

object BinaryOperatorEvaluator {

  def evaluateChainedOp(chainedOp: ChainedOpExpr)(implicit context: EvaluationContext): MashValue = {
    val ChainedOpExpr(left, opRights, _) = chainedOp
    val leftResult = Evaluator.evaluate(left)
    val (success, _) = opRights.foldLeft((true, leftResult)) {
      case ((leftSuccess, leftResult), (op, right)) ⇒
        lazy val rightResult = Evaluator.evaluate(right)
        lazy val thisSuccess = evaluateBinOp(leftResult, op, rightResult, chainedOp.locationOpt).asInstanceOf[MashBoolean].value
        (leftSuccess && thisSuccess, if (leftSuccess) rightResult else leftResult /* avoid evaluating right result if we know the expression is false */ )
    }
    MashBoolean(success)
  }

  def evaluateBinOp(binOp: BinOpExpr)(implicit context: EvaluationContext): MashValue = {
    val BinOpExpr(left, op, right, _) = binOp
    lazy val leftResult = Evaluator.evaluate(left)
    lazy val rightResult = Evaluator.evaluate(right)
    evaluateBinOp(leftResult, op, rightResult, binOp.locationOpt)
  }

  private def evaluateBinOp(leftResult: ⇒ MashValue, op: BinaryOperator, rightResult: ⇒ MashValue, locationOpt: Option[PointedRegion]): MashValue = {
    def compareWith(f: (Int, Int) ⇒ Boolean): MashBoolean = MashBoolean(f(MashValueOrdering.compare(leftResult, rightResult), 0))
    op match {
      case BinaryOperator.And               ⇒ if (Truthiness.isTruthy(leftResult)) rightResult else leftResult
      case BinaryOperator.Or                ⇒ if (Truthiness.isFalsey(leftResult)) rightResult else leftResult
      case BinaryOperator.Equals            ⇒ MashBoolean(leftResult == rightResult)
      case BinaryOperator.NotEquals         ⇒ MashBoolean(leftResult != rightResult)
      case BinaryOperator.Plus              ⇒ add(leftResult, rightResult, locationOpt)
      case BinaryOperator.Minus             ⇒ subtract(leftResult, rightResult, locationOpt)
      case BinaryOperator.Multiply          ⇒ multiply(leftResult, rightResult, locationOpt)
      case BinaryOperator.Divide            ⇒ arithmeticOp(leftResult, rightResult, locationOpt, "divide", _ / _)
      case BinaryOperator.LessThan          ⇒ compareWith(_ < _)
      case BinaryOperator.LessThanEquals    ⇒ compareWith(_ <= _)
      case BinaryOperator.GreaterThan       ⇒ compareWith(_ > _)
      case BinaryOperator.GreaterThanEquals ⇒ compareWith(_ >= _)
      case BinaryOperator.Sequence          ⇒ leftResult; rightResult
    }
  }

  private def arithmeticOp(left: MashValue, right: MashValue, locationOpt: Option[PointedRegion], name: String, f: (MashNumber, MashNumber) ⇒ MashNumber): MashNumber =
    (left, right) match {
      case (left: MashNumber, right: MashNumber) ⇒
        f(left, right)
      case _ ⇒
        throw new EvaluatorException(s"Could not $name, incompatible operands", locationOpt.map(SourceLocation))
    }

  private def multiply(left: MashValue, right: MashValue, locationOpt: Option[PointedRegion]) = (left, right) match {
    case (left: MashString, right: MashNumber) if right.isInt ⇒ left.modify(_ * right.asInt.get)
    case (left: MashNumber, right: MashString) if left.isInt ⇒ right.modify(_ * left.asInt.get)
    case (left: MashNumber, right: MashNumber) ⇒ left * right
    case _ ⇒ throw new EvaluatorException("Could not multiply, incompatible operands", locationOpt.map(SourceLocation))
  }

  private implicit class RichInstant(instant: Instant) {
    def +(duration: TemporalAmount): Instant = instant.plus(duration)
    def -(duration: TemporalAmount): Instant = instant.minus(duration)
  }

  def add(left: MashValue, right: MashValue, locationOpt: Option[PointedRegion]): MashValue = (left, right) match {
    case (xs: MashList, ys: MashList)          ⇒ xs ++ ys
    case (s: MashString, right)                ⇒ s + right
    case (left, s: MashString)                 ⇒ s.rplus(left)
    case (left: MashNumber, right: MashNumber) ⇒ left + right
    case (MashWrapped(instant: Instant), MashNumber(n, Some(klass: ChronoUnitClass))) ⇒
      MashWrapped(instant + klass.temporalAmount(n.toInt))
    case (MashNumber(n, Some(klass: ChronoUnitClass)), MashWrapped(instant: Instant)) ⇒
      MashWrapped(instant + klass.temporalAmount(n.toInt))
    case _ ⇒ throw new EvaluatorException("Could not add, incompatible operands", locationOpt.map(SourceLocation))
  }

  def subtract(left: MashValue, right: MashValue, locationOpt: Option[PointedRegion]): MashValue = (left, right) match {
    case (left: MashNumber, right: MashNumber) ⇒ left - right
    case (MashWrapped(instant: Instant), MashNumber(n, Some(klass: ChronoUnitClass))) ⇒
      MashWrapped(instant - klass.temporalAmount(n.toInt))
    case (MashWrapped(instant1: Instant), MashWrapped(instant2: Instant)) ⇒
      val duration = Duration.between(instant2, instant1)
      val millis = duration.getSeconds * 1000 + duration.getNano / 1000000
      MashNumber(millis, Some(MillisecondsClass))
    case _ ⇒ throw new EvaluatorException("Could not subtract, incompatible operands", locationOpt.map(SourceLocation))
  }

}