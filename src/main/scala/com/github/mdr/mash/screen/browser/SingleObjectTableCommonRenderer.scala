package com.github.mdr.mash.screen.browser

import com.github.mdr.mash.printer.UnicodeBoxCharacterSupplier
import com.github.mdr.mash.printer.model.SingleObjectTableModel
import com.github.mdr.mash.repl.browser.TwoDTableBrowserState.SearchState
import com.github.mdr.mash.screen.Style.StylableString
import com.github.mdr.mash.screen._
import com.github.mdr.mash.utils.Utils._
import com.github.mdr.mash.utils.{ Region, StringUtils }

import scala.collection.mutable.ArrayBuffer

class SingleObjectTableCommonRenderer(model: SingleObjectTableModel,
                                      selectedIndexOpt: Option[Int] = None,
                                      moreDataItemsAboveWindow: Boolean = false,
                                      moreDataItemsBelowWindow: Boolean = false,
                                      searchStateOpt: Option[SearchState] = None) {

  private val searchHitsByPoint = searchStateOpt.map(_.byPoint).getOrElse(Map())

  private val boxChars = UnicodeBoxCharacterSupplier

  def renderTableLines(rowOffset: Int = 0, rowCount: Int = model.numberOfRows): Seq[Line] =
    renderHeaderLines(moreDataItemsAboveWindow) ++
      renderFieldLines(rowOffset, rowCount) ++
      Seq(renderFooterLine(break = hasFields, addArrow = moreDataItemsBelowWindow))

  private def renderHeaderLines(moreDataItemsAboveWindow: Boolean): Seq[Line] =
    model.classNameOpt match {
      case Some(className) ⇒ renderClassHeaderLines(className, moreDataItemsAboveWindow)
      case None            ⇒ Seq(renderTopRow(break = hasFields, addArrow = moreDataItemsAboveWindow))
    }

  private def renderClassHeaderLines(className: String, moreDataItemsAboveWindow: Boolean): Seq[Line] = {
    val topLine = renderTopRow(break = false, addArrow = false)
    val classNameLine = renderClassNameLine(className)
    val belowHeaderLineOpt =
      if (model.fields.isEmpty)
        None
      else
        Some(renderBelowHeaderLine(addArrow = moreDataItemsAboveWindow))
    Seq(topLine, classNameLine) ++ belowHeaderLineOpt
  }

  /**
    * ╔══════════════════════════╗
    * or
    * ╔════════════╤═════════════╗
    */
  private def renderTopRow(break: Boolean = true, addArrow: Boolean): Line = {
    import boxChars._
    val chars = new StringBuilder()
      .append(doubleTopLeft)
      .append(doubleHorizontal * model.fieldColumnWidth)
      .append(if (break) doubleHorizontalSingleDown else doubleHorizontal)
      .append(doubleHorizontal * model.valueColumnWidth)
      .append(doubleTopRight)
      .toString
    Line(chars.when(addArrow, addUpArrow).style)
  }

  /**
    * ║           Class          ║
    **/
  private def renderClassNameLine(className: String): Line = {
    val fit = StringUtils.ellipsisise(StringUtils.centre(className, fullRowWidth), fullRowWidth)
    val renderedClassName = fit.style(classNameStyle)
    val buffer = ArrayBuffer[StyledCharacter]()
    buffer ++= boxChars.doubleVertical.style.chars
    buffer ++= renderedClassName.chars
    buffer ++= boxChars.doubleVertical.style.chars
    Line(StyledString(buffer))
  }

  /**
    * ╟────────────┬─────────────╢
    */
  private def renderBelowHeaderLine(addArrow: Boolean): Line = {
    import boxChars._
    val chars = new StringBuilder()
      .append(doubleVerticalSingleRight)
      .append(singleHorizontal * model.fieldColumnWidth)
      .append(singleHorizontalSingleDown)
      .append(singleHorizontal * model.valueColumnWidth)
      .append(doubleVerticalSingleLeft)
      .toString
    Line(chars.when(addArrow, addUpArrow).style)
  }

  private def renderFieldLines(rowOffset: Int, rowCount: Int): Seq[Line] =
    for {
      ((renderedField, renderedValue), row) ← model.fields.toSeq.zipWithIndex.window(rowOffset, rowCount)
      isCursorRow = selectedIndexOpt contains row
      fieldSearchHitRegions = searchHitsByPoint.get(Point(row, 0)).map(_.matches).getOrElse(Seq())
      valueSearchHitRegions = searchHitsByPoint.get(Point(row, 1)).map(_.matches).getOrElse(Seq())
    } yield renderFieldLine(renderedField, renderedValue, isCursorRow, fieldSearchHitRegions, valueSearchHitRegions)

  /**
    * ║fieldName   │fieldValue   ║
    */
  private def renderFieldLine(renderedField: String,
                              renderedValue: String,
                              isCursorRow: Boolean,
                              fieldSearchHitRegions: Seq[Region],
                              valueSearchHitRegions: Seq[Region]): Line = {
    val side = boxChars.doubleVertical.style
    val internalVertical = boxChars.singleVertical.style(internalRowStyle(isCursorRow))
    val fieldChars = renderFieldCell(renderedField, isCursorRow, fieldSearchHitRegions)
    val valueChars = renderValueCell(renderedValue, isCursorRow, valueSearchHitRegions)
    Line(side + fieldChars + internalVertical + valueChars + side)
  }

  private def renderFieldCell(renderedField: String, isCursorRow: Boolean, searchHitRegions: Seq[Region]): StyledString = {
    val chars = StringUtils.fitToWidth(renderedField, model.fieldColumnWidth)
    val buf = ArrayBuffer[StyledCharacter]()
    for ((c, offset) <- chars.zipWithIndex) {
      val isSearchMatch = searchHitRegions exists (_ contains offset)
      val style = fieldStyle(isCursorRow, isSearchMatch)
      buf += StyledCharacter(c, style)
    }
    StyledString(buf)
  }

  private def renderValueCell(renderedValue: String, isCursorRow: Boolean, searchHitRegions: Seq[Region]): StyledString = {
    val chars = StringUtils.fitToWidth(renderedValue, model.valueColumnWidth)
    val buf = ArrayBuffer[StyledCharacter]()
    for ((c, offset) <- chars.zipWithIndex) {
      val isSearchMatch = searchHitRegions exists (_ contains offset)
      val style = internalRowStyle(isCursorRow, isSearchMatch)
      buf += StyledCharacter(c, style)
    }
    StyledString(buf)
  }

  /**
    * ╚════════════╧═════════════╝
    * or
    * ╚══════════════════════════╝
    */
  private def renderFooterLine(break: Boolean, addArrow: Boolean): Line = {
    import boxChars._
    val chars = new StringBuilder()
      .append(doubleBottomLeft)
      .append(doubleHorizontal * model.fieldColumnWidth)
      .append(if (break) doubleHorizontalSingleUp else doubleHorizontal)
      .append(doubleHorizontal * model.valueColumnWidth)
      .append(doubleBottomRight)
      .toString
    Line(chars.when(addArrow, addDownArrow).style)
  }

  private def addUpArrow(s: String): String = setMiddleCharacter(s, '↑')

  private def addDownArrow(s: String): String = setMiddleCharacter(s, '↓')

  private def setMiddleCharacter(s: String, c: Char): String =
    if (s.isEmpty) s else s.updated(s.length / 2, c)

  private def fullRowWidth = model.fieldColumnWidth + model.valueColumnWidth + 1

  private def hasFields: Boolean = model.fields.nonEmpty

  private val classNameStyle: Style = Style(bold = true, foregroundColour = BasicColour.Yellow)

  private def internalRowStyle(isCursorRow: Boolean, isSearchHit: Boolean = false): Style =
    Style(inverse = isCursorRow, foregroundColour = if (isSearchHit) BasicColour.Cyan else DefaultColour)

  private def fieldStyle(isCursorRow: Boolean, isSearchHit: Boolean): Style =
    Style(inverse = isCursorRow, foregroundColour = if (isSearchHit) BasicColour.Cyan else BasicColour.Yellow)

}
