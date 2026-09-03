package com.sanchr.core.designsystem.component

import android.content.Context
import android.telephony.TelephonyManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanchr.core.designsystem.theme.LocalSanchrSurfaces
import com.sanchr.core.designsystem.theme.SanchrIndigo500
import kotlinx.coroutines.launch

/**
 * Static record for a selectable country entry in the picker.
 *
 * [dialCode] is in E.164 leading form (e.g. "+91"); [iso] is the uppercase
 * ISO-3166 alpha-2 code used to match against [TelephonyManager] lookups.
 * [flagEmoji] is the regional-indicator flag glyph — rendered as-is since
 * emoji fonts on Android 8+ cover the full block. Keep this file alphabetical
 * by [name] for reviewability.
 */
data class Country(
    val name: String,
    val dialCode: String,
    val flagEmoji: String,
    val iso: String,
)

/**
 * Curated short list of the ~30 most common countries Sanchr users will be
 * dialing from. Not exhaustive — if a user's SIM ISO is not present, we
 * fall back to US and they can still search / scroll. Expand deliberately
 * rather than pulling libphonenumber (adds ~500KB per ABI).
 *
 * Ordered alphabetically by display name.
 */
val SanchrCountries: List<Country> =
    listOf(
        Country("Argentina", "+54", "\uD83C\uDDE6\uD83C\uDDF7", "AR"),
        Country("Australia", "+61", "\uD83C\uDDE6\uD83C\uDDFA", "AU"),
        Country("Bangladesh", "+880", "\uD83C\uDDE7\uD83C\uDDE9", "BD"),
        Country("Brazil", "+55", "\uD83C\uDDE7\uD83C\uDDF7", "BR"),
        Country("Canada", "+1", "\uD83C\uDDE8\uD83C\uDDE6", "CA"),
        Country("China", "+86", "\uD83C\uDDE8\uD83C\uDDF3", "CN"),
        Country("Egypt", "+20", "\uD83C\uDDEA\uD83C\uDDEC", "EG"),
        Country("France", "+33", "\uD83C\uDDEB\uD83C\uDDF7", "FR"),
        Country("Germany", "+49", "\uD83C\uDDE9\uD83C\uDDEA", "DE"),
        Country("Indonesia", "+62", "\uD83C\uDDEE\uD83C\uDDE9", "ID"),
        Country("India", "+91", "\uD83C\uDDEE\uD83C\uDDF3", "IN"),
        Country("Israel", "+972", "\uD83C\uDDEE\uD83C\uDDF1", "IL"),
        Country("Italy", "+39", "\uD83C\uDDEE\uD83C\uDDF9", "IT"),
        Country("Japan", "+81", "\uD83C\uDDEF\uD83C\uDDF5", "JP"),
        Country("Kenya", "+254", "\uD83C\uDDF0\uD83C\uDDEA", "KE"),
        Country("Mexico", "+52", "\uD83C\uDDF2\uD83C\uDDFD", "MX"),
        Country("Netherlands", "+31", "\uD83C\uDDF3\uD83C\uDDF1", "NL"),
        Country("Nigeria", "+234", "\uD83C\uDDF3\uD83C\uDDEC", "NG"),
        Country("Pakistan", "+92", "\uD83C\uDDF5\uD83C\uDDF0", "PK"),
        Country("Philippines", "+63", "\uD83C\uDDF5\uD83C\uDDED", "PH"),
        Country("Russia", "+7", "\uD83C\uDDF7\uD83C\uDDFA", "RU"),
        Country("Saudi Arabia", "+966", "\uD83C\uDDF8\uD83C\uDDE6", "SA"),
        Country("Singapore", "+65", "\uD83C\uDDF8\uD83C\uDDEC", "SG"),
        Country("South Africa", "+27", "\uD83C\uDDFF\uD83C\uDDE6", "ZA"),
        Country("South Korea", "+82", "\uD83C\uDDF0\uD83C\uDDF7", "KR"),
        Country("Spain", "+34", "\uD83C\uDDEA\uD83C\uDDF8", "ES"),
        Country("Turkey", "+90", "\uD83C\uDDF9\uD83C\uDDF7", "TR"),
        Country("United Arab Emirates", "+971", "\uD83C\uDDE6\uD83C\uDDEA", "AE"),
        Country("United Kingdom", "+44", "\uD83C\uDDEC\uD83C\uDDE7", "GB"),
        Country("United States", "+1", "\uD83C\uDDFA\uD83C\uDDF8", "US"),
        Country("Vietnam", "+84", "\uD83C\uDDFB\uD83C\uDDF3", "VN"),
    )

private val DefaultCountry: Country = SanchrCountries.first { it.iso == "US" }

/**
 * Resolve the initial country for the picker using only permission-free
 * telephony lookups: [TelephonyManager.networkCountryIso] first (tracks the
 * actively-attached network, most accurate for travellers) and then
 * [TelephonyManager.simCountryIso] (MCC baked into the SIM). If neither
 * yields a match against [SanchrCountries], fall back to US.
 *
 * Callers pass this as the seed; because it's a one-shot read, updating the
 * SIM mid-flow won't re-resolve — that's an acceptable trade for avoiding a
 * broadcast receiver.
 */
