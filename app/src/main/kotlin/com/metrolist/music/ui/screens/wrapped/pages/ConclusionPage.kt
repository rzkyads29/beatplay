package com.metrolist.music.ui.screens.wrapped.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.R
import com.metrolist.music.ui.screens.beatplay.BeatPlayGreen
import com.metrolist.music.ui.screens.wrapped.WrappedConstants
import com.metrolist.music.ui.screens.wrapped.components.AnimatedBackground
import com.metrolist.music.ui.screens.wrapped.components.ShapeType
import com.metrolist.music.ui.screens.wrapped.components.WrappedEyebrow
import com.metrolist.music.ui.screens.wrapped.components.WrappedPrimaryButton

@Composable
fun ConclusionPage(onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedBackground(elementCount = 30, shapeTypes = listOf(ShapeType.Circle, ShapeType.Line))
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            WrappedEyebrow(text = "BEATPLAY WRAPPED ${WrappedConstants.YEAR}", color = BeatPlayGreen)
            Spacer(modifier = Modifier.height(20.dp))
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = stringResource(R.string.utaloom_logo_content_description),
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp))
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.wrapped_thank_you),
                style = TextStyle(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.utaloom_wrapped_special_thanks),
                style = TextStyle(
                    fontSize = 16.sp,
                    color = Color.Gray
                )
            )
            Spacer(modifier = Modifier.height(48.dp))
            WrappedPrimaryButton(
                onClick = onClose,
                contentPadding = PaddingValues(horizontal = 30.dp, vertical = 13.dp),
            ) {
                Text(
                    text = stringResource(R.string.wrapped_close),
                    style = TextStyle(
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}
