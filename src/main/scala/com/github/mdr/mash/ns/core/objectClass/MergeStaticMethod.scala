package com.github.mdr.mash.ns.core.objectClass

import com.github.mdr.mash.functions.{ BoundParams, MashFunction, Parameter, ParameterModel }
import com.github.mdr.mash.runtime.{ MashList, MashObject }

object MergeStaticMethod extends MashFunction("merge") {

  object Params {
    val Objects = Parameter(
      nameOpt = Some("objects"),
      summaryOpt = Some("Objects to merge"),
      isVariadic = true,
      variadicAtLeastOne = true)
  }

  import Params._

  val params = ParameterModel(Objects)

  override def call(boundParams: BoundParams): MashObject = {
    val xs = boundParams.validateSequence(Objects)
    val items = xs match {
      case Seq(list: MashList) ⇒ list.immutableElements
      case _                   ⇒ xs
    }
    val objects = items.map {
      case item: MashObject ⇒ item
      case badItem          ⇒ boundParams.throwInvalidArgument(Objects, "Cannot merge value of type " + badItem.typeName)
    }
    MashObject.merge(objects)
  }

  override def summaryOpt = Some("Merge objects together")

}
