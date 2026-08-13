package tech.ssemaj.pipe.provider

/** Result of [PipeProviderService.onOpenPane]: either build a pane, or reject the request. */
sealed interface PaneResult {
    /**
     * Show [content] as a pane. [spec] shapes the window (default: full-screen transparent). Any
     * enter/exit animation is the provider's own — animate the content (e.g. with Compose).
     */
    data class Content(
        val content: PipeContent,
        val spec: PaneSpec = PaneSpec(),
    ) : PaneResult

    data class Reject(val reason: String) : PaneResult
}
