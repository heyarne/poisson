(ns poisson.core
  (:require [quil.core :as q :include-macros true]
            [quil.middleware :as m]))

(def r 13) ;; minimum distance between samples
(def k 30) ;; limit of samples to choose before rejection
(def n 2)  ;; number of dimensions
(def w (/ r (Math/sqrt n)))

(defn grid-idx
  "Returns the horizontal or vertical grid index for a coordinate"
  [a]
  (Math/floor (/ a w)))

(defn make-grid
  "Initialize our background grid"
  [width height]
  (let [ncols (inc (grid-idx width))
        nrows (inc (grid-idx height))]
    (vec (repeat (* ncols nrows) nil))))

(defn into-grid
  "Inserts a point at the correct location in the grid"
  [grid width pos]
  (let [[x y] pos
        idx (+ (grid-idx x) (* (grid-idx y) (grid-idx width)))]
    (assoc grid idx pos)))

(defn neighbors
  "Returns non-empty cells in a 3x3 neighborhood"
  [grid width height [x y]]
  (let [c (grid-idx x)
        r (grid-idx y)
        cols (inc (grid-idx width))
        rows (inc (grid-idx height))]
    (->>
     (for [j (range (max 0 (dec r)) (min rows (+ r 2)))
           i (range (max 0 (dec c)) (min cols (+ c 2)))]
       [i j])
     (keep (fn [[i j]]
             (nth grid (+ i (* j (dec cols)))))))))

(defn rand-around
  "Generates in a given distance around a point"
  [[x y] min-dist max-dist]
  (let [dist (+ min-dist (rand (- max-dist min-dist)))
        a (rand (* Math/PI 2))
        off-x (* dist (Math/cos a))
        off-y (* dist (Math/sin a))]
    [(+ x off-x) (+ y off-y)]))

(defn distance
  "Calculates the distance between two two-dimensional vectors"
  [[a1 a2] [b1 b2]]
  (Math/sqrt (* (- a1 b1) (- a1 b1)) (* (- a2 b2) (- a2 b2))))

(defn init-with-point
  "Returns the initial state"
  [pos width height]
  {:grid (into-grid (make-grid width height) width pos)
   :active #{pos}})

(defn setup []
  (q/frame-rate 60)
  (q/color-mode :hsb)
  (let [width (q/width)
        height (q/height)
        pos [(q/random width) (q/random height)]]
    (init-with-point pos width height)))

(defn pick-samples [state]
  ;; while the active list is not empty...
  (if (seq (:active state))
    (let [width (q/width)
          height (q/height)
          chosen (rand-nth (seq (:active state)))
          next (->>
                ;; generate k points
                (repeatedly #(rand-around chosen r (* 2 r)))
                ;; keep only the ones in our screen space
                (filter (fn [[x y]]
                          (and (<= 0 x width)
                               (<= 0 y height))))
                (take k)
                ;; for each point, check if it is within distance r of existing
                ;; samples if it is far enough, emit it as a sample and add it
                ;; to the active list
                (reduce (fn [state sample]
                          (let [neighborhood (neighbors (:grid state) width height sample)]
                            (if (every? #(>= (distance sample %) r) neighborhood)
                              (-> state
                                  (update :grid into-grid width sample)
                                  (update :active conj sample))
                              state)))
                        state))]
      ;; if after k attempts no such point is found, instead remove our random
      ;; active point
      (if (= state next)
        (update state :active disj chosen)
        next))
    state))

(defn update-state [state]
  (nth (iterate pick-samples state) 25))

(defn reset-state [state event]
  (init-with-point [(:x event) (:y event)] (q/width) (q/height)))

(defn draw-state [{:keys [active grid]}]
  (q/background 20 180 200)
  (q/stroke-weight 4)
  (q/stroke 20 200 120)
  (doseq [pt (remove nil? grid)]
    (when-not (active pt)
      (apply q/point pt)))
  (q/stroke 255)
  (doseq [pt active]
    (apply q/point pt)))

(defn fullscreen []
  [(.. js/document -body -offsetWidth) (.. js/document -body -offsetHeight)])

; this function is called in index.html
(defn ^:export run-sketch []
  (q/defsketch poisson
    :host "poisson"
    :size (fullscreen)
    :setup setup
    :update update-state
    :mouse-pressed reset-state
    :draw draw-state
    :renderer :p2d
    :middleware [m/fun-mode]))

; uncomment this line to reset the sketch on save
; (run-sketch)
