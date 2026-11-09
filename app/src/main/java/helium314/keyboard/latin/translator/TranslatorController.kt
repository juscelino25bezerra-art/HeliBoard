// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.translator

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.InputTypeUtils
import helium314.keyboard.latin.utils.dpToPx
import helium314.keyboard.latin.utils.prefs
import java.util.Locale

/**
 * Modo tradutor, no estilo do Gboard: com ele ligado, o que você digita vai para um campo dentro do
 * teclado, e a tradução vai aparecendo no app em que você está escrevendo.
 *
 * Funciona assim:
 * - [LatinIME.getCurrentInputConnection] devolve [inputConnection] enquanto o modo está ligado, então toda a
 *   lógica de digitação (sugestões, autocorreção, gestos) passa a escrever no campo interno;
 * - a cada pausa na digitação o texto vai para o [TranslatorClient] e o resultado entra no app como texto
 *   em composição, que é substituído a cada nova tradução;
 * - Enter confirma a tradução e executa a ação do app (enviar, buscar...). Ao fechar, a tradução fica no app.
 */
class TranslatorController(private val ime: LatinIME) {
    private val client by lazy { TranslatorClient(ime) }
    private val handler = Handler(Looper.getMainLooper())

    private var bar: LinearLayout? = null
    private var field: EditText? = null
    private var fromChip: TextView? = null
    private var toChip: TextView? = null
    private var swapButton: ImageButton? = null
    private var status: TextView? = null
    private var internalConnection: InputConnection? = null

    private var requestId = 0
    private var lastTranslatedSource: String? = null
    private var sendAfterTranslation = false

    private val prefs get() = ime.prefs()
    private var fromLanguage: String
        get() = prefs.getString(PREF_FROM, null) ?: defaultFrom()
        set(value) = prefs.edit { putString(PREF_FROM, value) }
    private var toLanguage: String
        get() = prefs.getString(PREF_TO, null) ?: defaultTo(fromLanguage)
        set(value) = prefs.edit { putString(PREF_TO, value) }

    private val translateRunnable = Runnable { translateNow() }

    /** O campo interno, enquanto o modo tradutor está ligado; senão null (usa o campo do app). */
    fun inputConnection(): InputConnection? = if (isActive) internalConnection else null

    /** Chamado a cada nova view do teclado (troca de tema, rotação...). */
    fun attach(mainKeyboardFrame: ViewGroup?) {
        if (isActive) deactivate(restartInput = false)
        bar?.let { (it.parent as? ViewGroup)?.removeView(it) }
        bar = null
        if (mainKeyboardFrame == null) return
        val newBar = buildBar(mainKeyboardFrame.context)
        newBar.visibility = View.GONE
        mainKeyboardFrame.addView(newBar, 0)
        bar = newBar
    }

    fun toggle() {
        if (isActive || bar?.visibility == View.VISIBLE) deactivate(restartInput = true) else activate()
    }

    /** O teclado foi fechado ou o campo mudou: sai do modo sem mexer mais no app. */
    fun onFinishInputView() {
        if (isActive || bar?.visibility == View.VISIBLE) deactivate(restartInput = false)
    }

    fun release() {
        client.disconnect()
    }

    /** Enter no modo tradutor. Retorna false quando o Enter deve seguir normalmente (quebra de linha). */
    fun onEnter(): Boolean {
        if (!isActive) return false
        val editorInfo = ime.currentInputEditorInfo ?: return false
        if (InputTypeUtils.getImeOptionsActionIdFromEditorInfo(editorInfo) == EditorInfo.IME_ACTION_NONE)
            return false // campo de várias linhas: a quebra de linha entra no texto a traduzir
        val text = currentText()
        if (text.isBlank() || text == lastTranslatedSource) {
            sendToApp()
        } else {
            sendAfterTranslation = true
            translateNow()
        }
        return true
    }

