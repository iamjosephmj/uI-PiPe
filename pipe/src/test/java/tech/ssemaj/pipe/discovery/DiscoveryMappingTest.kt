package tech.ssemaj.pipe.discovery

import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoveryMappingTest {
    @Test fun buildsDescriptorsWithCerts() {
        val out = buildDescriptors(
            listOf(ResolvedService("com.p", "com.p.Svc", "Prov")),
            certOf = { if (it == "com.p") listOf("aa11") else emptyList() },
        )
        assertEquals(1, out.size)
        assertEquals("com.p", out[0].packageName)
        assertEquals("com.p.Svc", out[0].component.serviceClass)
        assertEquals(listOf("aa11"), out[0].certSha256)
        assertEquals("Prov", out[0].label)
    }
}
