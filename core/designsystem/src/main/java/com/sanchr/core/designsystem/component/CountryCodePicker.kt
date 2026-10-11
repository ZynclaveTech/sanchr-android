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

private val DefaultCountry: Country by lazy { SanchrCountries.first { it.iso == "US" } }

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
 * The [Country] a stored selection refers to.
 *
 * Screens keep the dial code in view-model state rather than the whole
 * `Country`, and a dial code is not unique: `+1` is both the United States
 * and Canada. The previous version used `singleOrNull`, which returned null
 * for exactly that case, so selecting either one fell through to the SIM
 * country — the chip showed one country while the state held another, and the
 * number submitted did not match what was on screen.
 *
 * [iso] disambiguates when the caller knows it. Without it, the first match in
 * [SanchrCountries] wins, which is stable and keeps the chip and the state
 * agreeing even when neither is the user's country.
 */
fun findCountryByDialCode(
    dialCode: String,
    iso: String? = null,
): Country? =
    iso?.let { wanted -> SanchrCountries.firstOrNull { it.iso == wanted && it.dialCode == dialCode } }
        ?: SanchrCountries.firstOrNull { it.dialCode == dialCode }
