package com.pinterest.ktlint.ruleset.standard.rules

import com.pinterest.ktlint.rule.engine.core.api.ElementType
import com.pinterest.ktlint.rule.engine.core.api.ElementType.ARROW
import com.pinterest.ktlint.rule.engine.core.api.ElementType.CLASS
import com.pinterest.ktlint.rule.engine.core.api.ElementType.DESTRUCTURING_DECLARATION
import com.pinterest.ktlint.rule.engine.core.api.ElementType.FUNCTION_LITERAL
import com.pinterest.ktlint.rule.engine.core.api.ElementType.FUNCTION_TYPE
import com.pinterest.ktlint.rule.engine.core.api.ElementType.TYPE_PARAMETER_LIST
import com.pinterest.ktlint.rule.engine.core.api.ElementType.VALUE_PARAMETER_LIST
import com.pinterest.ktlint.rule.engine.core.api.ElementType.WHEN_ENTRY
import com.pinterest.ktlint.rule.engine.core.api.ElementType.WHITE_SPACE
import com.pinterest.ktlint.rule.engine.core.api.Rule.VisitorModifier.RunAfterRule.Mode.ONLY_WHEN_RUN_AFTER_RULE_IS_LOADED_AND_ENABLED
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.children
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.EditorConfig
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.EditorConfigProperty
import com.pinterest.ktlint.rule.engine.core.api.indent
import com.pinterest.ktlint.rule.engine.core.api.isCodeLeaf
import com.pinterest.ktlint.rule.engine.core.api.noNewLineInClosedRange
import com.pinterest.ktlint.rule.engine.core.api.prevCodeLeaf
import com.pinterest.ktlint.rule.engine.core.api.prevLeaf
import com.pinterest.ktlint.rule.engine.core.api.upsertWhitespaceAfterMe
import com.pinterest.ktlint.rule.engine.core.util.cast
import com.pinterest.ktlint.ruleset.standard.StandardRule
import org.ec4j.core.model.PropertyType
import org.ec4j.core.model.PropertyType.PropertyValueParser
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.com.intellij.psi.tree.TokenSet
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.nextLeaf
import org.jetbrains.kotlin.psi.psiUtil.prevLeaf

/**
 * Linting trailing comma for declaration site.
 *
 * @see [Kotlin Style Guide](https://kotlinlang.org/docs/coding-conventions.html#trailing-commas)
 */
