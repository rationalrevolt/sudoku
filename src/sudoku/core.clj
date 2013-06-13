(ns sudoku.core
  (:use [clojure.set :only (union difference)]))

(def ^:dynamic ^:private *random* false)

(def ^:private  all-values
  #{1 2 3 4 5 6 7 8 9})

(def ^:private  allowed?
  (conj all-values 0))

(defn- make-matrix [cols rows]
  (fn [xs]
    (loop [cnt 1, xs xs, mat []]
      (if (> cnt rows)
        mat
        (let [[rx rest] (split-at cols xs)
              rx        (vec rx)]
          (recur (inc cnt) rest (conj mat rx)))))))

(def ^:private make-9x9-board (make-matrix 9 9))

(def all-locations
  (for [r (range 9), c (range 9)]
    [r c]))

(defn make-board [xs]
  (let [xs (filter allowed? xs)]
    (make-9x9-board xs)))

(defn print-board [board]
  (doseq [row board]
    (println (vec (map #(if (pos? %) % \_) row)))))

(defn- in-row [row]
  (for [col (range 9)]
    [row col]))

(defn- in-col [col]
  (for [row (range 9)]
    [row col]))

(defn- same-box [col row]
  (let [start  #(* 3 (quot % 3))
        add-3  #(+ 3 %)
        row    (start row)
        col    (start col)]
    (for [r (range row (add-3 row))
          c (range col (add-3 col))]
      [r c])))

(defn- in-box [n]
  (let [col (* 3 (rem n 3))
        row (* 3 (quot n 3))]
    (same-box col row)))

(defn- values [locations board]
  (let [get-inb    (partial get-in board)
        not-empty? #(pos? %)
        vx         (map get-inb locations)
        vx         (filter not-empty? vx)]
    (set vx)))

(defn can-have [[row col] board]
  (let [row-vals  (values (in-row row) board)
        col-vals  (values (in-col col) board)
        box-vals  (values (same-box col row) board)
        used-vals (union row-vals col-vals box-vals)]
    (difference all-values used-vals)))

(defn blank-cells [board]
  (for [r     (range 9)
        c     (range 9)
        :when (= 0 (get-in board [r c]))]
    [r c]))

(defn non-blank-cells [board]
  (for [r     (range 9)
        c     (range 9)
        :when (not= 0 (get-in board [r c]))]
    [r c]))

(defn- sort-by-easy [prospects]
  (let [comparator (fn [c1 c2]
                     (let [c1v (count (:values c1))
                           c2v (count (:values c2))]
                       (compare c1v c2v)))]
    (sort comparator prospects)))

(defn- get-prospect [board]
  (let [blanks    (blank-cells board)
        prospects (for [cell blanks]
                    {:cell   cell
                     :values (can-have cell board)})
        prospects (sort-by-easy prospects)]
    (first prospects)))

(defn- none-empty? [board]
  (let [blanks (blank-cells board)
        result (zero? (count blanks))]
    result))

(defn solved? [board]
  (let [row-vals (map #(values (in-row %) board) (range 9))
        col-vals (map #(values (in-col %) board) (range 9))
        box-vals (map #(values (in-box %) board) (range 9))]
    (every? #(= % all-values) (concat row-vals col-vals box-vals))))

(defn- lazy-shuffle [xs]
  (let [make-lazy (fn make-lazy [xs]
                    (if-let [[x & xs] (seq xs)]
                      (cons x (lazy-seq (make-lazy xs)))))]
    (make-lazy (shuffle xs))))

(defn solve [board]
  (if-not (none-empty? board)
    (let [prospect (get-prospect board)
          cell     (:cell prospect)
          values   (:values prospect)]
      (if (empty? values)
        nil
        (let [solutions (for [v (if *random* (lazy-shuffle values) values)]
                          (let [new-board (assoc-in board cell v)]
                            (solve new-board)))
              solutions (filter identity solutions)]
          (first solutions))))
    board))

(def ^:private blank-board (make-board (repeat 0)))

(def difficulty
  {:easy     [3 5]
   :moderate [3 6]
   :hard     [4 6]})

(defn- n->cell [n]
  (let [row #(quot % n)
        col #(- % (* n (row %)))]
    (juxt row col)))

(defn- random-take [cnt coll]
  (take cnt (shuffle coll)))

(defn- random-cells [cnt]
  (let [locs  (random-take cnt (range 81))
        to9x9 (n->cell 9)]
    (for [p locs]
      (to9x9 p))))

(defn- random-cells-in-box [cnt box-num]
  (let [numbered-board (make-9x9-board (range 81))
        to3x3          (n->cell 3)
        to9x9          (n->cell 9)
        [row col]      (to3x3 box-num)
        [row col]      [(* row 3) (* col 3)]
        rng            (for [r (range row (+ row 3))
                             c (range col (+ col 3))]
                         (get-in numbered-board [r c]))]
    (map to9x9 (random-take cnt rng))))

(defn random-board
  ([] (random-board :easy))
  ([level] (let [board (binding [*random* true]
                         (solve blank-board))
                 cfn   (fn [n]
                         (let [vx  (difficulty level)
                               cnt (rand-nth vx)]
                           (random-cells-in-box cnt n)))
                 locs  (apply concat (map cfn (range 9)))]
             (loop [board board, locs locs]
               (if-let [[l & lx] locs]
                 (recur (assoc-in board l 0) lx)
                 board)))))

