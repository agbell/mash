package com.github.mdr.mash.ns.core

import com.github.mdr.mash.evaluator._
import com.github.mdr.mash.functions.{ MashFunction, Parameter, ParameterModel }
import com.github.mdr.mash.runtime.{ MashBoolean, MashNull }

object IsNullFunction extends MashFunction("core.isNull") {

  object Params {
    val Value = Parameter(
      nameOpt = Some("value"),
      summary = "Value to test for nullness")
  }
  import Params._

  val params = ParameterModel(Seq(Value))

  def apply(arguments: Arguments): MashBoolean = {
    val boundParams = params.validate(arguments)
    MashBoolean(boundParams(Value).isNull)
  }

  override def summaryOpt = Some("Check whether or not the given argument is null")

  override def descriptionOpt = Some("""Examples:
  isNull null # true
  isNull 0    # false""")

}
