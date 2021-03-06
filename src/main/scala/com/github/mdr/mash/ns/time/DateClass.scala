package com.github.mdr.mash.ns.time

import com.github.mdr.mash.classes.MashClass
import com.github.mdr.mash.ns.core.AnyClass

object DateClass extends MashClass("time.Date") {

  override def summaryOpt = Some("A date")

  override def parentOpt = Some(AnyClass)

}