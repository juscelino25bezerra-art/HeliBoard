package helium314.keyboard.keyboard.internal

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.customIconIds
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.prefs
import java.util.Locale

class KeyboardIconsSet private constructor() {
    var iconIds = emptyMap<String, Int>()
        private set
    private val iconsByName = HashMap<String, Drawable>(80)

    fun loadIcons(context: Context) {
        val prefs = context.prefs()
        val iconStyle = prefs.getString(Settings.PREF_ICON_STYLE, Defaults.PREF_ICON_STYLE(prefs))
        val defaultIds = when (iconStyle) {
            KeyboardTheme.STYLE_HOLO -> keyboardIconsHolo
            KeyboardTheme.STYLE_ROUNDED, KeyboardTheme.STYLE_IOS -> keyboardIconsRounded
            else -> keyboardIconsMaterial
        }
        val overrideIds = customIconIds(context, prefs)
        val ids = if (overrideIds.isEmpty()) defaultIds else defaultIds + overrideIds
        if (!needsReload && ids == iconIds) return
        iconIds = ids
        iconsByName.clear()
        ids.forEach { (name, id) ->
            try {
                val icon = ContextCompat.getDrawable(context, id) ?: return@forEach
                icon.setBounds(0, 0, icon.intrinsicWidth, icon.intrinsicHeight)
                iconsByName[name] = icon
            } catch (_: Resources.NotFoundException) {
                Log.w(TAG, "Drawable resource for icon $name not found")
            }
        }
        needsReload = false
    }

    fun getIconDrawable(name: String?): Drawable? = name?.lowercase(Locale.US)?.let {
        iconsByName[it] ?: iconsByName[alternativeNames[it]]
    }

    /** gets drawable from resources, with mutate (might be necessary to avoid coloring issues...) */
    fun getNewDrawable(name: String?, context: Context): Drawable? = name?.lowercase(Locale.US)?.let { name ->
        (iconIds[name] ?: iconIds[alternativeNames[name]])?.let { ContextCompat.getDrawable(context, it)?.mutate() }
    }

