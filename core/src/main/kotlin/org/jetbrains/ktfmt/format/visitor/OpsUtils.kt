package org.jetbrains.ktfmt.format.visitor

import com.google.googlejavaformat.Doc
import com.google.googlejavaformat.Doc.Level
import com.google.googlejavaformat.Doc.Token
import com.google.googlejavaformat.Indent
import com.google.googlejavaformat.Indent.Const.ZERO
import com.google.googlejavaformat.OpsBuilder
import java.util.Optional
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.ktfmt.format.FenceCommentsOp

/** Helper method to sync the current offset to match any element in the AST */
fun OpsBuilder.sync(psiElement: PsiElement) {
  sync(psiElement.startOffset)
}

/** Prevent subsequent comments from being moved ahead of this point, into parent [Level]s. */
fun OpsBuilder.fenceComments() {
  addAll(FenceCommentsOp.AS_LIST)
}

/**
 * Emit a [Doc.Token].
 *
 * @param token the [String] to wrap in a [Doc.Token]
 * @param plusIndentCommentsBefore extra block for comments before this token
 */
fun OpsBuilder.token(token: String, plusIndentCommentsBefore: Indent = ZERO) {
  token(
      token,
      Doc.Token.RealOrImaginary.REAL,
      plusIndentCommentsBefore,
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
fun OpsBuilder.block(
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

val Int.asIndent: Indent.Const
  get() = Indent.Const.make(this, 1)
