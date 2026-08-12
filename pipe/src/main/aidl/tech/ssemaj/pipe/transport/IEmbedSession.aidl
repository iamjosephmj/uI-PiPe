package tech.ssemaj.pipe.transport;

/** Host's handle to control the live pane. */
interface IEmbedSession {
    oneway void resize(int widthPx, int heightPx);
    oneway void close();
}
