# Main content input regression

## Root cause

The floating assistant's root `AnchorPane` was changed from a compact 420 x 620 maximum to `Infinity` for both maximum dimensions. `assistantLayer` is stacked over `contentScroll` and covers the central viewport. As a result, JavaFX resized the assistant root to the full content viewport (observed at 948 x 737). Although the pane had no visible background and `pickOnBounds="false"`, JavaFX still returned the root pane itself from hit testing across this layout. Live scene events showed main-page scrolls and clicks targeting `AnchorPane#dock`, so they never reached the underlying controls or `contentScroll`. The sidebar remained responsive because it is outside that overlay stack.

## Layout invariant

The assistant layer may span the central viewport, but the interactive assistant root must stay bounded to its compact dock size (`maxWidth=420`, `maxHeight=620`) and all transparent wrapper panes must use `pickOnBounds="false"`. The dock is bottom-right aligned, its controls occupy only their own bounds, and unoccupied central-page coordinates must hit `contentScroll` or the page control beneath it. The shell keeps one main vertical `ScrollPane` and a separate sidebar `ScrollPane`; page-root `ScrollPane`s are unwrapped into the shell viewport by `SceneNavigator`.

`MainLayoutInvariantTest` guards the finite assistant bounds, click-through overlay properties, and the two independent shell scroll panes. `SceneNavigatorLayoutTest` guards page-root scroll-pane unwrapping.
