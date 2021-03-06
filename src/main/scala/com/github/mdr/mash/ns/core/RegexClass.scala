package com.github.mdr.mash.ns.core

import com.github.mdr.mash.classes.{ Field, MashClass }
import com.github.mdr.mash.functions.{ BoundParams, MashMethod, Parameter, ParameterModel }
import com.github.mdr.mash.runtime._

import scala.collection.immutable.ListMap

object RegexClass extends MashClass("core.Regex") {

  override val methods = Seq(
    MatchMethod)

  object MatchMethod extends MashMethod("match") {

    object Params {
      val String = Parameter(
        nameOpt = Some("string"),
        summaryOpt = Some("String to search within for matches"))
    }

    private val String = "string"

    val params = ParameterModel(Params.String)

    def call(target: MashValue, boundParams: BoundParams): MashValue = {
      val regex = target.asInstanceOf[MashString].s.r
      val s = boundParams.validateString(Params.String).s
      val matchOption = regex.findFirstMatchIn(s)
      matchOption.map { m ⇒
        import MatchClass.Fields
        val groups = MashList(m.subgroups.map {
          case null ⇒ MashNull
          case s ⇒ MashString(s)
        })
        MashObject.of(
          ListMap(
            Fields.Matched -> MashString(m.matched),
            Fields.Groups -> groups),
          klass = MatchClass)
      }.getOrElse(MashNull)
    }

    override def typeInferenceStrategy = MatchClass

    override def summaryOpt = Some("Find the first match of the regex in the given string, or else null")
  }

  override def summaryOpt = Some("A regular expression")

  override def parentOpt = Some(AnyClass)

}

object MatchClass extends MashClass("core.Match") {

  object Fields {
    val Matched = "matched"
    val Groups = "groups"
  }

  override val fields = Seq(
    Field(Fields.Matched, Some("The matched region of text"), StringClass),
    Field(Fields.Groups, Some("Type (file, dir, link)"), Seq(StringClass)))

  override def summaryOpt = Some("A match from a regular expression search")

}
