package org.jellyfin.androidtv.ui.preference.category

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Text

@Composable
fun DonateDialog(onDismiss: () -> Unit) {
	val closeFocusRequester = remember { FocusRequester() }

	Dialog(
		onDismissRequest = onDismiss,
		properties = DialogProperties(usePlatformDefaultWidth = false),
	) {
		Box(
			modifier = Modifier.fillMaxSize(),
			contentAlignment = Alignment.Center,
		) {
			Column(
				horizontalAlignment = Alignment.CenterHorizontally,
				modifier = Modifier
					.widthIn(min = 340.dp, max = 480.dp)
					.clip(RoundedCornerShape(20.dp))
					.background(Color(0xE6141414))
					.border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
					.padding(vertical = 20.dp)
					.verticalScroll(rememberScrollState()),
			) {
				// Title
				Text(
					text = stringResource(R.string.donate_dialog_title),
					fontSize = 20.sp,
					fontWeight = FontWeight.W600,
					color = Color.White,
					textAlign = TextAlign.Center,
					modifier = Modifier
						.padding(horizontal = 24.dp)
						.padding(bottom = 12.dp),
				)

				// Divider
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.height(1.dp)
						.background(Color.White.copy(alpha = 0.08f)),
				)

				Spacer(modifier = Modifier.height(16.dp))

				// Message
				Text(
					text = stringResource(R.string.donate_dialog_message),
					fontSize = 15.sp,
					color = Color.White.copy(alpha = 0.7f),
					textAlign = TextAlign.Center,
					lineHeight = 22.sp,
					modifier = Modifier.padding(horizontal = 24.dp),
				)

				Spacer(modifier = Modifier.height(20.dp))

				// QR Code
				Box(
					modifier = Modifier
						.size(260.dp)
						.clip(RoundedCornerShape(12.dp))
						.background(Color.White)
						.padding(12.dp),
					contentAlignment = Alignment.Center,
				) {
					Image(
						painter = painterResource(R.drawable.qr_code),
						contentDescription = "Donation QR Code",
						contentScale = ContentScale.Fit,
						modifier = Modifier.fillMaxSize(),
					)
				}

				Spacer(modifier = Modifier.height(12.dp))

				// URL
				Text(
					text = "buymeacoffee.com/moonfin",
					fontSize = 14.sp,
					color = Color(0xFF00A4DC),
					fontFamily = FontFamily.Monospace,
					textAlign = TextAlign.Center,
					modifier = Modifier.padding(horizontal = 24.dp),
				)

				Spacer(modifier = Modifier.height(16.dp))

				// Thanks
				Text(
					text = stringResource(R.string.donate_dialog_thanks),
					fontSize = 13.sp,
					color = Color.White.copy(alpha = 0.5f),
					textAlign = TextAlign.Center,
					modifier = Modifier.padding(horizontal = 24.dp),
				)

				Spacer(modifier = Modifier.height(20.dp))

				// Divider
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.height(1.dp)
						.background(Color.White.copy(alpha = 0.08f)),
				)

				Spacer(modifier = Modifier.height(16.dp))

				// Close button
				GlassDialogButton(
					text = "Close",
					onClick = onDismiss,
					modifier = Modifier
						.padding(horizontal = 24.dp)
						.focusRequester(closeFocusRequester),
				)
			}
		}
	}

	LaunchedEffect(Unit) {
		closeFocusRequester.requestFocus()
	}
}

@Composable
fun GlassDialogButton(
	text: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	isPrimary: Boolean = false,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val isFocused by interactionSource.collectIsFocusedAsState()

	val bgColor = when {
		isFocused -> if (isPrimary) Color(0xFF00A4DC) else Color.White.copy(alpha = 0.15f)
		isPrimary -> Color(0xFF00A4DC).copy(alpha = 0.8f)
		else -> Color.White.copy(alpha = 0.06f)
	}
	val textColor = when {
		isFocused && isPrimary -> Color.White
		isFocused -> Color.White
		isPrimary -> Color.White
		else -> Color.White.copy(alpha = 0.7f)
	}

	Box(
		modifier = modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(10.dp))
			.background(bgColor)
			.border(
				1.dp,
				if (isFocused) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f),
				RoundedCornerShape(10.dp),
			)
			.clickable(interactionSource = interactionSource, indication = null) { onClick() }
			.focusable(interactionSource = interactionSource)
			.padding(vertical = 12.dp),
		contentAlignment = Alignment.Center,
	) {
		Text(
			text = text,
			fontSize = 15.sp,
			fontWeight = FontWeight.W600,
			color = textColor,
		)
	}
}
