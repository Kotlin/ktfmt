package com.facebook.ktfmt.format

import com.google.googlejavaformat.Doc
import com.google.googlejavaformat.Doc.Level
import com.google.googlejavaformat.Doc.Token
import com.google.googlejavaformat.Indent
import com.google.googlejavaformat.Indent.Const.ZERO
import com.google.googlejavaformat.OpsBuilder
import com.google.googlejavaformat.Output.BreakTag
import java.util.Optional
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.psiUtil.startOffset

/**
 * Emit a [Doc.Token].
 *
 * @param token the [String] to wrap in a [Doc.Token]
 * @param plusIndentCommentsBefore extra block for comments before this token
 */
internal fun OpsBuilder.token(token: String, plusIndentCommentsBefore: Indent = ZERO) {
  token(
      token,
      Token.RealOrImaginary.REAL,
      plusIndentCommentsBefore,
      /* breakAndIndentTrailingComment */ Optional.empty(),
  )
}

/** Emit a [Doc.Token] followed by a [Doc.Space]. */
internal fun OpsBuilder.tokenThenSpace(token: String) {
  token(token)
  space()
}

/** Emit a [Doc.Space] followed by a [Doc.Token]. */
internal fun OpsBuilder.spaceThenToken(token: String) {
  space()
  token(token)
}

/** Emit a [Doc.Token] surrounded by [Doc.Space]s. */
internal fun OpsBuilder.spacedToken(token: String) {
  space()
  token(token)
  space()
}

/**
 * Emit a [Doc.Break] with a specified [flat] value and extra indent.
 *
 * [OpsBuilder] only provides overloads that set either [flat] or [plusIndent], but not both.
 *
 * @param flat the [Doc.Break] when not broken
 * @param plusIndent extra indent if taken
 * @param optionalTag an optional tag for remembering whether the break was taken
 */
internal fun OpsBuilder.breakOp(
    flat: String,
    plusIndent: Indent,
    optionalTag: Optional<BreakTag> = Optional.empty(),
) {
  breakOp(Doc.FillMode.UNIFIED, flat, plusIndent, optionalTag)
}

/**
 * Emit a filled [Doc.Break] with extra indent.
 *
 * @param plusIndent extra indent if taken
 */
internal fun OpsBuilder.breakToFill(plusIndent: Indent) {
  breakOp(Doc.FillMode.INDEPENDENT, "", plusIndent)
}

/**
 * Emit a filled [Doc.Break] with a specified [flat] value and extra indent.
 *
 * @param flat the [Doc.Break] when not broken
 * @param plusIndent extra indent if taken
 * @param optionalTag an optional tag for remembering whether the break was taken
 */
internal fun OpsBuilder.breakToFill(
    flat: String,
    plusIndent: Indent,
    optionalTag: Optional<BreakTag> = Optional.empty(),
) {
  breakOp(Doc.FillMode.INDEPENDENT, flat, plusIndent, optionalTag)
}

/**
 * Opens a new level, emits into it and closes it.
 *
 * This is a helper method to make it easier to keep track of [OpsBuilder.open] and
 * [OpsBuilder.close] calls
 *
 * @param plusIndent the block level to pass to the block
 * @param block a code block to be run in this block level
 */
internal fun OpsBuilder.block(
    plusIndent: Indent = ZERO,
    isEnabled: Boolean = true,
    block: () -> Unit,
) {
  if (isEnabled) {
    open(plusIndent)
  }
  block()
  if (isEnabled) {
    close()
  }
}

/**
 * Emit a [Doc.Break], then open a level indented by the same amount, emit into it and close it.
 *
 * Breaking and then indenting the continuation by the same [plusIndent] is a very common
 * combination, and stating the indent once keeps the two from drifting apart.
 *
 * @param plusIndent extra indent if the break is taken, and the indent of the level
 * @param block a code block to be run in this block level
 */
internal fun OpsBuilder.breakOpThenBlock(plusIndent: Indent, block: () -> Unit) {
  breakOpThenBlock("", plusIndent, block)
}

/**
 * Emit a [Doc.Break] with a specified [flat] value, then open a level indented by the same amount,
 * emit into it and close it.
 *
 * @param flat the [Doc.Break] when not broken
 * @param plusIndent extra indent if the break is taken, and the indent of the level
 * @param block a code block to be run in this block level
 */
internal fun OpsBuilder.breakOpThenBlock(flat: String, plusIndent: Indent, block: () -> Unit) {
  breakOp(flat, plusIndent)
  block(plusIndent, block = block)
}

/**
 * Emit a filled [Doc.Break] with a specified [flat] value, then open a level indented by the same
 * amount, emit into it and close it.
 *
 * @param flat the [Doc.Break] when not broken
 * @param plusIndent extra indent if the break is taken, and the indent of the level
 * @param block a code block to be run in this block level
 */
internal fun OpsBuilder.breakToFillThenBlock(flat: String, plusIndent: Indent, block: () -> Unit) {
  breakToFill(flat, plusIndent)
  block(plusIndent, block = block)
}

/** Emit a `;` if the input has one at this point. */
internal fun OpsBuilder.guessSemicolon() {
  guessToken(";")
}

/** Helper method to sync the current offset to match any element in the AST */
internal fun OpsBuilder.sync(psiElement: PsiElement) {
  sync(psiElement.startOffset)
}

/** Prevent subsequent comments from being moved ahead of this point, into parent [Level]s. */
internal fun OpsBuilder.fenceComments() {
  addAll(FenceCommentsOp.AS_LIST)
}
