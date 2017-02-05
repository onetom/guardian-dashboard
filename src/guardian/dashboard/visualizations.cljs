(ns guardian.dashboard.visualizations
  (:require
    [javelin.core           :refer [cell= with-let]]
    [hoplon.core            :refer [defelem for-tpl]]
    [hoplon.svg             :refer [text g path rect line]]
    [hoplon.ui              :refer [elem svg]]
    [hoplon.ui.attrs        :refer [r translate hsl lgr]]
    [hoplon.ui.elems        :refer [in]]
    [cljsjs.d3]))

;;; utils ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn duration! [scale data accessor mins]
  (let [max (when data (.max js/d3 data accessor))]
    (.domain scale #js [(.offset js/d3.timeMinute max (- mins)) max])))

;;; charts ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defelem grid [{:keys [eh ev xticks yticks]}]
  (list
    (for-tpl [y yticks]
      (line :x1 0 :x2 eh :y1 y :y2 y :stroke "#202020" :stroke-width 3))
    (for-tpl [x xticks]
      (line :x1 x :x2 x :y1 0 :y2 ev :stroke "#202020" :stroke-width 3))))

(defelem gauge [{:keys [data yfn] :as attrs}]
  (let [e 250
        y (-> (.scaleLinear js/d3) (.domain #js[20 60]) (.rangeRound #js[e 0]))
        h (cell= (when data (-> data yfn y)))]
    (svg :view-box [0 0 e e] (dissoc attrs :data :yf)
      (g :transform (translate 0 -2)
        (for-tpl [[yd yr] (cell= (mapv #(vector % (y %)) (.ticks y 10)))]
          (list
            (line :x1 0 :x2 12 :y1 yr :y2 yr :stroke "#202020" :stroke-width 3)
            (text :x 4 :y (cell= (+ yr 2)) :font-size 12
              (cell= (str yd "°"))))))
         (rect :x 0 :y h :width e :height h :fill "steelblue" :fill-opacity 0.5))))

(defelem histogram [{:keys [data xfn yfn] :as attrs}]
  (let [eh 800 ev 200
        x (-> (.scaleTime   js/d3)                      (.rangeRound #js[0 eh]))
        y (-> (.scaleLinear js/d3) (.domain #js[20 60]) (.rangeRound #js[ev 0]))
        l (-> (.area        js/d3) (.x (comp x xfn)) (.y0 (y 0)) (.y1 (comp y yfn)))]
    (cell= (duration! x data xfn 2))
    (svg :view-box [0 0 eh ev] (dissoc attrs :data :xfn :yfn)
      (path :d (cell= (when data (l data))) :fill "steelblue")
      (grid :eh eh :ev ev :xticks (cell= (mapv x (.ticks x (.every js/d3.timeSecond 10)))) :yticks (cell= (mapv y (.ticks y 11)))))))

#_(defelem stack-chart [{:keys [data xfn yfn zfn] :as attrs}]
  (let [eh 800 ev 200
        x (-> (.scaleTime    js/d3) (.domain #js[#inst"2017-01-26T00:00:00" #inst"2017-01-26T00:02:00"]) (.rangeRound #js[0 eh])) ;; todo: time extent
        y (-> (.scaleLinear  js/d3) (.domain #js[20 60])                                                 (.rangeRound #js[ev 0]))
        z (-> (.scaleOrdinal js/d3) (.-schemeCategory10 js/d3))
        l (-> (shape/area) (.x (comp x xfn)) (.y0 (y 0)) (.y1 (comp y yfn)))]
    (cell= (duration! x data xfn 2))
    (elem :d :pile
      (elem :s (r 1 1)
        "thing")
      (svg :s (r 1 1) :view-box [0 0 eh ev] (dissoc attrs :data)
        (path :d (cell= (l data)) :fill "steelblue")))))

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
