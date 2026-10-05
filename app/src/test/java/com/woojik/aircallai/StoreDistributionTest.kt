package com.woojik.aircallai

import com.woojik.aircallai.distribution.StoreDistribution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoreDistributionTest {
    @Test fun productLinkRequiresIssuedPidAndPreservesLeadingZeros() {
        assertEquals("https://onesto.re/0000123456", StoreDistribution.oneStoreProductUrl("0000123456"))
        listOf("", "com.woojik.aircallai.release", "OA12345678", "12345", "12345678901",
            "12345/6789", "https://bad").forEach { assertNull(StoreDistribution.oneStoreProductUrl(it)) }
    }

    @Test fun developmentBuildNeverClaimsToBeStoreRelease() {
        assertEquals("개발용 앱", StoreDistribution.label("onestore", true))
        assertEquals("원스토어용 앱", StoreDistribution.label("onestore", false))
        assertEquals("Google Play용 앱", StoreDistribution.label("play", false))
        assertEquals("직접 설치용 앱", StoreDistribution.label("direct", false))
    }
}
