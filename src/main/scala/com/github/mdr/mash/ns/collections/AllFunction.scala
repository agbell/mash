package com.github.mdr.mash.ns.collections

import com.github.mdr.mash.functions._
import com.github.mdr.mash.inference._
import com.github.mdr.mash.completions.CompletionSpec
import com.github.mdr.mash.evaluator.Arguments
import com.github.mdr.mash.evaluator.Truthiness
import com.github.mdr.mash.ns.core.BooleanClass

object AllFunction extends MashFunction("collections.all") {

  object Params {
    val Predicate = Parameter(
      name = "predicate",
      summary = "Predicate used to test elements of the sequence")
    val Sequence = Parameter(
      name = "sequence",
      summary = "Sequence of elements to test",
      isLast = true)
  }

  import Params._

  val params = ParameterModel(Seq(Predicate, Sequence))

  def apply(arguments: Arguments): Boolean = {
    val boundParams = params.validate(arguments)
    val sequence = FunctionHelpers.interpretAsSequence(boundParams(Sequence))
    val predicate = FunctionHelpers.interpretAsFunction(boundParams(Predicate))
    sequence.forall(x ⇒ Truthiness.isTruthy(predicate(x)))
  }

  override def typeInferenceStrategy = AllTypeInferenceStrategy

  override def getCompletionSpecs(argPos: Int, arguments: TypedArguments) =
    MapFunction.getCompletionSpecs(argPos, arguments)

  override def summary = "Check whether a predicate holds for every element in a sequence"

  override def descriptionOpt = Some("""Returns true if the given predicate returns a truthy result for every element in the given sequence; false otherwise.
    
Examples
  all (_ > 0) [1, 2, 3]  # true
  all (_ > 0) [1, -2, 3] # false
  all (_ > 0) []         # true""")

}

object AllTypeInferenceStrategy extends TypeInferenceStrategy {

  def inferTypes(inferencer: Inferencer, arguments: TypedArguments): Option[Type] = {
    import AllFunction.Params._
    val argBindings = AllFunction.params.bindTypes(arguments)
    val sequenceExprOpt = argBindings.get(Sequence)
    val predicateExprOpt = argBindings.get(Predicate)
    MapTypeInferenceStrategy.inferAppliedType(inferencer, predicateExprOpt, sequenceExprOpt)
    Some(Type.Instance(BooleanClass))
  }

}