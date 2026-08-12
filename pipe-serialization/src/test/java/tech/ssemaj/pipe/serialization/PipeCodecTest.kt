package tech.ssemaj.pipe.serialization

import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Serializable data class Sample(val n: Int, val s: String)
@Serializable data class Other(val x: Boolean)

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class PipeCodecTest {
    @Test fun roundTrips() {
        val msg = PipeCodec.encode(Sample(7, "hi"))
        assertEquals(Sample(7, "hi"), PipeCodec.decodeOrNull<Sample>(msg))
    }
    @Test fun wrongTypeDecodesNull() {
        assertNull(PipeCodec.decodeOrNull<Other>(PipeCodec.encode(Sample(1, "a"))))
    }
}
