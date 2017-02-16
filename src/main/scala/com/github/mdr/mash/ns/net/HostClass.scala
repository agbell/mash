package com.github.mdr.mash.ns.net

import java.net.InetAddress

import com.github.mdr.mash.classes.MashClass
import com.github.mdr.mash.evaluator.Arguments
import com.github.mdr.mash.functions.{ MashMethod, ParameterModel }
import com.github.mdr.mash.inference.Type
import com.github.mdr.mash.ns.core.{ AnyClass, StringClass }
import com.github.mdr.mash.runtime.{ MashList, MashString, MashValue }

object HostClass extends MashClass("net.Host") {

  override val methods = Seq(IpAddressesMethod)

  override def summaryOpt = Some("Host")

  object IpAddressesMethod extends MashMethod("ipAddresses") {

    val params = ParameterModel()

    def apply(target: MashValue, arguments: Arguments): MashValue = {
      params.validate(arguments)
      val host = target.asInstanceOf[MashString].s
      val addresses = InetAddress.getAllByName(host).map(asMashString)
      MashList(addresses)
    }

    override def typeInferenceStrategy = Type.Seq(StringClass)

    override def summaryOpt = Some("Look up the IP addresses of the given host")

    def asMashString(address: InetAddress) = MashString(address.getHostAddress)

  }

  override def parentOpt = Some(AnyClass)

}

