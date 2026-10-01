package com.woojik.aircallai

import android.app.Application

/**
 * Application entry point.
 * PRD-05: UI(Activity)와 Foreground Service가 공유하는 AppGraph의 구성 루트.
 * 산발적 전역 Singleton이 아니라 App 수명주기를 따르는 단일 구성 루트다.
 */
class AirCallApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}
