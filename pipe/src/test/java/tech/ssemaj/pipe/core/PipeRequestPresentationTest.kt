package tech.ssemaj.pipe.core

import android.os.Parcel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class PipeRequestPresentationTest {

    @Test fun defaultPresentationIsEmbedded() {
        assertEquals(PipePresentation.EMBEDDED, PipeRequest("a").presentation)
    }

    @Test fun parcelizePreservesPresentation() {
        val original = PipeRequest("a", presentation = PipePresentation.DIALOG)
        val parcel = Parcel.obtain()
        original.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        @Suppress("UNCHECKED_CAST")
        val restored = (PipeRequest::class.java.getField("CREATOR").get(null) as android.os.Parcelable.Creator<PipeRequest>).createFromParcel(parcel)
        parcel.recycle()
        assertEquals(PipePresentation.DIALOG, restored.presentation)
    }

    @Test fun forcePresentationOverrides() {
        assertEquals(PipePresentation.FULL_SCREEN,
            PipeRequest("a").forcePresentation(PipePresentation.FULL_SCREEN).presentation)
    }

    @Test fun forcePresentationReturnsSameInstanceWhenUnchanged() {
        val req = PipeRequest("a", presentation = PipePresentation.DIALOG)
        assertSame(req, req.forcePresentation(PipePresentation.DIALOG))
    }
}
