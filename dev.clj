(require
  '[figwheel.main :as figwheel]
  '[odoyle-frame.start-dev :refer [-main]])

(-main)
(figwheel/-main "--build" "dev")

