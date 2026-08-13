package tech.ssemaj.pipe.transport;

/** Host's handle to control the live full-screen pane. */
interface IEmbedSession {
    oneway void close();
}
