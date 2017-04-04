package com.github.mdr.mash.evaluator

import java.time.{ Instant, LocalDate }

import com.github.mdr.mash.classes.{ BoundMethod, Field, MashClass }
import com.github.mdr.mash.evaluator.Evaluator.suggestionSuffix
import com.github.mdr.mash.functions.MashFunction
import com.github.mdr.mash.ns.collections.ListClass
import com.github.mdr.mash.ns.core._
import com.github.mdr.mash.ns.time.{ DateClass, DateTimeClass }
import com.github.mdr.mash.parser.AbstractSyntax._
import com.github.mdr.mash.parser.ConcreteSyntax
import com.github.mdr.mash.runtime._
import com.github.mdr.mash.utils.{ PointedRegion, Utils }

import scala.PartialFunction.condOpt

object MemberEvaluator extends EvaluatorHelper {

  case class MemberExprEvalResult(result: MashValue, wasVectorised: Boolean = false)

  /**
    * @param invokeNullaryWhenVectorising if true, then any vectorised member lookups that result in
    *                                     nullary-callable functions will be invoked. If false, they will
    *                                     be returned as-is. (false is useful for vectorised invocations).
    */
  def evaluateMemberExpr(memberExpr: MemberExpr,
                         invokeNullaryWhenVectorising: Boolean)(implicit context: EvaluationContext): MemberExprEvalResult = {
    val target = Evaluator.evaluate(memberExpr.target)
    val thisTarget = memberExpr.target.isInstanceOf[ThisExpr]
    evaluateMemberExpr(memberExpr, target, thisTarget, invokeNullaryWhenVectorising)
  }

  def evaluateMemberExpr(memberExpr: AbstractMemberExpr,
                         target: MashValue,
                         thisTarget: Boolean,
                         invokeNullaryWhenVectorising: Boolean)(implicit context: EvaluationContext): MemberExprEvalResult = {
    val name = memberExpr.name
    val isSafe = memberExpr.isSafe
    val locationOpt = getLocation(memberExpr)
    val scalarLookup = MemberEvaluator.maybeLookup(target, name, includePrivate = thisTarget).map(result ⇒
      MemberExprEvalResult(result))
    def vectorisedLookup = vectorisedMemberLookup(target, name, isSafe, invokeNullaryWhenVectorising, locationOpt)
      .map(result ⇒ MemberExprEvalResult(result, wasVectorised = true))
    scalarLookup orElse
      vectorisedLookup getOrElse {
      if (isSafe)
        MemberExprEvalResult(MashNull)
      else {
        val names = getMemberNames(target)
        throw new EvaluatorException(s"Cannot find member '$name' in value of type ${target.typeName}${suggestionSuffix(names, name)}", locationOpt)
      }
    }
  }

  def getLocation(memberExpr: AbstractMemberExpr): Option[SourceLocation] = {
    memberExpr.sourceInfoOpt.flatMap(info ⇒ condOpt(info.node) {
      case ConcreteSyntax.MemberExpr(_, _, nameToken) ⇒
        SourceLocation(info.provenance, PointedRegion(nameToken.offset, nameToken.region))
    })
  }

  private def vectorisedMemberLookup(target: MashValue,
                                     name: String,
                                     isSafe: Boolean,
                                     immediatelyResolveNullaryWhenVectorising: Boolean,
                                     locationOpt: Option[SourceLocation]): Option[MashList] =
    target match {
      case xs: MashList ⇒
        val options = xs.elements.map { element ⇒
          val memberOpt = MemberEvaluator.maybeLookup(element, name)
          val resolvedOpt =
            if (immediatelyResolveNullaryWhenVectorising)
              memberOpt.map(lookup ⇒ Evaluator.invokeNullaryFunctions(lookup, locationOpt))
            else
              memberOpt
          if (isSafe) resolvedOpt orElse Some(MashNull) else resolvedOpt
        }
        Utils.sequence(options).map(MashList(_))
      case _            ⇒
        None
    }

