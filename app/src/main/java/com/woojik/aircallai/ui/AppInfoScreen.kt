package com.woojik.aircallai.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
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
fun AppInfoScreen(onOpenAiSettings: () -> Unit, onOpenPrivacy: () -> Unit, onReport: () -> Unit) {
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
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("시작하기", style = MaterialTheme.typography.titleMedium)
                Text("앱 가입 없이 사용할 수 있습니다. 먼저 AI 및 모델 설정에서 응답 생성 방식을 준비해 주세요.")
                Text("로컬: 모델을 다운로드하고 적용한 뒤 사용합니다. 다운로드 용량과 기기 메모리를 확인해 주세요.")
                Text("클라우드: 사용하는 AI 제공자의 API 키를 저장합니다. API 이용 요금은 해당 제공자의 정책에 따릅니다.")
                Text("GitHub·Google 연결은 선택입니다. Google 연결에는 Google Play 서비스가 필요합니다.")
                Text("음성 대화에는 마이크 권한과 Android 음성 서비스가 필요합니다. 음성 기능은 인터넷을 사용할 수 있습니다.")
                TextButton(onClick = onOpenAiSettings) { Text("AI 및 모델 설정") }
            }
        }
        Text("AI가 생성한 답변은 틀릴 수 있습니다. 잘못된 응답은 응답의 신고 버튼으로 알려 주세요.")
        if (BuildConfig.AIRCALL_DEVELOPER_NAME.isNotBlank()) Text("개발자: ${BuildConfig.AIRCALL_DEVELOPER_NAME}")
        if (BuildConfig.AIRCALL_SUPPORT_EMAIL.isNotBlank()) Text("문의: ${BuildConfig.AIRCALL_SUPPORT_EMAIL}")
        TextButton(onClick = onOpenPrivacy) { Text("개인정보 및 데이터 삭제") }
        TextButton(onClick = onReport) { Text("문의 및 AI 응답 신고") }
        if (BuildConfig.AIRCALL_DISTRIBUTION_CHANNEL == "onestore" && !BuildConfig.DEBUG) {
            StoreDistribution.oneStoreProductUrl(BuildConfig.AIRCALL_ONESTORE_PRODUCT_ID)?.let { url ->
                TextButton(onClick = { openUrl(url) }) { Text("원스토어에서 업데이트 확인") }
            }
        }
        status?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
