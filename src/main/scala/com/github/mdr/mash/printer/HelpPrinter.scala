package com.github.mdr.mash.printer

import java.io.PrintStream

import com.github.mdr.mash.evaluator.{ Field, ToStringifier }
import com.github.mdr.mash.functions.Parameter
import com.github.mdr.mash.ns.core.help.{ ClassHelpClass, FieldHelpClass, FunctionHelpClass, ParameterHelpClass }
import com.github.mdr.mash.runtime._
import com.github.mdr.mash.utils.StringUtils.indent
import org.fusesource.jansi.Ansi
import org.fusesource.jansi.Ansi.Color

/**
 * Render function/method/field help objects in a similar style to man pages
 */
class HelpPrinter(output: PrintStream) {

  private val indentAmount = 8

  private val indentSpace = " " * indentAmount

  def bold(s: Any) = Ansi.ansi().bold().a("" + s).boldOff().toString

  def printFunctionHelp(obj: MashObject) {
    import FunctionHelpClass.Fields._
    val classOpt = MashNull.option(obj(Class)).map(_.asInstanceOf[MashString].s)
    output.println(bold(if (classOpt.isDefined) "METHOD" else "FUNCTION"))
    val summaryOpt = MashNull.option(obj(Summary))
    val name = ToStringifier.stringify(obj(FullyQualifiedName))
    val aliases = obj(Aliases).asInstanceOf[MashList].elements.map(ToStringifier.stringify)
    val names = (name +: aliases).map(bold(_)).mkString(", ")
    output.println(indentSpace + bold(names) + summaryOpt.fold("")(" - " + _))

    output.println()
    for (klass ← classOpt) {
      output.println(bold("CLASS"))
      output.println(indentSpace + klass)
      output.println()
    }
    output.println(bold("CALLING SYNTAX"))
    val maybeTarget = if (classOpt.isDefined) "target." else ""
    output.println(indentSpace + maybeTarget + obj(CallingSyntax))
    output.println()
    val parameters = obj(Parameters).asInstanceOf[MashList].elements
    if (parameters.nonEmpty) {
      output.println(bold("PARAMETERS"))
      for (param ← parameters)
        printParameterHelp(param.asInstanceOf[MashObject])
    }
    for (description ← MashNull.option(obj(Description)).map(_.asInstanceOf[MashString])) {
      output.println(bold("DESCRIPTION"))
      output.println(indent(description.s, indentAmount))
      output.println()
    }
  }

  def printFieldHelp(obj: MashObject) {
    val fieldHelp = FieldHelpClass.Wrapper(obj)
    output.println(bold("FIELD"))
    output.println(indentSpace + bold(fieldHelp.name) + fieldHelp.summaryOpt.fold("")(" - " + _))
    output.println()
    output.println(bold("CLASS"))
    output.println(indentSpace + fieldHelp.klass)
    output.println()
    for (description ← fieldHelp.descriptionOpt) {
      output.println(bold("DESCRIPTION"))
      output.println(indent(description, indentAmount))
      output.println()
    }
  }

  def printParameterHelp(param: MashObject) {
    import ParameterHelpClass.Fields._
    def paramNameStyle(s: Any) = Ansi.ansi().bold().fg(Color.BLUE).a("" + s).boldOff().fg(Color.DEFAULT).toString
    output.print(indentSpace)
    def boolParam(field: Field) = param(field).asInstanceOf[MashBoolean].value
    var qualifiers: Seq[String] = Seq()
    val isFlag = boolParam(IsFlagParameter)
    if (boolParam(IsLast))
      qualifiers +:= "last"
    if (boolParam(IsLazy))
      qualifiers +:= "lazy"
    if (boolParam(IsOptional))
      qualifiers +:= "optional"
    if (boolParam(IsVariadic))
      qualifiers +:= "variadic"
    val qualifierString = qualifiers match {
      case Seq() ⇒ ""
      case _     ⇒ qualifiers.mkString(" [", ", ", "]")
    }
    val name = MashNull.option(param(Name)) getOrElse Parameter.AnonymousParamName
    val paramName = paramNameStyle("" + (if (isFlag) "--" + param(Name) else name))
    val shortFlagDescription = paramNameStyle(MashNull.option(param(ShortFlag)).map(f ⇒ s" | -$f").getOrElse(""))
    val summaryOpt = MashNull.option(param(Summary))
    output.println(paramName + shortFlagDescription + qualifierString + summaryOpt.fold("")(" - " + _))
    for (description ← MashNull.option(param(Description)).map(_.asInstanceOf[MashString]))
      output.println(indent(description.s, indentAmount * 2))
    output.println()
  }

  def printClassHelp(obj: MashObject) {
    def fieldMethodStyle(s: Any) = Ansi.ansi().bold().fg(Color.BLUE).a("" + s).boldOff().fg(Color.DEFAULT).toString
    import ClassHelpClass.Fields._
    output.println(bold("CLASS"))
    val summaryOpt = MashNull.option(obj(Summary))
    output.println(indentSpace + bold(obj(FullyQualifiedName)) + summaryOpt.fold("")(" - " + _))
    for (description ← MashNull.option(obj(Description)).map(_.asInstanceOf[MashString])) {
      output.println()
      output.println(bold("DESCRIPTION"))
      output.println(indent(description.s, indentAmount))
    }
    for (parent ← MashNull.option(obj(Parent)).map(_.asInstanceOf[MashString])) {
      output.println()
      output.println(bold("PARENT"))
      output.println(indentSpace + obj(Parent))
    }
    val fields = obj(Fields).asInstanceOf[MashList]
    if (fields.nonEmpty) {
      output.println()
      output.println(bold("FIELDS"))
      for (field ← fields) {
        val fieldObject = field.asInstanceOf[MashObject]
        output.print(indentSpace)
        output.print(fieldMethodStyle(fieldObject(FieldHelpClass.Fields.Name)))
        for (summary ← MashNull.option(fieldObject(FieldHelpClass.Fields.Summary)))
          output.print(" - " + summary)
        output.println()
      }
    }
    val methods = obj(Methods).asInstanceOf[MashList].elements
    if (methods.nonEmpty) {
      output.println()
      output.println(bold("METHODS"))
      for (method ← methods) {
        val methodObject = method.asInstanceOf[MashObject]
        output.print(indentSpace)
        output.print(fieldMethodStyle(methodObject(FieldHelpClass.Fields.Name)))
        for (summary ← MashNull.option(methodObject(FieldHelpClass.Fields.Summary)))
          output.print(" - " + summary)
        output.println()
      }
    }

  }

}