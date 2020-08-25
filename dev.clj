(require
  '[figwheel.main :as figwheel]
  '[example.start-dev :refer [-main]])

(-main)
(figwheel/-main "--build" "dev")