  private def lookupMethod(target: MashValue,
                           klass: MashClass,
                           name: String,
                           includePrivate: Boolean = false,
                           includeShyParents: Boolean = true): Option[BoundMethod] = {
    val directResultOpt =
      for {
        method ← klass.getMethod(name)
        if method.isPublic || includePrivate
      } yield BoundMethod(target, method, klass)
    def parentResultOpt = klass.parentOpt
      .flatMap(parentClass ⇒ lookupMethod(target, parentClass, name))
      .filter(includeShyParents || !_.method.isShy)
    directResultOpt orElse parentResultOpt
  }

  def lookup(target: MashValue, field: Field): MashValue =
    lookup(target, field.name)

  def lookup(target: MashValue, name: String, includePrivate: Boolean = false, locationOpt: Option[SourceLocation] = None): MashValue =
    maybeLookup(target, name, includePrivate).getOrElse {
      val names = getMemberNames(target)
      throw new EvaluatorException(s"Cannot find member '$name' in value of type ${target.typeName}${suggestionSuffix(names, name)}", locationOpt)
    }

  def hasMember(target: MashValue, name: String): Boolean =
    maybeLookup(target, name).isDefined

  /**
    * @return a bound method, a static method, or a field value corresponding to the given name in the target
    */
  def maybeLookup(target: MashValue,
                  name: String,
                  includePrivate: Boolean = false,
                  includeShyParents: Boolean = true): Option[MashValue] =
    target match {
      case MashNumber(n, tagClassOpt)     ⇒ lookupMethod(target, NumberClass, name) orElse tagClassOpt.flatMap(lookupMethod(target, _, name))
      case MashString(s, tagClassOpt)     ⇒ lookupMethod(target, StringClass, name) orElse tagClassOpt.flatMap(lookupMethod(target, _, name))
      case MashNull                       ⇒ lookupMethod(target, NullClass, name)
      case MashUnit                       ⇒ lookupMethod(target, UnitClass, name)
      case b: MashBoolean                 ⇒ lookupMethod(b, BooleanClass, name)
      case xs: MashList                   ⇒ lookupMethod(xs, ListClass, name)
      case f: MashFunction                ⇒ lookupMethod(f, FunctionClass, name)
      case bm: BoundMethod                ⇒ lookupMethod(bm, BoundMethodClass, name)
      case klass: MashClass               ⇒ klass.getStaticMethod(name) orElse lookupMethod(klass, ClassClass, name)
      case dt@MashWrapped(_: Instant)     ⇒ lookupMethod(dt, DateTimeClass, name)
      case date@MashWrapped(_: LocalDate) ⇒ lookupMethod(date, DateClass, name)
      case obj: MashObject                ⇒ obj.get(name) orElse lookupMethod(obj, obj.classOpt getOrElse ObjectClass, name, includePrivate, includeShyParents)
    }

  private def getMemberNames(target: MashValue): Seq[String] = {
    val memberNames = target match {
      case MashNumber(n, tagClassOpt)     ⇒ NumberClass.memberNames ++ tagClassOpt.toSeq.flatMap(_.memberNames)
      case MashString(s, tagClassOpt)     ⇒ StringClass.memberNames ++ tagClassOpt.toSeq.flatMap(_.memberNames)
      case MashNull                       ⇒ NullClass.memberNames
      case MashUnit                       ⇒ UnitClass.memberNames
      case b: MashBoolean                 ⇒ BooleanClass.memberNames
      case xs: MashList                   ⇒ ListClass.memberNames
      case f: MashFunction                ⇒ FunctionClass.memberNames
      case bm: BoundMethod                ⇒ BoundMethodClass.memberNames
      case klass: MashClass               ⇒ ClassClass.memberNames
      case dt@MashWrapped(_: Instant)     ⇒ DateTimeClass.memberNames
      case date@MashWrapped(_: LocalDate) ⇒ DateClass.memberNames
      case obj: MashObject                ⇒ obj.immutableFields.keys.toSeq ++ (obj.classOpt getOrElse ObjectClass).memberNames
    }
    (memberNames ++ AnyClass.memberNames).distinct
  }

}