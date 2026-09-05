package com.sanchr.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.common.support.SupportLinks
import com.sanchr.core.designsystem.component.CountryCodePickerInlineChip
import com.sanchr.core.designsystem.component.SanchrGradientButton
import com.sanchr.core.designsystem.component.findCountryByDialCode
import com.sanchr.core.designsystem.component.resolveDefaultCountry
import com.sanchr.core.designsystem.theme.LocalSanchrSurfaces
import com.sanchr.core.designsystem.theme.SanchrIndigo500
import com.sanchr.core.designsystem.theme.SanchrTheme

/**
 * Phone-only entry. Android counterpart of iOS `LoginView` phone section
 * (`LoginView.swift` complete file, with the inline country menu at lines
 * 107-138 and the security card at lines 181-215). Observes
 * [AuthViewModel.state] and renders keyed off [AuthState.LoginPhone]; any
 * other state means the NavHost observer has already moved the flow forward,
 * so this composable renders an empty [Box] to avoid a last-frame flicker
 * during route transitions.
 *
 * Layout follows §6.1 of `docs/android/ios-pixel-parity-spec.md` literally:
 * 28dp horizontal screen padding; 54dp top spacer; 120dp logo with 28dp
 * corner radius; 40dp gap; displayMedium "Welcome to Sanchr"; 14dp gap;
 * bodyMedium tagline; 44dp gap; phone section (label + 14dp + 60dp tall
 * country-chip+TextField row inside RoundedCornerShape(20.dp) with 1.2dp
 * border and 18dp shadow at black/0.04); 18dp gap; security card
 * (RoundedCornerShape(24.dp), 18×20dp padding, primary/0.12 border, 52dp
 * tinted lock square + headlineSmall + bodyMedium); 28dp gap;
 * [SanchrGradientButton] Continue CTA; 24dp gap; combined privacy line;
 * 24dp bottom spacer.
 *
 * Country chip is the new [CountryCodePickerInlineChip] which embeds inside
 * the phone-field surface — matches iOS `LoginView.swift:106-147` where the
 * Menu button sits in the same RoundedCornerShape(20) container as the
 * TextField, separated by a 1×28dp `SanchrLine` divider.
 *
 * Privacy footer collapses to a single sentence ("By continuing, you agree
 * to our Privacy Policy and Terms of Service") matching iOS
 * `LoginView.swift:252-258`. The previous Material-style two-button row was
 * removed in H4 to honor pixel parity; tappable links are deferred.
 */