    private fun activate() {
        val bar = bar ?: return
        bar.visibility = View.VISIBLE
        updateLanguageChips()
        if (!client.isInstalled()) {
            field?.visibility = View.GONE
            showStatus(ime.getString(R.string.translator_not_installed), clickable = true)
            return
        }
        field?.visibility = View.VISIBLE
        showStatus(null)
        client.connect()
        ime.switchInputTarget {
            field?.text?.clear()
            internalConnection = field?.onCreateInputConnection(EditorInfo())
            lastTranslatedSource = null
            isActive = internalConnection != null
        }
        if (!isActive) showStatus(ime.getString(R.string.translator_not_connected))
        updateToolbar()
    }

    private fun deactivate(restartInput: Boolean) {
        handler.removeCallbacks(translateRunnable)
        requestId++
        sendAfterTranslation = false
        val wasActive = isActive
        val leave = {
            isActive = false
            internalConnection = null
            ime.appInputConnection?.finishComposingText()
            field?.text?.clear()
        }
        if (wasActive && restartInput) ime.switchInputTarget(leave) else leave()
        bar?.visibility = View.GONE
        showStatus(null)
        updateToolbar()
    }

    private fun scheduleTranslation() {
        if (!isActive) return
        handler.removeCallbacks(translateRunnable)
        handler.postDelayed(translateRunnable, TRANSLATE_DELAY_MS)
    }

    private fun translateNow() {
        handler.removeCallbacks(translateRunnable)
        if (!isActive) return
        val text = currentText()
        val id = ++requestId
        if (text.isBlank()) {
            applyTranslation(text, "")
            return
        }
        val from = fromLanguage
        val to = toLanguage
        if (from == to) {
            applyTranslation(text, text)
            return
        }
        showStatus(ime.getString(R.string.translator_translating))
        client.translate(text.trim(), from, to) { result ->
            if (id != requestId || !isActive) return@translate
            when (result) {
                is TranslatorClient.Result.Success -> applyTranslation(text, keepOuterSpaces(text, result.text))
                is TranslatorClient.Result.Failure -> {
                    sendAfterTranslation = false
                    showStatus(errorMessage(result))
                }
            }
        }
    }

    private fun applyTranslation(source: String, translation: String) {
        lastTranslatedSource = source
        showStatus(null)
        ime.appInputConnection?.setComposingText(translation, 1)
        if (sendAfterTranslation) {
            sendAfterTranslation = false
            sendToApp()
        }
    }

    /** Fixa a tradução no app, executa a ação do Enter (enviar, buscar...) e limpa o campo interno. */
    private fun sendToApp() {
        val appConnection = ime.appInputConnection ?: return
        val editorInfo = ime.currentInputEditorInfo ?: return
        appConnection.finishComposingText()
        val actionId = InputTypeUtils.getImeOptionsActionIdFromEditorInfo(editorInfo)
        appConnection.performEditorAction(
            if (actionId == InputTypeUtils.IME_ACTION_CUSTOM_LABEL) editorInfo.actionId else actionId
        )
        ime.switchInputTarget {
            field?.text?.clear()
            lastTranslatedSource = null
        }
    }

    private fun currentText() = field?.text?.toString().orEmpty()

    private fun errorMessage(failure: TranslatorClient.Result.Failure): String = when (failure.reason) {
        TranslatorClient.Reason.NOT_CONNECTED -> ime.getString(R.string.translator_not_connected)
        TranslatorClient.Reason.COULD_NOT_DETECT_LANGUAGE -> ime.getString(R.string.translator_detect_failed)
        TranslatorClient.Reason.LANGUAGE_NOT_DOWNLOADED -> failure.detail?.let { ime.getString(R.string.translator_language_missing, it) }
            ?: ime.getString(R.string.translator_language_missing_generic)
        TranslatorClient.Reason.UNEXPECTED -> failure.detail ?: ime.getString(R.string.translator_error)
    }

