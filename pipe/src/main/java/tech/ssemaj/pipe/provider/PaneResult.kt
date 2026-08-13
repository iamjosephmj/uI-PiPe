package tech.ssemaj.pipe.provider

/** Result of [PipeProviderService.onOpenPane]: either build a pane, or reject the request. */
sealed interface PaneResult {
    data class Content(val content: PipeContent) : PaneResult
    data class Reject(val reason: String) : PaneResult
}
