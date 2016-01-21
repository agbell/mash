package com.github.mdr.mash.ns.core

import com.github.mdr.mash.evaluator._
import com.github.mdr.mash.functions.MashFunction
import com.github.mdr.mash.functions.Parameter
import com.github.mdr.mash.functions.ParameterModel
import com.github.mdr.mash.inference._
import java.io.PrintStream

object PrintFunction extends MashFunction("core.print") {

  private val output: PrintStream = System.out

  object Params {
    val Item = Parameter(
      name = "item",
      summary = "Item to negate")
  }
  import Params._

  val params = ParameterModel(Seq(Item))

  def apply(arguments: Arguments) {
    val boundParams = params.validate(arguments)
    val item = boundParams(Item)
    output.println(ToStringifier.stringify(item))
  }

  override def typeInferenceStrategy = ConstantTypeInferenceStrategy(Type.Instance(UnitClass))

  override def summary = "Print the given argument to standard output"

}