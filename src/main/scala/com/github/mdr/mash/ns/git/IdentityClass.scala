package com.github.mdr.mash.ns.git

import com.github.mdr.mash.evaluator.{ AbstractToStringMethod, Field, MashClass }
import com.github.mdr.mash.ns.core.{ AnyClass, StringClass }
import com.github.mdr.mash.runtime.{ MashObject, MashString, MashValue }

object IdentityClass extends MashClass("git.Identity") {

  object Fields {
    val Name = Field("author", "Name", StringClass)
    val Email = Field("email", "Email address", StringClass)
  }
  import Fields._

  override lazy val fields = Seq(Name, Email)

  override lazy val methods = Seq(
    ToStringMethod)

  def summary = "A combination of a person identity and time in Git"

  case class Wrapper(target: MashValue) {
    def name: MashString = target.asInstanceOf[MashObject](Name).asInstanceOf[MashString]
    def email: MashString = target.asInstanceOf[MashObject](Email).asInstanceOf[MashString]
  }

  object ToStringMethod extends AbstractToStringMethod {

    override def toString(target: MashValue) = {
      val identity = Wrapper(target)
      s"${identity.name} <${identity.email}>"
    }

  }

  override def parentOpt = Some(AnyClass)

}