    // ---------- idiomas ----------

    private fun keyboardLanguage(): String? {
        val locale = RichInputMethodManager.getInstance().currentSubtypeLocale
        return locale.language.takeIf { it in LANGUAGES }
    }

    private fun defaultFrom() = keyboardLanguage() ?: "pt"

    private fun defaultTo(from: String) = if (from == "en") "pt" else "en"

    private fun swapLanguages() {
        val oldFrom = fromLanguage
        val oldTo = toLanguage
        val newTo = oldFrom.ifEmpty { keyboardLanguage()?.takeIf { it != oldTo } ?: defaultTo(oldTo) }
        fromLanguage = oldTo
        toLanguage = newTo
        onLanguagesChanged()
    }

    private fun onLanguagesChanged() {
        updateLanguageChips()
        lastTranslatedSource = null
        if (isActive && currentText().isNotBlank()) translateNow()
    }

    private fun showLanguageMenu(anchor: View, source: Boolean) {
        val menu = PopupMenu(anchor.context, anchor)
        val options = buildList {
            if (source) add("" to ime.getString(R.string.translator_detect_language))
            LANGUAGES.map { it to languageName(it) }.sortedBy { it.second.lowercase() }.forEach { add(it) }
        }
        options.forEachIndexed { index, (_, name) -> menu.menu.add(0, index, index, name) }
        menu.setOnMenuItemClickListener { item ->
            val code = options[item.itemId].first
            if (source) {
                if (code.isNotEmpty() && code == toLanguage) toLanguage = fromLanguage.ifEmpty { defaultTo(code) }
                fromLanguage = code
            } else {
                if (code == fromLanguage) fromLanguage = toLanguage
                toLanguage = code
            }
            onLanguagesChanged()
            true
        }
        menu.show()
    }

    private fun languageName(code: String): String {
        if (code.isEmpty()) return ime.getString(R.string.translator_detect_language)
        val tag = when (code) {
            "zh" -> "zh-Hans"
            "zh_hant" -> "zh-Hant"
            else -> code.replace('_', '-')
        }
        val locale = Locale.forLanguageTag(tag)
        val name = if (code.startsWith("zh")) locale.getDisplayName(Locale.getDefault()) else locale.getDisplayLanguage(Locale.getDefault())
        return name.replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }

    private fun updateLanguageChips() {
        val from = fromLanguage
        fromChip?.text = languageName(from)
        toChip?.text = languageName(toLanguage)
        field?.hint = if (from.isEmpty()) ime.getString(R.string.translator_hint_auto)
            else ime.getString(R.string.translator_hint, languageName(from).lowercase(Locale.getDefault()))
    }

    private fun updateToolbar() {
        ime.updateToolbarKeysActivatedState()
    }

    // ---------- interface ----------

    private fun showStatus(message: String?, clickable: Boolean = false) {
        val status = status ?: return
        status.text = message
        status.visibility = if (message.isNullOrEmpty()) View.GONE else View.VISIBLE
        status.setOnClickListener(if (clickable) View.OnClickListener { openStore() } else null)
        status.isClickable = clickable
    }

