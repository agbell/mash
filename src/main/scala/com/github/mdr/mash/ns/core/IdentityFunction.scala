package com.github.mdr.mash.ns.core

import com.github.mdr.mash.evaluator._
import com.github.mdr.mash.functions.MashFunction
import com.github.mdr.mash.functions.Parameter
import com.github.mdr.mash.functions.ParameterModel
import com.github.mdr.mash.inference._

object IdentityFunction extends MashFunction("core.identity") {

  object Params {
    val Item = Parameter(
      name = "item",
      summary = "Item to return")
  }
  import Params._

  val params = ParameterModel(Seq(Item))

  def apply(arguments: Arguments): Any = {
    val boundParams = params.validate(arguments)
    boundParams(Item)
  }

  override def typeInferenceStrategy = ConstantTypeInferenceStrategy(Type.Instance(BooleanClass))

  override def summary = "Return the argument unchanged"

  override def descriptionOpt = Some("""Examples:
  id 42 # 42""")

}
