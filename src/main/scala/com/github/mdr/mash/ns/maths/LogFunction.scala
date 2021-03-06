package com.github.mdr.mash.ns.maths

import com.github.mdr.mash.functions._
import com.github.mdr.mash.ns.core.NumberClass
import com.github.mdr.mash.runtime.MashNumber

object LogFunction extends MashFunction("maths.log") {

  object Params {
    val N = Parameter(
      nameOpt = Some("n"),
      summaryOpt = Some("Number to take the logarithm of"))
    val Base = Parameter(
      nameOpt = Some("base"),
      summaryOpt = Some("base"),
      defaultValueGeneratorOpt = Some(MashNumber(Math.E)))
  }
  import Params._

  val params = ParameterModel(N, Base)

  def call(boundParams: BoundParams): MashNumber = {
    val n = boundParams.validateNumber(N)
    val base = boundParams.validateNumber(Base)
    MashNumber(base match {
      case 10     ⇒ Math.log10(n)
      case Math.E ⇒ math.log(n)
      case _      ⇒ Math.log(n) / Math.log(base)
    })
  }

  override def typeInferenceStrategy = NumberClass

  override def summaryOpt = Some("Calculate the logarithm of a given value")
}