@Composable
fun LoginPhoneScreen(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val deviceDefault = remember(context) { resolveDefaultCountry(context) }

    val loginPhone: AuthState.LoginPhone =
        when (val s = state) {
            is AuthState.LoginPhone -> s
            is AuthState.Error ->
                s.previousState as? AuthState.LoginPhone ?: run {
                    Box(modifier = modifier.fillMaxSize())
                    return
                }
            else -> {
                Box(modifier = modifier.fillMaxSize())
                return
            }
        }

    LaunchedEffect(deviceDefault.dialCode) {
        if (loginPhone.phone.isEmpty() &&
            loginPhone.countryCode == "+1" &&
            deviceDefault.dialCode != "+1"
        ) {
            viewModel.onLoginPhoneChanged(deviceDefault.dialCode, "")
        }
    }

    val selectedCountry =
        findCountryByDialCode(loginPhone.countryCode)
            ?: deviceDefault.takeIf { it.dialCode == loginPhone.countryCode }
            ?: deviceDefault

    val errorMessage = (state as? AuthState.Error)?.message
    val isPhoneValid = loginPhone.phone.length >= MIN_CONTINUE_DIGITS
    val darkTheme = isSystemInDarkTheme()
    val surfaces = LocalSanchrSurfaces.current

    // Hoist shape + border-color allocations out of the recomposing modifier
    // chain. Without `remember`, every keystroke into the phone field rebuilds
    // a fresh RoundedCornerShape (consumed by shadow/clip/border) plus a fresh
    // Color, allocating ~5 modifier nodes per character. Composable accessors
    // (theme spacing, MaterialTheme error color) are captured into locals
    // first so the `remember` calculation lambdas (which are
    // @DisallowComposableCalls) only see plain values.
    val phoneFieldRadius = SanchrTheme.spacing.phoneFieldRadius
    val cardCorner = SanchrTheme.spacing.cardCorner
    val errorColor = MaterialTheme.colorScheme.error
    val lineColor = surfaces.line
    val fieldShape = remember(phoneFieldRadius) { RoundedCornerShape(phoneFieldRadius) }
    val borderColor =
        remember(errorMessage, errorColor, lineColor) {
            if (errorMessage != null) errorColor.copy(alpha = 0.35f) else lineColor
        }
    val logoShape = remember(cardCorner) { RoundedCornerShape(cardCorner) }
    Scaffold(modifier = modifier) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp)
                    .imePadding(),
        ) {
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.heroTopGap))

            // Hero — iOS LoginView.swift:70-95.
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(id = com.sanchr.core.designsystem.R.drawable.sanchr_logo),
                    contentDescription = "Sanchr logo",
                    modifier =
                        Modifier
                            .size(120.dp)
                            .clip(logoShape),
                )
                Spacer(modifier = Modifier.height(40.dp))
                Text(
                    text = "Welcome to Sanchr",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Encrypted. Synced. Secure.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.heroBottomGap))

            // Phone section — iOS LoginView.swift:97-179.
            Text(
                text = "Phone Number",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 18.dp,
                            shape = fieldShape,
                            ambientColor = Color.Black.copy(alpha = 0.04f),
                            spotColor = Color.Black.copy(alpha = 0.04f),
                        ).clip(fieldShape)
                        .background(surfaces.surface)
                        .border(width = 1.2.dp, color = borderColor, shape = fieldShape)
                        .defaultMinSize(minHeight = 60.dp),
            ) {
                CountryCodePickerInlineChip(
                    selected = selectedCountry,
                    onSelected = { country ->
                        viewModel.onLoginPhoneChanged(country.dialCode, loginPhone.phone)
                    },
                )
                BasicTextField(
                    value = loginPhone.phone,
                    onValueChange = { viewModel.onLoginPhoneChanged(loginPhone.countryCode, it) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .padding(horizontal = 18.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    textStyle =
                        LocalTextStyle.current.merge(
                            MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onBackground,
                            ),
                        ),
                    cursorBrush = SolidColor(SanchrIndigo500),
                    decorationBox = { inner ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (loginPhone.phone.isEmpty()) {
                                Text(
                                    text = "(555) 123-4567",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        }
                    },
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "We'll send you a verification code",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // SanchrGradientButton already gates clicks via `enabled && !isLoading`
            // and swaps in a spinner when isLoading=true (commit 69fc6dc); passing
            // `!isSubmitting` here would double-fade the spinner with disabled-alpha.
            // retry() unwraps Error → LoginPhone so submit can re-enter the validation gate.
            SanchrGradientButton(
                text = "Continue",
                onClick = {
                    if (state is AuthState.Error) viewModel.retry()
                    viewModel.submitLoginPhone()
                },
                enabled = isPhoneValid,
                isLoading = loginPhone.isSubmitting,
                trailingIcon = Icons.AutoMirrored.Filled.ArrowForward,
            )

            Spacer(modifier = Modifier.height(24.dp))

            LegalConsentLine(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private const val MIN_CONTINUE_DIGITS = 7

/**
 * The consent line, with both documents openable. It used to be flat text
 * naming a policy the user had no way to read — the one place they are asked
 * to agree to it.
 */
@Composable
private fun LegalConsentLine(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val linkStyle = SpanStyle(color = SanchrIndigo500, textDecoration = TextDecoration.Underline)
    val text =
        buildAnnotatedString {
            append("By continuing, you agree to our ")
            withLink(
                LinkAnnotation.Clickable("privacy", TextLinkStyles(style = linkStyle)) {
                    SupportLinks.openUrl(context, SupportLinks.PRIVACY_POLICY)
                },
            ) {
                append("Privacy Policy")
            }
            append(" and ")
            withLink(
                LinkAnnotation.Clickable("terms", TextLinkStyles(style = linkStyle)) {
                    SupportLinks.openUrl(context, SupportLinks.TERMS_OF_SERVICE)
                },
            ) {
                append("Terms of Service")
            }
        }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}
