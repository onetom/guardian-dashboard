(ns guardian.dashboard.visualizations
  (:require
    [javelin.core    :refer [cell=]]
    [hoplon.core     :refer [defelem for-tpl]]
    [hoplon.svg      :refer [svg text g line path]]
    [hoplon.ui       :refer [elem]]
    [hoplon.ui.attrs :refer [r]]
    [hoplon.ui.elems :refer [in]]
    [cljsjs.d3]))

(defn scale-linear [domain range]
  (-> (.scaleLinear js/d3)
      (.domain (clj->js domain))
      (.range  (clj->js range))))

(defn scale-time [domain range]
  (-> (.scaleTime js/d3)
      (.domain (clj->js domain))
      (.range  (clj->js range))))

(def x (scale-linear [2 130] [0 800]))
(def y (scale-linear [0 100] [0 200]))

(prn :x (x 20) :y (y 20))

(defelem temp-chart [{:keys [values] :as attrs} _]
  (elem (dissoc attrs :values)
    (svg :svg/width "100%" :svg/height "100%"
      #_(text :svg/x 10 :svg/y 10 "hello")
      (line :svg/x1 10 :svg/y1 10  :svg/x2 400 :svg/y2 10 :svg/stroke "steelblue")
      #_(path :svg/stroke "steelblue" :svg/d (cell= (-> (.line js/d3) (.x (x values)) (.y (y values))))))))

(defelem dist-chart [{:keys [domain range] :as attrs} _]
  (let [total (cell= (apply + (mapv :value domain)))]
    (elem :p 10 (dissoc attrs :domain :range)
      (for-tpl [[{:keys [label value]} {:keys [color]}] (cell= (mapv vector domain (cycle range)))]
        (elem :sh (cell= (r value total)) :sv (r 1 1) :a :mid :c color
          (cell= (when (> value 5) label)))))))

(defelem hist-chart [{:keys [domain range s sv] :as attrs} _]
  (let [height (cell= (or sv s))]
    (elem :g 4 :a :end (dissoc attrs :domain :range)
      (for-tpl [[{:keys [label value color]} {c :color}] (cell= (mapv vector domain (cycle range)))]
        (elem :sv (cell= (+ value 6)) :sh (r 1 80) :r 2 :a :mid :c (cell= (or color c)) label)))))

#_(elem :s 300 :c grey-4 :b 10 :bc grey-5
  (for-tpl [{:keys [name temp threads]} (cell= (:cores data-model))]
    (elem :sh (cell= (r 1 (count (:cores data-model)))) :sv (r 1 1) :gh 8 :ah :mid :av :end
      (for-tpl [{:keys [name load]} threads]
        (elem :sh 4 :sv (cell= (+ (* load 3) 10)) :r 6 :c (cell= (condp < temp 40 :blue 50 :yellow :red)))))))

#_(defelem line-chart [{:keys [labels series styles] :as attrs} _]
  (with-let [e (elem (dissoc attrs :labels :series))]
    (doto (js/Chartist.Line. (in e) (clj->js {:labels labels :series series :pointSmooth false :lineSmooth false}))
      (.on "draw" #(when-let [style (some (fn [[k v]] (prn :k k :v v :t (.-type %)) (= (.-type %) (name k)) v) styles)]
        (.attr (.-element %) #js{:style style}))))))

#_(v/line-chart :sh (r 1 1) :sv (- (r 1 1) 30)
      :labels (->> (range) (take 11) (mapv #(str (* 10 %) "%")))
      :series [[0 5 8 10 8 10 8 10]]
      :styles {:line "stroke: red"})