(ns guardian.dashboard.transformations
  (:require
    [clojure.set :refer [rename-keys]]))

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
      (assoc :volume (-> loads first :name)) ;; can't even use textual name here, will break relying on index
             :used   (-> loads first :value))
             :temp   (some #(when (= "Assembly" (:name %)) %) temps))

(defn monitor-data [data]
  (let [init     #(subs % 0 (dec (count %)))
        type-seq (comp keyword init name)
        into-seq #(into % (mapv (fn [x] (assoc x :type (type-seq %2))) %3))
        conj-map #(conj % (assoc %3 :type %2))]
  (->> (update data :cpus (partial mapv xform-cpu))
       (update data :hdds (partial mapv xform-hdd))
       (reduce-kv #(if (sequential? %3) (into-seq %1 %2 %3) (conj-map %1 %2 %3)) [])
       (assoc {} :components))))

(defn xform [data]
  (cond
    (:mb data) (monitor-data data)
    :else      data))
