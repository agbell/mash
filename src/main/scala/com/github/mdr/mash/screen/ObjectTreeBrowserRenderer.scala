package com.github.mdr.mash.screen

import com.github.mdr.mash.os.linux.LinuxFileSystem
import com.github.mdr.mash.printer.ObjectTreeNode
import com.github.mdr.mash.repl.{ ObjectTreeBrowserState, ObjectTreePath }
import com.github.mdr.mash.screen.Style.StylableString
import com.github.mdr.mash.terminal.TerminalInfo

import scala.collection.mutable.ArrayBuffer

case class ObjectTreeBrowserRenderer(state: ObjectTreeBrowserState, terminalInfo: TerminalInfo) {

  private val fileSystem = LinuxFileSystem

  def renderObjectBrowser: Screen = {
    val lines = renderLines
    val title = "mash " + fileSystem.pwd.toString
    Screen(lines, cursorPos = Point(0, 0), cursorVisible = false, title = title)
  }

  private def renderLines: Seq[Line] = {
    val printer = new Printer
    print(printer, state.model.root, prefix = "", currentPath = ObjectTreePath(Seq()))
    printer.getLines.drop(state.firstRow).take(windowSize) :+ renderStatusLine
  }

  private class Printer() {

    private val lines: ArrayBuffer[Line] = ArrayBuffer()

    private var line: Seq[StyledCharacter] = Seq()

    def println(): Unit = {
      lines += Line(line)
      line = Seq()
    }

    def println(s: String): Unit = {
      line ++= s.style
      println()
    }

    def print(s: String, highlighted: Boolean = false, yellow: Boolean = false): Unit = {
      val foregroundColour = if (yellow) Colour.Yellow else Colour.Default
      line ++= s.style(Style(inverse = highlighted, foregroundColour = foregroundColour))
    }

    def getLines = lines :+ Line(line)

  }

  private def renderStatusLine: Line = {
    import KeyHint._
    val hints = Seq(Exit, Back, Table)
    Line(s"${state.path} (".style ++ renderKeyHints(hints) ++ ")".style)
  }

  private def print(printer: Printer, node: ObjectTreeNode, prefix: String, currentPath: ObjectTreePath): Unit = node match {
    case ObjectTreeNode.List(Seq(), _)    => printer.println("[]")
    case ObjectTreeNode.List(items, _)    => printList(printer, items, prefix, currentPath, connectUp = true)
    case ObjectTreeNode.Object(Seq(), _)  => printer.println("{}")
    case ObjectTreeNode.Object(values, _) => printObject(printer, values, prefix, currentPath, connectUp = true)
    case ObjectTreeNode.Leaf(value, _)    => printer.println(value)
  }

  private def printList(printer: Printer, nodes: Seq[ObjectTreeNode], prefix: String, currentPath: ObjectTreePath, connectUp: Boolean) {
    for ((node, index) <- nodes.zipWithIndex) {
      val itemPath = currentPath.descend(index)
      val isLastNode = index == nodes.length - 1
      val (nodePrefix, connector) = getPrefixAndConnector(index, nodes, connectUp, prefix)
      printer.print(nodePrefix)
      printer.print(connector, highlighted = itemPath == state.selectionPath)
      val nestingPrefix = prefix + (if (isLastNode) "   " else "│  ")
      node match {
        case ObjectTreeNode.Leaf(value, _)      =>
          printer.print("─ ")
          printer.print(value, highlighted = itemPath.ontoValue == state.selectionPath)
          printer.println()
        case ObjectTreeNode.List(Seq(), _)      =>
          printer.print("─ ")
          printer.print("[]", highlighted = itemPath.ontoValue == state.selectionPath)
          printer.println()
        case ObjectTreeNode.Object(Seq(), _)    =>
          printer.print("─ ")
          printer.print("{}", highlighted = itemPath.ontoValue == state.selectionPath)
          printer.println()
        case ObjectTreeNode.List(childNodes, _) =>
          printer.print(s"──")
          printList(printer, childNodes, nestingPrefix, itemPath, connectUp = false)
        case ObjectTreeNode.Object(values, _)   =>
          printer.print(s"──")
          printObject(printer, values, nestingPrefix, itemPath, connectUp = false)
      }
    }
  }

  private def printObject(printer: Printer, nodes: Seq[(String, ObjectTreeNode)], prefix: String, currentPath: ObjectTreePath, connectUp: Boolean) {
    for (((field, node), index) <- nodes.zipWithIndex) {
      val itemPath = currentPath.descend(field)
      val isLastNode = index == nodes.length - 1
      val (nodePrefix, connector) = getPrefixAndConnector(index, nodes.map(_._2), connectUp, prefix)
      printer.print(nodePrefix)
      printer.print(connector, highlighted = itemPath == state.selectionPath)
      val fieldPath = itemPath.ontoFieldLabel
      printer.print("─ ")
      printer.print(field, yellow = true, highlighted = fieldPath == state.selectionPath)
      printer.print(":")
      val nestingPrefix = prefix + (if (isLastNode) "   " else "│  ")
      node match {
        case ObjectTreeNode.Leaf(value, _)      =>
          printer.print(" ")
          printer.print(value, highlighted = fieldPath.ontoValue == state.selectionPath)
          printer.println()
        case ObjectTreeNode.List(Seq(), _)      =>
          printer.print(" ")
          printer.print("[]", highlighted = fieldPath.ontoValue == state.selectionPath)
          printer.println()
        case ObjectTreeNode.Object(Seq(), _)    =>
          printer.print(" ")
          printer.print("{}", highlighted = fieldPath.ontoValue == state.selectionPath)
          printer.println()
        case ObjectTreeNode.List(childNodes, _) =>
          printer.println()
          printer.print(nestingPrefix)
          printList(printer, childNodes, nestingPrefix, fieldPath, connectUp = true)
        case ObjectTreeNode.Object(values, _)   =>
          printer.println()
          printer.print(nestingPrefix)
          printObject(printer, values, nestingPrefix, fieldPath, connectUp = true)
      }
    }
  }

  private def getPrefixAndConnector(index: Int, nodes: Seq[ObjectTreeNode], connectUp: Boolean, prefix: String): (String, String) = {
    val isFirstNode = index == 0
    val isLastNode = index == nodes.length - 1
    if (isFirstNode) {
      val connector =
        if (prefix.isEmpty) {
          if (nodes.size > 1) "┌" else "─"
        } else {
          if (connectUp) {
            if (isLastNode) "└" else "├"
          } else {
            if (isLastNode) "─" else "┬"
          }
        }
      ("", connector)
    } else {
      (prefix, if (isLastNode) "└" else "├")
    }
  }

  private def windowSize = terminalInfo.rows - 1 // a status line

}