fun resolveDefaultCountry(context: Context): Country {
    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return DefaultCountry
    val iso =
        tm.networkCountryIso
            ?.takeIf { it.isNotBlank() }
            ?: tm.simCountryIso?.takeIf { it.isNotBlank() }
            ?: return DefaultCountry
    val upper = iso.uppercase()
    return SanchrCountries.firstOrNull { it.iso == upper } ?: DefaultCountry
}

/**
 * Reusable country-code picker pill. Renders a tappable Surface that shows
 * [selected]'s flag + dial code; tapping opens a bottom-sheet with a search
 * field over [SanchrCountries] and invokes [onSelected] with the chosen
 * country. The sheet auto-dismisses on selection.
 *
 * This composable is stateless in its selection — the caller owns the
 * selected [Country] (usually mirrored into ViewModel state via the
 * existing `onPhoneChanged(countryCode, phone)` hook) and provides the
 * initial value via [resolveDefaultCountry].
 *
 * Public API preserved (H4): the inline-chip variant lives alongside in
 * [CountryCodePickerInlineChip] and shares the same bottom-sheet body.
 */
@Composable
fun CountryCodePicker(
    selected: Country,
    onSelected: (Country) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheetOpen by remember { mutableStateOf(false) }

    Surface(
        modifier =
            modifier
                .clickable { sheetOpen = true },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Text(text = selected.flagEmoji, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = selected.dialCode,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = "Change country",
                modifier = Modifier.size(20.dp),
            )
        }
    }

    if (sheetOpen) {
        CountryCodePickerSheet(
            onSelected = onSelected,
            onDismiss = { sheetOpen = false },
        )
    }
}

/**
 * Inline chip variant matching iOS `LoginView.swift:107-138` Menu button:
 * 88dp wide x 60dp tall, [Icons.Filled.Public] globe icon (18dp,
 * [SanchrIndigo500] tint), country dial code (`labelLarge`, `onSurface`),
 * and a 1×28dp trailing divider painted with `LocalSanchrSurfaces.line`.
 *
 * Designed to sit inside the same `RoundedCornerShape(20.dp)` container as
 * the phone `TextField` so the whole row reads as a single visual unit
 * exactly as iOS renders it. Tapping opens the same bottom-sheet picker as
 * [CountryCodePicker] via the shared [CountryCodePickerSheet] helper.
 */
@Composable
fun CountryCodePickerInlineChip(
    selected: Country,
    onSelected: (Country) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    val lineColor = LocalSanchrSurfaces.current.line

    Box(
        modifier =
            modifier
                .width(88.dp)
                .height(60.dp)
                .clickable { sheetOpen = true },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Icon(
                imageVector = Icons.Filled.Public,
                contentDescription = "Change country",
                tint = SanchrIndigo500,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = selected.dialCode,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 1.dp)
                    .width(1.dp)
                    .height(28.dp)
                    .background(lineColor),
        )
    }

    if (sheetOpen) {
        CountryCodePickerSheet(
            onSelected = onSelected,
            onDismiss = { sheetOpen = false },
        )
    }
}

/**
 * Shared bottom-sheet implementation used by both [CountryCodePicker] and
 * [CountryCodePickerInlineChip]. Owns its own [SheetState] and dismiss
 * animation; [onSelected] fires once the user taps a country, after which
 * the sheet hides itself and notifies via [onDismiss].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryCodePickerSheet(
    onSelected: (Country) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        CountryPickerSheetContent(
            onPick = { country ->
                onSelected(country)
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    if (!sheetState.isVisible) onDismiss()
                }
            },
        )
    }
}

@Composable
private fun CountryPickerSheetContent(onPick: (Country) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered =
        remember(query) {
            if (query.isBlank()) {
                SanchrCountries
            } else {
                val q = query.trim()
                SanchrCountries.filter { country ->
                    country.name.contains(q, ignoreCase = true) ||
                        country.dialCode.contains(q) ||
                        country.iso.contains(q, ignoreCase = true)
                }
            }
        }

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Search country or code") },
        leadingIcon = {
            Icon(imageVector = Icons.Filled.Search, contentDescription = null)
        },
        singleLine = true,
    )

    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        items(items = filtered, key = { it.iso }) { country ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(country) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = country.flagEmoji, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = country.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = country.dialCode,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Convenience helper: find the [Country] whose [Country.dialCode] matches
 * [dialCode] exactly. Used by screens that store the dial code (not the
 * full Country object) in ViewModel state, so they can re-hydrate the
 * picker selection on recomposition. Returns null when the dial code is
 * shared by multiple countries (e.g. "+1" for US/CA) — callers should
 * handle that by preferring a known default.
 */
fun findCountryByDialCode(dialCode: String): Country? = SanchrCountries.singleOrNull { it.dialCode == dialCode }
