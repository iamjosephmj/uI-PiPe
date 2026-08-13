package tech.ssemaj.pipe.transport;

/** Host's handle to control the live pane. */
interface IEmbedSession {
    oneway void resize(int widthPx, int heightPx);
    oneway void close();
    /** API 30–34 only: the host forwards touch here (no public input-token path exists pre-35). */
    oneway void dispatchInput(in android.view.MotionEvent event);
}
