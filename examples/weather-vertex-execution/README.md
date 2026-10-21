# Weather vertex execution

This small Iris-style fixture gives `gbuffers_weather.vsh` a flat varying that
the matching fragment shader consumes. The graphics smoke uses a rainy world
and checks that the vertex program is linked into the real precipitation
pipeline by reading back its magenta fragment signal. Disabling Veyra restores
the vanilla weather pipeline and removes that signal.
