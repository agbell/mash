package com.github.mdr.mash.ns.collections

import scala.PartialFunction.condOpt
import com.github.mdr.mash.completions.CompletionSpec
import com.github.mdr.mash.evaluator.Arguments
import com.github.mdr.mash.functions.MashFunction
import com.github.mdr.mash.functions.Parameter
import com.github.mdr.mash.functions.ParameterModel
import com.github.mdr.mash.inference.AnnotatedExpr
import com.github.mdr.mash.inference.Inferencer
import com.github.mdr.mash.inference.Type
import com.github.mdr.mash.inference.TypeInferenceStrategy
import com.github.mdr.mash.inference.TypedArguments
import com.github.mdr.mash.ns.collections.FlatMapFunction.Params
import com.github.mdr.mash.ns.core.StringClass
import com.github.mdr.mash.runtime.MashList
import com.github.mdr.mash.runtime.MashString
import com.github.mdr.mash.runtime.MashValue
import com.github.mdr.mash.evaluator.EvaluatorException

object FlatMapFunction extends MashFunction("collections.flatMap") {

  object Params {
    val F = Parameter(
      name = "f",
      summary = "Function used to transform elements of the sequence",
      descriptionOpt = Some("Must return a sequence"))
    val Sequence = Parameter(
      name = "sequence",
      summary = "Sequence to map over",
      isLast = true)
  }

  import Params._

  val params = ParameterModel(Seq(F, Sequence))

  def apply(arguments: Arguments): MashValue = {
    val boundParams = params.validate(arguments)
    val inSequence = boundParams(Sequence)
    val sequence = boundParams.validateSequence(Sequence)
    val f = boundParams.validateFunction(F)
    val mapped = sequence.flatMap { item ⇒
      f(item) match {
        case MashList(items @ _*) ⇒
          items
        case badItem ⇒
          throw new EvaluatorException("Invalid item of type " + badItem.primaryClass)
      }
    }
    MashList(mapped)
  }

  override def typeInferenceStrategy = FlatMapTypeInferenceStrategy

  override def getCompletionSpecs(argPos: Int, arguments: TypedArguments) = {
    val argBindings = FlatMapFunction.params.bindTypes(arguments)
    val specOpt =
      for {
        param ← argBindings.paramAt(argPos)
        if param == F
        AnnotatedExpr(_, Some(Type.Seq(elementType))) ← argBindings.get(Sequence)
      } yield CompletionSpec.Members(elementType)
    specOpt.toSeq
  }

  override def summary = "Transform each element of a sequence by a given function, and then flatten"

  override def descriptionOpt = Some("""The given function is applied to each element of the input sequence 
  and is expected to yield a sequence for each element. The result is flattened to produce a sequence of transformed 
  output elements.

Examples:
  flatMap (x => [x * 10, x * 100]) [1, 2, 3] # [20, 200, 40, 400, 60, 600]""")

}

object FlatMapTypeInferenceStrategy extends TypeInferenceStrategy {

  def inferTypes(inferencer: Inferencer, arguments: TypedArguments): Option[Type] = {
    val argBindings = FlatMapFunction.params.bindTypes(arguments)
    import FlatMapFunction.Params._
    val functionOpt = argBindings.get(F)
    val sequenceOpt = argBindings.get(Sequence)
    val newElementSeqTypeOpt = MapTypeInferenceStrategy.inferAppliedType(inferencer, functionOpt, sequenceOpt)
    for {
      AnnotatedExpr(_, sequenceTypeOpt) ← sequenceOpt
      sequenceType ← sequenceTypeOpt
      newSequenceType ← condOpt(sequenceType) {
        case Type.Seq(_) ⇒
          newElementSeqTypeOpt match {
            case Some(Type.Seq(newElementType)) ⇒ Type.Seq(newElementType)
            case _                              ⇒ Type.Seq(Type.Any)
          }
      }
    } yield newSequenceType
  }

}