public class TrailingCommaOnDeclarationSiteRule :
    StandardRule(
        id = "trailing-comma-on-declaration-site",
        visitorModifiers =
            setOf(
                VisitorModifier.RunAfterRule(
                    ruleId = WRAPPING_RULE_ID,
                    mode = ONLY_WHEN_RUN_AFTER_RULE_IS_LOADED_AND_ENABLED,
                ),
                VisitorModifier.RunAsLateAsPossible,
            ),
        usesEditorConfigProperties = setOf(TRAILING_COMMA_ON_DECLARATION_SITE_PROPERTY),
    ) {
    private var allowTrailingComma = TRAILING_COMMA_ON_DECLARATION_SITE_PROPERTY.defaultValue

    override fun beforeFirstNode(editorConfig: EditorConfig) {
        allowTrailingComma = editorConfig[TRAILING_COMMA_ON_DECLARATION_SITE_PROPERTY]
    }

    override fun beforeVisitChildNodes(
        node: ASTNode,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
    ) {
        // Keep processing of element types in sync with Intellij Kotlin formatting settings.
        // https://github.com/JetBrains/intellij-kotlin/blob/master/formatter/src/org/jetbrains/kotlin/idea/formatter/trailingComma/util.kt
        when (node.elementType) {
            CLASS -> visitClass(node, emit, autoCorrect)
            DESTRUCTURING_DECLARATION -> visitDestructuringDeclaration(node, autoCorrect, emit)
            FUNCTION_LITERAL -> visitFunctionLiteral(node, autoCorrect, emit)
            TYPE_PARAMETER_LIST -> visitTypeList(node, autoCorrect, emit)
            VALUE_PARAMETER_LIST -> visitValueList(node, autoCorrect, emit)
            WHEN_ENTRY -> visitWhenEntry(node, autoCorrect, emit)
            else -> Unit
        }
    }

    private fun visitDestructuringDeclaration(
        node: ASTNode,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
    ) {
        val inspectNode =
            node
                .children()
                .last { it.elementType == ElementType.RPAR }
        node.reportAndCorrectTrailingCommaNodeBefore(
            inspectNode = inspectNode,
            isTrailingCommaAllowed = node.isTrailingCommaAllowed(),
            autoCorrect = autoCorrect,
            emit = emit,
        )
    }

    private fun ASTNode.isTrailingCommaAllowed() = elementType in TYPES_ON_DECLARATION_SITE && allowTrailingComma

    private fun visitFunctionLiteral(
        node: ASTNode,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
    ) {
        val inspectNode =
            node
                .children()
                .lastOrNull { it.elementType == ARROW }
                ?: // lambda w/o an arrow -> no arguments -> no commas
                return
        node.reportAndCorrectTrailingCommaNodeBefore(
            inspectNode = inspectNode,
            isTrailingCommaAllowed = node.isTrailingCommaAllowed(),
            autoCorrect = autoCorrect,
            emit = emit,
        )
    }

    private fun visitValueList(
        node: ASTNode,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
    ) {
        if (node.treeParent.elementType != FUNCTION_LITERAL) {
            node
                .children()
                .lastOrNull { it.elementType == ElementType.RPAR }
                ?.let { inspectNode ->
                    node.reportAndCorrectTrailingCommaNodeBefore(
                        inspectNode = inspectNode,
                        isTrailingCommaAllowed = node.isTrailingCommaAllowed(),
                        autoCorrect = autoCorrect,
                        emit = emit,
                    )
                }
        }
    }

    private fun visitTypeList(
        node: ASTNode,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
    ) {
        val inspectNode =
            node
                .children()
                .first { it.elementType == ElementType.GT }
        node.reportAndCorrectTrailingCommaNodeBefore(
            inspectNode = inspectNode,
            isTrailingCommaAllowed = node.isTrailingCommaAllowed(),
            autoCorrect = autoCorrect,
            emit = emit,
        )
    }

    private fun visitWhenEntry(
        node: ASTNode,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
    ) {
        val psi = node.psi
        require(psi is KtWhenEntry)
        if (psi.isElse || psi.parent.cast<KtWhenExpression>().leftParenthesis == null) {
            // no commas for "else" or when there are no opening parenthesis for the when-expression
            return
        }

        val inspectNode =
            node
                .children()
                .first { it.elementType == ARROW }
        node.reportAndCorrectTrailingCommaNodeBefore(
            inspectNode = inspectNode,
            isTrailingCommaAllowed = node.isTrailingCommaAllowed(),
            autoCorrect = autoCorrect,
            emit = emit,
        )
    }
    private fun visitClass(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
        autoCorrect: Boolean,
    ) {
        val psi = node.psi
        require(psi is KtClass)

        node
            .takeIf { psi.isEnum() }
            ?.findChildByType(ElementType.CLASS_BODY)
            ?.takeUnless {
                it.noEnumEntries() || it.lastTwoEnumEntriesAreOnSameLine()
            }?.let { classBody ->
                classBody
                    .findNodeAfterLastEnumEntry()
                    ?.let { nodeAfterLastEnumEntry ->
                        node.reportAndCorrectTrailingCommaNodeBefore(
                            inspectNode = nodeAfterLastEnumEntry,
                            isTrailingCommaAllowed = node.isTrailingCommaAllowed(),
                            autoCorrect = autoCorrect,
                            emit = emit,
                        )
                    }
            }
    }

    private fun ASTNode.noEnumEntries() = children().none { it.psi is KtEnumEntry }

    private fun ASTNode.lastTwoEnumEntriesAreOnSameLine(): Boolean {
        val lastTwoEnumEntries =
            children()
                .filter { it.psi is KtEnumEntry }
                .toList()
                .takeLast(2)

        return lastTwoEnumEntries.count() == 2 && noNewLineInClosedRange(lastTwoEnumEntries[0], lastTwoEnumEntries[1])
    }

    /**
     * Determines the [ASTNode] before which the trailing comma is allowed.
     *
     * If the list of enumeration entries is terminated by a semicolon, that semicolon will be returned. Otherwise, the
     * last element of the class.
     */
    private fun ASTNode.findNodeAfterLastEnumEntry() =
        children()
            .lastOrNull { it.psi is KtEnumEntry }
            ?.children()
            ?.singleOrNull { it.elementType == ElementType.SEMICOLON }
            ?: lastChildNode

    private fun ASTNode.reportAndCorrectTrailingCommaNodeBefore(
        inspectNode: ASTNode,
        isTrailingCommaAllowed: Boolean,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
    ) {
        val prevLeaf = inspectNode.prevLeaf()
        val trailingCommaNode = prevLeaf?.findPreviousTrailingCommaNodeOrNull()
        val trailingCommaState =
            when {
                isMultiline(psi) -> if (trailingCommaNode != null) TrailingCommaState.EXISTS else TrailingCommaState.MISSING
                else -> if (trailingCommaNode != null) TrailingCommaState.REDUNDANT else TrailingCommaState.NOT_EXISTS
            }
        when (trailingCommaState) {
            TrailingCommaState.EXISTS ->
                if (isTrailingCommaAllowed) {
                    inspectNode
                        .treeParent
                        .takeIf { it.elementType == WHEN_ENTRY }
                        ?.findChildByType(ARROW)
                        ?.prevLeaf()
                        ?.let { lastNodeBeforeArrow ->
                            if (lastNodeBeforeArrow.elementType != WHITE_SPACE || !lastNodeBeforeArrow.textContains('\n')) {
                                emit(
                                    trailingCommaNode!!.startOffset,
                                    "Expected a newline between the trailing comma and  \"${inspectNode.text}\"",
                                    true,
                                )
                                if (autoCorrect) {
                                    lastNodeBeforeArrow.upsertWhitespaceAfterMe(inspectNode.treeParent.indent())
                                }
                            }
                        }
                } else {
                    emit(
                        trailingCommaNode!!.startOffset,
                        "Unnecessary trailing comma before \"${inspectNode.text}\"",
                        true,
                    )
                    if (autoCorrect) {
                        this.removeChild(trailingCommaNode)
                    }
                }
            TrailingCommaState.MISSING ->
                if (isTrailingCommaAllowed) {
                    val addNewLineBeforeArrowInWhenEntry = addNewLineBeforeArrowInWhen()
                    val prevNode = inspectNode.prevCodeLeaf()!!
                    if (addNewLineBeforeArrowInWhenEntry) {
                        emit(
                            prevNode.startOffset + prevNode.textLength,
                            "Missing trailing comma and newline before \"${inspectNode.text}\"",
                            true,
                        )
                    } else {
                        emit(
                            prevNode.startOffset + prevNode.textLength,
                            "Missing trailing comma before \"${inspectNode.text}\"",
                            true,
                        )
                    }
                    if (autoCorrect) {
                        if (addNewLineBeforeArrowInWhenEntry) {
                            val newLine =
                                KtPsiFactory(prevNode.psi).createWhiteSpace(
                                    prevNode
                                        .treeParent
                                        .indent(),
                                )
                            val leafBeforeArrow = (psi as KtWhenEntry).arrow?.prevLeaf()
                            if (leafBeforeArrow != null && leafBeforeArrow is PsiWhiteSpace) {
                                leafBeforeArrow.replace(newLine)
                            } else {
                                prevNode.psi.parent.addAfter(newLine, prevNode.psi)
                            }
                        }

                        val comma = KtPsiFactory(prevNode.psi).createComma()
                        if (inspectNode.treeParent.elementType == ElementType.ENUM_ENTRY) {
                            with(KtPsiFactory(prevNode.psi)) {
                                val parentIndent =
                                    (prevNode.psi.parent.prevLeaf() as? PsiWhiteSpace)?.text
                                        ?: prevNode.indent()
                                val newline = createWhiteSpace(parentIndent)
                                val enumEntry = inspectNode.treeParent.psi
                                enumEntry.apply {
                                    inspectNode.psi.replace(comma)
                                    add(newline)
                                    add(createSemicolon())
                                }
                            }
                            Unit
                        } else {
                            prevNode.psi.parent.addAfter(comma, prevNode.psi)
                        }
                    }
                }
            TrailingCommaState.REDUNDANT -> {
                emit(
                    trailingCommaNode!!.startOffset,
                    "Unnecessary trailing comma before \"${inspectNode.text}\"",
                    true,
                )
                if (autoCorrect) {
                    this.removeChild(trailingCommaNode)
                }
            }
            TrailingCommaState.NOT_EXISTS -> Unit
        }
    }

    private fun isMultiline(element: PsiElement): Boolean =
        when {
            element.parent is KtFunctionLiteral -> {
                isMultiline(element.parent)
            }
            element is KtFunctionLiteral -> {
                containsLineBreakInLeavesRange(element.valueParameterList!!, element.arrow!!)
            }
            element is KtWhenEntry -> {
                containsLineBreakInLeavesRange(element.firstChild, element.arrow!!)
            }
            element is KtDestructuringDeclaration -> {
                containsLineBreakInLeavesRange(element.lPar!!, element.rPar!!)
            }
            element is KtValueArgumentList &&
                element.children.size == 1 &&
                element.anyDescendantOfType<KtCollectionLiteralExpression>() -> {
                // special handling for collection literal
                // @Annotation([
                //    "something",
                // ])
                val lastChild = element.collectDescendantsOfType<KtCollectionLiteralExpression>().last()
                containsLineBreakInLeavesRange(lastChild.rightBracket!!, element.rightParenthesis!!)
            }
            element is KtParameterList && element.parameters.isEmpty() -> {
                false
            }
            else -> {
                element.textContains('\n')
            }
        }

    private fun ASTNode.addNewLineBeforeArrowInWhen() =
        if (psi is KtWhenEntry) {
            val leafBeforeArrow = (psi as KtWhenEntry).arrow?.prevLeaf()
            !(leafBeforeArrow is PsiWhiteSpace && leafBeforeArrow.textContains('\n'))
        } else {
            false
        }

    private fun ASTNode.findPreviousTrailingCommaNodeOrNull(): ASTNode? {
        val codeLeaf =
            if (isCodeLeaf()) {
                this
            } else {
                prevCodeLeaf()
            }
        return codeLeaf?.takeIf { it.elementType == ElementType.COMMA }
    }

    private fun containsLineBreakInLeavesRange(
        from: PsiElement,
        to: PsiElement,
    ): Boolean {
        var leaf: PsiElement? = from
        while (leaf != null && !leaf.isEquivalentTo(to)) {
            if (leaf.textContains('\n')) {
                return true
            }
            leaf = leaf.nextLeaf(skipEmptyElements = false)
        }
        return leaf?.textContains('\n') ?: false
    }

    private enum class TrailingCommaState {
        /**
         * The trailing comma is needed and exists
         */
        EXISTS,

        /**
         * The trailing comma is needed and doesn't exist
         */
        MISSING,

        /**
         * The trailing comma isn't needed and doesn't exist
         */
        NOT_EXISTS,

        /**
         * The trailing comma isn't needed, but exists
         */
        REDUNDANT,
    }

    public companion object {
        private val BOOLEAN_VALUES_SET = setOf("true", "false")

        public val TRAILING_COMMA_ON_DECLARATION_SITE_PROPERTY: EditorConfigProperty<Boolean> =
            EditorConfigProperty(
                type =
                    PropertyType.LowerCasingPropertyType(
                        "ij_kotlin_allow_trailing_comma",
                        "Defines whether a trailing comma (or no trailing comma) should be enforced on the defining " +
                            "side, e.g. parameter-list, type-argument-list, lambda-value-parameters, enum-entries, etc." +
                            "When set, IntelliJ IDEA uses this property to allow usage of a trailing comma by discretion " +
                            "of the developer. KtLint however uses this setting to enforce consistent usage of the " +
                            "trailing comma when set.",
                        PropertyValueParser.BOOLEAN_VALUE_PARSER,
                        BOOLEAN_VALUES_SET,
                    ),
                defaultValue = true,
                androidStudioCodeStyleDefaultValue = false,
            )

        private val TYPES_ON_DECLARATION_SITE =
            TokenSet.create(
                CLASS,
                DESTRUCTURING_DECLARATION,
                FUNCTION_LITERAL,
                FUNCTION_TYPE,
                TYPE_PARAMETER_LIST,
                VALUE_PARAMETER_LIST,
                WHEN_ENTRY,
            )
    }
}

public val TRAILING_COMMA_ON_DECLARATION_SITE_RULE_ID: RuleId = TrailingCommaOnDeclarationSiteRule().ruleId