    companion object {
        private val TAG = KeyboardIconsSet::class.simpleName
        const val PREFIX_ICON = "!icon/"

        const val NAME_SHIFT_KEY = "shift_key"
        const val NAME_SHIFT_KEY_SHIFTED = "shift_key_shifted"
        const val NAME_SHIFT_KEY_LOCKED = "shift_key_locked"
        const val NAME_DELETE_KEY = "delete_key"
        const val NAME_SPACE_KEY = "space_key"
        const val NAME_SPACE_KEY_FOR_NUMBER_LAYOUT = "space_key_for_number_layout"
        const val NAME_ENTER_KEY = "enter_key"
        const val NAME_GO_KEY = "go_key"
        const val NAME_SEARCH_KEY = "search_key"
        const val NAME_SEND_KEY = "send_key"
        const val NAME_NEXT_KEY = "next_key"
        const val NAME_DONE_KEY = "done_key"
        const val NAME_PREVIOUS_KEY = "previous_key"
        const val NAME_TAB_KEY = "tab_key"
        const val NAME_SHORTCUT_KEY_DISABLED = "shortcut_key_disabled"
        const val NAME_LANGUAGE_SWITCH_KEY = "language_switch_key"
        const val NAME_ZWNJ_KEY = "zwnj_key"
        const val NAME_ZWJ_KEY = "zwj_key"
        const val NAME_STOP_ONEHANDED_KEY = "stop_onehanded_mode_key"
        const val NAME_SWITCH_ONEHANDED_KEY = "switch_onehanded_key"
        const val NAME_RESIZE_ONEHANDED_KEY = "resize_onehanded_key"
        const val NAME_TOOLBAR_KEY = "toolbar_key"
        const val NAME_BIN = "bin"

        // names used in the past, and we can't just delete them because they might still be in use in some layouts
        // (also some of them are in use for internal layouts, but there we could just remove them...)
        private val alternativeNames = hashMapOf(
            "clear_clipboard_key" to ToolbarKey.CLEAR_CLIPBOARD.name.lowercase(Locale.US),
            "shortcut_key" to ToolbarKey.VOICE.name.lowercase(Locale.US),
            "emoji_action_key" to ToolbarKey.EMOJI.name.lowercase(Locale.US),
            "emoji_normal_key" to ToolbarKey.EMOJI.name.lowercase(Locale.US),
            "clipboard_action_key" to ToolbarKey.CLIPBOARD.name.lowercase(Locale.US),
            "clipboard_normal_key" to ToolbarKey.CLIPBOARD.name.lowercase(Locale.US),
            "cut_key" to ToolbarKey.CUT.name.lowercase(Locale.US),
            "incognito_key" to ToolbarKey.INCOGNITO.name.lowercase(Locale.US),
            "settings_key" to ToolbarKey.SETTINGS.name.lowercase(Locale.US),
            "start_onehanded_mode_key" to ToolbarKey.ONE_HANDED.name.lowercase(Locale.US),
        )

        // toolbar icons from Phosphor Icons (MIT), shared by all styles
        private fun phosphorToolbarIcon(key: ToolbarKey) = when (key) {
            ToolbarKey.VOICE -> R.drawable.ic_phosphor_voice
            ToolbarKey.CLIPBOARD -> R.drawable.ic_phosphor_clipboard
            ToolbarKey.NUMPAD -> R.drawable.ic_phosphor_numpad
            ToolbarKey.DPAD -> R.drawable.ic_phosphor_dpad
            ToolbarKey.UNDO -> R.drawable.ic_phosphor_undo
            ToolbarKey.REDO -> R.drawable.ic_phosphor_redo
            ToolbarKey.SETTINGS -> R.drawable.ic_phosphor_settings
            ToolbarKey.SELECT_ALL -> R.drawable.ic_phosphor_select_all
            ToolbarKey.SELECT_WORD -> R.drawable.ic_phosphor_select_word
            ToolbarKey.COPY -> R.drawable.ic_phosphor_copy
            ToolbarKey.CUT -> R.drawable.ic_phosphor_cut
            ToolbarKey.PASTE -> R.drawable.ic_phosphor_paste
            ToolbarKey.ONE_HANDED -> R.drawable.ic_phosphor_one_handed
            ToolbarKey.FLOATING -> R.drawable.ic_phosphor_floating
            ToolbarKey.INCOGNITO -> R.drawable.ic_phosphor_incognito
            ToolbarKey.AUTOCORRECT -> R.drawable.ic_phosphor_autocorrect
            ToolbarKey.CLEAR_CLIPBOARD -> R.drawable.ic_phosphor_clear_clipboard
            ToolbarKey.CLOSE_HISTORY -> R.drawable.ic_phosphor_close_history
            ToolbarKey.EMOJI -> R.drawable.ic_phosphor_emoji
            ToolbarKey.LEFT -> R.drawable.ic_phosphor_left
            ToolbarKey.RIGHT -> R.drawable.ic_phosphor_right
            ToolbarKey.UP -> R.drawable.ic_phosphor_up
            ToolbarKey.DOWN -> R.drawable.ic_phosphor_down
            ToolbarKey.WORD_LEFT -> R.drawable.ic_phosphor_word_left
            ToolbarKey.WORD_RIGHT -> R.drawable.ic_phosphor_word_right
            ToolbarKey.PAGE_UP -> R.drawable.ic_phosphor_page_up
            ToolbarKey.PAGE_DOWN -> R.drawable.ic_phosphor_page_down
            ToolbarKey.FULL_LEFT -> R.drawable.ic_phosphor_full_left
            ToolbarKey.FULL_RIGHT -> R.drawable.ic_phosphor_full_right
            ToolbarKey.PAGE_START -> R.drawable.ic_phosphor_page_start
            ToolbarKey.PAGE_END -> R.drawable.ic_phosphor_page_end
            ToolbarKey.SPLIT -> R.drawable.ic_phosphor_split
            ToolbarKey.BACKGROUND_GATHERING -> R.drawable.ic_phosphor_background_gathering
        }

        // todo: incognito and force incognito should not be the same? or not the same as toolbar key?
        private val keyboardIconsHolo by lazy { hashMapOf(
            NAME_SHIFT_KEY to                   R.drawable.sym_keyboard_shift_holo,
            NAME_SHIFT_KEY_SHIFTED to           R.drawable.sym_keyboard_shifted_holo,
            NAME_SHIFT_KEY_LOCKED to            R.drawable.sym_keyboard_shift_lock_holo,
            NAME_DELETE_KEY to                  R.drawable.sym_keyboard_delete_holo,
//            NAME_SPACE_KEY to                   null,
            NAME_ENTER_KEY to                   R.drawable.sym_keyboard_return_holo,
//            NAME_GO_KEY to                      null,
            NAME_SEARCH_KEY to                  R.drawable.sym_keyboard_search_holo,
//            NAME_SEND_KEY to                    null,
//            NAME_DONE_KEY to                    null,
//            NAME_NEXT_KEY to                    null,
//            NAME_PREVIOUS_KEY to                null,
            NAME_TAB_KEY to                     R.drawable.sym_keyboard_tab_holo,
            NAME_SPACE_KEY_FOR_NUMBER_LAYOUT to R.drawable.sym_keyboard_space_holo,
            NAME_SHORTCUT_KEY_DISABLED to       R.drawable.sym_keyboard_voice_off_holo,
            NAME_LANGUAGE_SWITCH_KEY to         R.drawable.sym_keyboard_language_switch,
            NAME_ZWNJ_KEY to                    R.drawable.sym_keyboard_zwnj_holo,
            NAME_ZWJ_KEY to                     R.drawable.sym_keyboard_zwj_holo,
            NAME_STOP_ONEHANDED_KEY to          R.drawable.sym_keyboard_stop_onehanded_holo,
            NAME_SWITCH_ONEHANDED_KEY to        R.drawable.ic_arrow_left,
            NAME_RESIZE_ONEHANDED_KEY to        R.drawable.ic_arrow_horizontal,
            NAME_TOOLBAR_KEY to                 R.drawable.ic_phosphor_toolbar_expand,
            NAME_BIN to                         R.drawable.ic_phosphor_bin,
        ).apply {
            ToolbarKey.entries.forEach {
                put(it.name.lowercase(Locale.US), phosphorToolbarIcon(it))
            }
        } }

        private val keyboardIconsMaterial by lazy { hashMapOf(
            NAME_SHIFT_KEY to                   R.drawable.sym_keyboard_shift_lxx,
            NAME_SHIFT_KEY_SHIFTED to           R.drawable.sym_keyboard_shifted_lxx,
            NAME_SHIFT_KEY_LOCKED to            R.drawable.sym_keyboard_shift_lock_lxx,
            NAME_DELETE_KEY to                  R.drawable.sym_keyboard_delete_lxx,
//            NAME_SPACE_KEY to                   null,
            NAME_ENTER_KEY to                   R.drawable.sym_keyboard_return_lxx,
            NAME_GO_KEY to                      R.drawable.sym_keyboard_go_lxx,
            NAME_SEARCH_KEY to                  R.drawable.sym_keyboard_search_lxx,
            NAME_SEND_KEY to                    R.drawable.sym_keyboard_send_lxx,
            NAME_DONE_KEY to                    R.drawable.sym_keyboard_done_lxx,
            NAME_NEXT_KEY to                    R.drawable.ic_arrow_right,
            NAME_PREVIOUS_KEY to                R.drawable.ic_arrow_left,
            NAME_TAB_KEY to                     R.drawable.sym_keyboard_tab_lxx,
            NAME_SPACE_KEY_FOR_NUMBER_LAYOUT to R.drawable.sym_keyboard_space_lxx,
            NAME_SHORTCUT_KEY_DISABLED to       R.drawable.sym_keyboard_voice_off_lxx,
            NAME_LANGUAGE_SWITCH_KEY to         R.drawable.sym_keyboard_language_switch_lxx,
            NAME_ZWNJ_KEY to                    R.drawable.sym_keyboard_zwnj_lxx,
            NAME_ZWJ_KEY to                     R.drawable.sym_keyboard_zwj_lxx,
            NAME_STOP_ONEHANDED_KEY to          R.drawable.sym_keyboard_stop_onehanded_lxx,
            NAME_SWITCH_ONEHANDED_KEY to        R.drawable.ic_arrow_left,
            NAME_RESIZE_ONEHANDED_KEY to        R.drawable.ic_arrow_horizontal,
            NAME_TOOLBAR_KEY to                 R.drawable.ic_phosphor_toolbar_expand,
            NAME_BIN to                         R.drawable.ic_phosphor_bin,
        ).apply {
            ToolbarKey.entries.forEach {
                put(it.name.lowercase(Locale.US), phosphorToolbarIcon(it))
            }
        } }

        private val keyboardIconsRounded by lazy { hashMapOf(
            NAME_SHIFT_KEY to                   R.drawable.sym_keyboard_shift_rounded,
            NAME_SHIFT_KEY_SHIFTED to           R.drawable.sym_keyboard_shifted_rounded,
            NAME_SHIFT_KEY_LOCKED to            R.drawable.sym_keyboard_shift_lock_rounded,
            NAME_DELETE_KEY to                  R.drawable.sym_keyboard_delete_rounded,
//            NAME_SPACE_KEY to                   null,
            NAME_ENTER_KEY to                   R.drawable.sym_keyboard_return_rounded,
            NAME_GO_KEY to                      R.drawable.sym_keyboard_go_rounded,
            NAME_SEARCH_KEY to                  R.drawable.sym_keyboard_search_rounded,
            NAME_SEND_KEY to                    R.drawable.sym_keyboard_send_rounded,
            NAME_DONE_KEY to                    R.drawable.sym_keyboard_done_rounded,
            NAME_NEXT_KEY to                    R.drawable.ic_arrow_right_rounded,
            NAME_PREVIOUS_KEY to                R.drawable.ic_arrow_left_rounded,
            NAME_TAB_KEY to                     R.drawable.sym_keyboard_tab_rounded,
            NAME_SPACE_KEY_FOR_NUMBER_LAYOUT to R.drawable.sym_keyboard_space_rounded,
            NAME_SHORTCUT_KEY_DISABLED to       R.drawable.sym_keyboard_voice_off_rounded,
            NAME_LANGUAGE_SWITCH_KEY to         R.drawable.sym_keyboard_language_switch_lxx,
            NAME_ZWNJ_KEY to                    R.drawable.sym_keyboard_zwnj_lxx,
            NAME_ZWJ_KEY to                     R.drawable.sym_keyboard_zwj_lxx,
            NAME_STOP_ONEHANDED_KEY to          R.drawable.sym_keyboard_stop_onehanded_rounded,
            NAME_SWITCH_ONEHANDED_KEY to        R.drawable.ic_arrow_left_rounded,
            NAME_RESIZE_ONEHANDED_KEY to        R.drawable.ic_arrow_horizontal_rounded,
            NAME_TOOLBAR_KEY to                 R.drawable.ic_phosphor_toolbar_expand,
            NAME_BIN to                         R.drawable.ic_phosphor_bin,
        ).apply {
            ToolbarKey.entries.forEach {
                put(it.name.lowercase(Locale.US), phosphorToolbarIcon(it))
            }
        } }

        fun getAllIcons(context: Context): Map<String, List<Int>> {
            // currently active style first
            val iconStyle = context.prefs().getString(Settings.PREF_ICON_STYLE, Defaults.PREF_ICON_STYLE(context.prefs()))
            val iconsMap = keyboardIconsMaterial.entries.associateTo(HashMap()) { (name, id) ->
                name to when (iconStyle) {
                    KeyboardTheme.STYLE_HOLO -> listOfNotNull(keyboardIconsHolo[name], keyboardIconsRounded[name], id)
                    KeyboardTheme.STYLE_ROUNDED, KeyboardTheme.STYLE_IOS -> listOfNotNull(keyboardIconsRounded[name], id, keyboardIconsHolo[name])
                    else -> listOfNotNull(id, keyboardIconsRounded[name], keyboardIconsHolo[name])
                }
            }

            // add legacy shift icons
            iconsMap[NAME_SHIFT_KEY] = iconsMap[NAME_SHIFT_KEY]!! + R.drawable.sym_keyboard_shift_legacy + R.drawable.sym_keyboard_shift_legacy_rounded
            iconsMap[NAME_SHIFT_KEY_SHIFTED] = iconsMap[NAME_SHIFT_KEY_SHIFTED]!! + R.drawable.sym_keyboard_shift_legacy + R.drawable.sym_keyboard_shift_legacy_rounded
            iconsMap[NAME_SHIFT_KEY_LOCKED] = iconsMap[NAME_SHIFT_KEY_LOCKED]!! + R.drawable.sym_keyboard_shift_lock_legacy + R.drawable.sym_keyboard_shift_lock_legacy_rounded
            return iconsMap
        }

        val instance = KeyboardIconsSet()
        var needsReload = false
    }
}
