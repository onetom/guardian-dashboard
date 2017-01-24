(ns guardian.dashboard.transformations
  (:require
    [clojure.set :refer [rename-keys]]
    [cljsjs.d3]))

;;; utils ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn hsl->rgb [[h s l]]
  (let [c (.rgb js/d3 (.hsl js/d3 h s l))]
    (mapv int [(.-r c) (.-g c) (.-b c)])))

(defn rgb->hsl [[r g b]]
  (let [c (.hsl js/d3 (.rgb js/d3 r g b))]
    (mapv int [(.-h c) (.-s c) (.-l c)])))

;;; xforms ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn xform-cpu [{:keys [name temps loads volts] :as cpu}]
  (when cpu
    (let [cores   (mapv (fn [t] (rename-keys t {:value :temp})) (remove #(= (:name %) "Package") temps))
          threads (mapv (fn [l] (rename-keys l {:value :load})) (remove #(= (:name %) "UC"     ) loads))]
      (-> (dissoc cpu :temps :loads)
          (assoc :temp  (some #(when (= "Package" (:name %)) %) temps)
                 :load  (some #(when (= "UC"      (:name %)) %) loads)
                 :cores (mapv #(assoc % :threads %2) cores (partition 2 threads)))))))

(defn xform-hdd [{:keys [name loads temps] :as hdd}]
  (-> (dissoc hdd :loads :temps)
      (assoc :volume (-> loads first :name) ;; can't even use textual name here, will break relying on index
             :used   (-> loads first :value)
             :free   (- 100 (-> loads first :value))
             :temp   (some #(when (= "Assembly" (:name %)) %) temps))))

(defn xform-keyboard [keyboard]
  (let [xform #(-> (rename-keys % {:zone :id}) ;; todo: start, stop colors
                   (dissoc :end_color)
                   (update :color rgb->hsl)
                   (assoc :name (str "Zone " (:zone %))))]
    {:name  "Keyboard"
     :zones (->> (dissoc keyboard :all) (sort first) (mapv (comp xform second)))}))

(defn xform-memory [{:keys [name free total] :as memory}] ;; no name available
  (-> (dissoc memory :total)
      (assoc :used (- total free) :name "Memory")))

(defn sensor-data [data]
  (let [init     #(subs % 0 (dec (count %)))
        type-seq (comp keyword init name)
        into-seq #(into % (mapv (fn [x] (assoc x :type (type-seq %2))) %3))
        conj-map #(conj % (assoc %3 :type %2))]
  (as-> data $
        (rename-keys $ {:led_keyboard :keyboard})
        (update $ :cpus     (partial mapv xform-cpu))
        (update $ :hdds     (partial mapv xform-hdd))
        (update $ :memory   xform-memory)
        (update $ :keyboard xform-keyboard)
        (reduce-kv #(if (sequential? %3) (into-seq %1 %2 %3) (conj-map %1 %2 %3)) [] $)
        (assoc {} :components $))))

(defn xform [data]
  (cond
    (:mb data) (sensor-data data)
    :else      data))