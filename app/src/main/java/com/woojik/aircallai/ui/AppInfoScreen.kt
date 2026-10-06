package com.woojik.aircallai.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.woojik.aircallai.BuildConfig
import com.woojik.aircallai.distribution.StoreDistribution

@Composable
fun AppInfoScreen(onOpenAiSettings: () -> Unit, onOpenPrivacy: () -> Unit) {
    val context = LocalContext.current
    var status by remember { mutableStateOf<String?>(null) }
    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { status = "페이지를 열 수 없습니다. 사용할 브라우저를 확인해 주세요." }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("AirCall AI", style = MaterialTheme.typography.headlineSmall)
        Text("버전 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        Text(StoreDistribution.label(BuildConfig.AIRCALL_DISTRIBUTION_CHANNEL, BuildConfig.DEBUG),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onOpenAiSettings) { Text("AI 및 모델 설정") }
        if (BuildConfig.AIRCALL_DEVELOPER_NAME.isNotBlank()) Text("개발자: ${BuildConfig.AIRCALL_DEVELOPER_NAME}")
        if (BuildConfig.AIRCALL_SUPPORT_EMAIL.isNotBlank()) Text("문의: ${BuildConfig.AIRCALL_SUPPORT_EMAIL}")
        TextButton(onClick = onOpenPrivacy) { Text("개인정보 및 데이터 삭제") }
        SupportContactButton()
        if (BuildConfig.AIRCALL_DISTRIBUTION_CHANNEL == "onestore" && !BuildConfig.DEBUG) {
            StoreDistribution.oneStoreProductUrl(BuildConfig.AIRCALL_ONESTORE_PRODUCT_ID)?.let { url ->
                TextButton(onClick = { openUrl(url) }) { Text("원스토어에서 업데이트 확인") }
            }
        }
        status?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