    private fun openStore() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(TranslatorClient.FDROID_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ime.startActivity(intent)
        } catch (_: Exception) { }
    }

    private fun buildBar(context: Context): LinearLayout {
        val colors = Settings.getValues().mColors
        val res = context.resources
        fun dp(value: Int) = value.dpToPx(res)
        val textColor = colors.get(ColorType.KEY_TEXT)
        val hintColor = colors.get(ColorType.KEY_HINT_TEXT)

        fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
            cornerRadius = dp(radius).toFloat()
            setColor(color)
        }

        fun chip(onClick: (View) -> Unit) = TextView(context).apply {
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(colors.get(ColorType.FUNCTIONAL_KEY_BACKGROUND), 16)
            setPadding(dp(12), 0, dp(8), 0)
            compoundDrawablePadding = dp(4)
            ContextCompat.getDrawable(context, R.drawable.ic_phosphor_caret_down)?.mutate()?.let {
                it.setBounds(0, 0, dp(14), dp(14))
                it.setTint(hintColor)
                setCompoundDrawablesRelative(null, null, it, null)
            }
            setOnClickListener(onClick)
        }

        fun iconButton(icon: Int, description: Int, onClick: () -> Unit) = ImageButton(context).apply {
            setImageResource(icon)
            colors.setColor(this, ColorType.TOOL_BAR_KEY)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = null
            setPadding(dp(8), dp(8), dp(8), dp(8))
            contentDescription = context.getString(description)
            setOnClickListener { onClick() }
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            tag = BAR_TAG
            colors.setBackground(this, ColorType.STRIP_BACKGROUND)
            setPadding(dp(8), dp(6), dp(8), dp(4))
        }

        val languagesRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val chipParams = { LinearLayout.LayoutParams(0, dp(32), 1f) }
        val from = chip { showLanguageMenu(it, source = true) }
        val to = chip { showLanguageMenu(it, source = false) }
        val swap = iconButton(R.drawable.ic_phosphor_swap, R.string.translator_swap) { swapLanguages() }
        val close = iconButton(R.drawable.ic_phosphor_close, R.string.translator_close) { deactivate(restartInput = true) }
        languagesRow.addView(from, chipParams())
        languagesRow.addView(swap, LinearLayout.LayoutParams(dp(40), dp(40)))
        languagesRow.addView(to, chipParams())
        languagesRow.addView(close, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginStart = dp(4) })

        val input = EditText(context).apply {
            // o campo nunca recebe foco do sistema: quem escreve nele é o próprio teclado
            isFocusable = false
            isFocusableInTouchMode = false
            showSoftInputOnFocus = false
            isLongClickable = false
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 3
            minHeight = dp(40)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(textColor)
            setHintTextColor(hintColor)
            background = rounded(colors.get(ColorType.KEY_BACKGROUND), 12)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) = scheduleTranslation()
            })
        }

        val statusView = TextView(context).apply {
            setTextColor(hintColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(4), dp(4), dp(4), dp(2))
            visibility = View.GONE
        }

        root.addView(languagesRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        root.addView(statusView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        fromChip = from
        toChip = to
        swapButton = swap
        field = input
        status = statusView
        updateLanguageChips()
        return root
    }

    companion object {
        /** Lido pela barra de ferramentas para destacar o botão do tradutor. */
        @JvmStatic
        var isActive = false
            private set

        const val BAR_TAG = "translator_bar"
        private const val PREF_FROM = "translator_from_language"
        private const val PREF_TO = "translator_to_language"
        private const val TRANSLATE_DELAY_MS = 350L

        /** Idiomas com modelos de tradução no Offline Translator (catálogo index_v7). */
        val LANGUAGES = listOf(
            "af", "ar", "az", "be", "bg", "bn", "bs", "ca", "cs", "da", "de", "el", "en", "es", "et", "eu", "fa",
            "fi", "fr", "gl", "gu", "he", "hi", "hr", "hu", "id", "is", "it", "ja", "ka", "kn", "ko", "lt", "lv",
            "ml", "mr", "ms", "nb", "nl", "nn", "no", "pl", "pt", "ro", "ru", "sk", "sl", "sq", "sr", "sv", "sw",
            "ta", "te", "th", "tl", "tr", "ug", "uk", "ur", "vi", "zh", "zh_hant",
        )

        /** Mantém espaços do começo/fim do texto digitado, que o tradutor descarta. */
        private fun keepOuterSpaces(source: String, translation: String): String {
            val leading = source.takeWhile { it.isWhitespace() }
            val trailing = source.takeLastWhile { it.isWhitespace() }
            return leading + translation.trim() + trailing
        }
    }
}
