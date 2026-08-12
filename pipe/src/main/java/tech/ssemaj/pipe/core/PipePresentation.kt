package tech.ssemaj.pipe.core

/** How the host presents a pane. Chosen by the host; readable by the provider via [PipeRequest]. */
enum class PipePresentation { EMBEDDED, FULL_SCREEN, DIALOG }
