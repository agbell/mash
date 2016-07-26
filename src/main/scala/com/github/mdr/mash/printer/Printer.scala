package com.github.mdr.mash.printer

import java.io.PrintStream
import java.time.Instant
import java.util.Date

import org.ocpsoft.prettytime.PrettyTime

import com.github.mdr.mash.evaluator.BoundMethod
import com.github.mdr.mash.evaluator.MashClass
import com.github.mdr.mash.evaluator.ToStringifier
import com.github.mdr.mash.functions.MashFunction
import com.github.mdr.mash.ns.core.BytesClass
import com.github.mdr.mash.ns.core.help.ClassHelpClass
import com.github.mdr.mash.ns.core.help.FieldHelpClass
import com.github.mdr.mash.ns.core.help.FunctionHelpClass
import com.github.mdr.mash.ns.core.help.HelpFunction
import com.github.mdr.mash.ns.git.StatusClass
import com.github.mdr.mash.ns.os.PermissionsClass
import com.github.mdr.mash.ns.os.PermissionsSectionClass
import com.github.mdr.mash.ns.time.MillisecondsClass
import com.github.mdr.mash.ns.time.SecondsClass
import com.github.mdr.mash.ns.view.ViewClass
import com.github.mdr.mash.runtime._
import com.github.mdr.mash.terminal.TerminalInfo
import com.github.mdr.mash.utils.NumberUtils
import com.github.mdr.mash.utils.StringUtils

object Printer {

  private val prettyTime = new PrettyTime

  def replaceProblematicChars(s: String): String = s.map {
    case '\t' | '\n' | '\r' | '\b' ⇒ ' '
    case c                         ⇒ c
  }

  def renderField(x: MashValue, inCell: Boolean = false): String = x match {
    case mo: MashObject if mo.classOpt == Some(PermissionsClass) ⇒ PermissionsPrinter.permissionsString(mo)
    case mo: MashObject if mo.classOpt == Some(PermissionsSectionClass) ⇒ PermissionsPrinter.permissionsSectionString(mo)
    case MashNumber(n, Some(BytesClass)) ⇒ BytesPrinter.humanReadable(n)
    case MashNumber(n, Some(MillisecondsClass)) ⇒ NumberUtils.prettyString(n) + "ms"
    case MashNumber(n, Some(SecondsClass)) ⇒ NumberUtils.prettyString(n) + "s"
    case MashNumber(n, _) ⇒ NumberUtils.prettyString(n)
    case MashWrapped(i: Instant) ⇒ prettyTime.format(Date.from(i))
    case xs: MashList if inCell ⇒ xs.items.map(renderField(_)).mkString(", ")
    case xs: MashList ⇒ xs.items.map(renderField(_)).mkString("[", ", ", "]")
    case _ ⇒ Printer.replaceProblematicChars(ToStringifier.stringify(x))
  }

}

case class PrintResult(objectTableModelOpt: Option[ObjectTableModel] = None)

class Printer(output: PrintStream, terminalInfo: TerminalInfo) {

  private val helpPrinter = new HelpPrinter(output)

  def print(x: MashValue, disableCustomViews: Boolean = false, alwaysUseBrowser: Boolean = false): PrintResult = {
    x match {
      case xs: MashList if xs.nonEmpty && xs.forall(_.isInstanceOf[MashObject]) ⇒
        val objects = xs.items.asInstanceOf[Seq[MashObject]]
        val nonDataRows = 4 // 3 header rows + 1 footer
        if (alwaysUseBrowser || objects.size > terminalInfo.rows - nonDataRows) {
          val model = new ObjectTableModelCreator(terminalInfo, showSelections = true).create(objects)
          return PrintResult(Some(model))
        } else
          new ObjectTablePrinter(output, terminalInfo).printTable(objects)
      case xs: MashList if xs.nonEmpty && xs.forall(x ⇒ x == MashNull || x.isInstanceOf[MashString]) ⇒
        xs.items.foreach(output.println)
      case mo: MashObject if mo.classOpt == Some(ViewClass) ⇒
        val data = mo(ViewClass.Fields.Data)
        val disableCustomViews = mo(ViewClass.Fields.DisableCustomViews) == MashBoolean.True
        val alwaysUseBrowser = mo(ViewClass.Fields.UseBrowser) == MashBoolean.True
        return print(data, disableCustomViews = disableCustomViews, alwaysUseBrowser = alwaysUseBrowser)
      case mo: MashObject if mo.classOpt == Some(FunctionHelpClass) && !disableCustomViews ⇒
        helpPrinter.printFunctionHelp(mo)
      case mo: MashObject if mo.classOpt == Some(FieldHelpClass) && !disableCustomViews ⇒
        helpPrinter.printFieldHelp(mo)
      case mo: MashObject if mo.classOpt == Some(ClassHelpClass) && !disableCustomViews ⇒
        helpPrinter.printClassHelp(mo)
      case mo: MashObject if mo.classOpt == Some(StatusClass) && !disableCustomViews ⇒
        new GitStatusPrinter(output).print(mo)
      case mo: MashObject ⇒ new ObjectPrinter(output, terminalInfo).printObject(mo)
      case xs: MashList if xs.nonEmpty && xs.forall(_ == ((): Unit)) ⇒ // Don't print out sequence of unit
      case f: MashFunction if !disableCustomViews ⇒
        print(HelpFunction.getHelp(f), disableCustomViews = disableCustomViews, alwaysUseBrowser = alwaysUseBrowser)
      case bm: BoundMethod if !disableCustomViews ⇒
        print(HelpFunction.getHelp(bm), disableCustomViews = disableCustomViews, alwaysUseBrowser = alwaysUseBrowser)
      case klass: MashClass if !disableCustomViews ⇒
        print(HelpFunction.getHelp(klass), disableCustomViews = disableCustomViews, alwaysUseBrowser = alwaysUseBrowser)
      case MashUnit ⇒ // Don't print out Unit 
      case _ ⇒
        val f = ToStringifier.safeStringify(x)
        //        val f = StringUtils.ellipsisise(Printer.renderField(x), maxLength = terminalInfo.columns)
        output.println(f)
    }
    PrintResult()
  }

  def printBox(title: String, lines: Seq[String]) {
    val boxWidth = math.min(math.max(lines.map(_.size + 4).max, title.size + 4), terminalInfo.columns)
    val innerWidth = boxWidth - 4
    val displayTitle = " " + StringUtils.ellipsisise(title, innerWidth) + " "
    val displayLines = lines.map(l ⇒ StringUtils.ellipsisise(l, innerWidth))
    val topLine = "┌─" + displayTitle + "─" * (innerWidth - displayTitle.length) + "─┐"
    val bottomLine = "└─" + "─" * innerWidth + "─┘"
    val contentLines = displayLines.map(l ⇒ "│ " + l + " " * (innerWidth - l.length) + " │")
    for (line ← (topLine +: contentLines :+ bottomLine))
      output.println(line)
  }

}