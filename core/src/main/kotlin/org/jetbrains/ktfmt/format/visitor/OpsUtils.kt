package org.jetbrains.ktfmt.format.visitor

import com.google.googlejavaformat.Doc
import com.google.googlejavaformat.Doc.Level
import com.google.googlejavaformat.OpsBuilder
import com.google.googlejavaformat.Output
import java.util.Optional
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.ktfmt.format.FenceCommentsOp
import org.jetbrains.ktfmt.format.visitor.Indentation.Companion.ZERO

/** Helper method to sync the current offset to match any element in the AST */
internal fun OpsBuilder.sync(psiElement: PsiElement) {
  sync(psiElement.startOffset)
}

/** Prevent subsequent comments from being moved ahead of this point, into parent [Level]s. */
internal fun OpsBuilder.fenceComments() {
  addAll(FenceCommentsOp.AS_LIST)
}

/**
 * Emit a [Doc.Token].
 *
 * @param token the [String] to wrap in a [Doc.Token]
 * @param plusIndentCommentsBefore extra block for comments before this token
 */
internal fun OpsBuilder.token(token: String, plusIndentCommentsBefore: Indentation = ZERO) {
  token(
      token,
      Doc.Token.RealOrImaginary.REAL,
      plusIndentCommentsBefore.indent,
      /* breakAndIndentTrailingComment */ Optional.empty(),
  )
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
    plusIndent: Indentation = ZERO,
    isEnabled: Boolean = true,
    block: () -> Unit,
) {
  if (isEnabled) {
    open(plusIndent.indent)
  }
  block()
  if (isEnabled) {
    close()
  }
}

internal fun OpsBuilder.breakOp(
    fillMode: Doc.FillMode = Doc.FillMode.UNIFIED,
    flat: String = " ",
    plusIndent: Indentation = ZERO,
    optionalTag: Optional<Output.BreakTag> = Optional.empty(),
) = breakOp(fillMode, flat, plusIndent.indent, optionalTag)

internal fun OpsBuilder.open(plusIndent: Indentation) = open(plusIndent.indent)

internal fun OpsBuilder.forcedBreak(plusIndent: Indentation) = forcedBreak(plusIndent.indent)
