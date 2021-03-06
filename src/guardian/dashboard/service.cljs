(ns guardian.dashboard.service
  (:require
    [javelin.core :refer [with-let cell cell=]]
    [clojure.set  :refer [rename-keys]]
    [cljsjs.d3]))

;;; config ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def effects
  {:color    ["Fixed Color"     "static_color"]
   :cpu-load ["CPU Load"        "cpu_load"]
   :cpu-temp ["CPU Temperature" "cpu_temp"]
   :gpu-load ["GPU Load"        "gpu_load"]
   :gpu-temp ["GPU Temperature" "gpu_temp"]})

(def sensors
  {:cpu-power         "Package"
   :cpu-temp          "Package"
   :cpu-load          "UC"
   :gpu-processor     "GPU"
   :gpu-memory        "Memory"
   :gpu-frame-buffer  "Frame Buffer"
   :gpu-video-engine  "Video Engine"
   :gpu-bus-interface "Bus Interface"
   :hdd-temp          "Assembly"
   :zone-1            "THRM"
   :zone-2            "TZ00"
   :zone-3            "TZ01"})

;;; utils ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn hsl->rgb [[h s l]]
  (when (and h s l)
    (let [c (.rgb js/d3 (.hsl js/d3 h s l))]
      (mapv js/parseInt [(.-r c) (.-g c) (.-b c)]))))

(defn rgb->hsl [[r g b]]
  (when (and r g b)
    (let [c (.hsl js/d3 (.rgb js/d3 r g b))]
      (mapv js/parseFloat [(.-h c) (.-s c) (.-l c)]))))

(defn name->sensor [reading]
  (-> (rename-keys reading {:name :sensor})
      (update :value int)))

(defn get-sensor [coll sensor]
 (-> (some #(when (= (sensor sensors) (:name %)) %) coll)
     (name->sensor)))

;;; xforms ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn cpu [{:keys [name clocks loads temps volts watts]}]
  (let [load    (get-sensor loads :cpu-load)
        temp    (get-sensor temps :cpu-temp)
        loads*  (remove (partial = load) loads)
        temps*  (remove (partial = temp) temps)
        threads (mapv #(hash-map :name (:name %) :load (name->sensor %1)) loads*)
        cores   (mapv #(hash-map :name (:name %) :freq (name->sensor %1) :temp (name->sensor %2)) clocks temps*)]
    {:name  name
     :type  :cpu
     :temp  temp
     :load  load
     :cores (mapv #(assoc % :threads %2) cores (partition 2 threads))}))

(defn hard-drive [{:keys [name loads temps] :as hdd}]
  {:name    name
   :type    :hard-drive
   :temp    (hash-map :value (apply + (mapv :value temps)))
   :load    (hash-map :value (apply + (mapv :value loads)))
   :volumes (mapv #(hash-map :name (:name %1) :load (name->sensor %1) :temp (name->sensor %2)) loads temps)})

(defn graphics-card [{:keys [name loads temps]}]
  (let [card {:name name :type :graphics-card}]
    (if (seq loads)
      (assoc card
        :name          name
        :type          :graphics-card
        :integrated?   false
        :gpu           {:name "GPU"
                        :temp (get-sensor temps :gpu-processor)
                        :load (get-sensor loads :gpu-processor)}
        :memory        {:name "Memory"
                        :load (get-sensor loads :gpu-memory)}
        :frame-buffer  {:name "Frame Buffer"
                        :load (get-sensor loads :gpu-frame-buffer)}
        :video-engine  {:name "Video Engine"
                        :temp (get-sensor loads :gpu-video-engine)}
        :bus-interface {:name "Bus Interface"
                        :temp (get-sensor loads :gpu-bus-interface)})
      (assoc card :integrated? true))))

(defn keyboard [keyboard]
  (let [zone #(hash-map
                :id        (->> % :zone js/parseInt)
                :name      (->> % :zone (str "Zone "))
                :effect    (some (fn [[ k [_ n]]] (when (= n (:name %)) k)) effects)
                :color     (-> % :color     rgb->hsl)
                :beg-color (-> % :beg_color rgb->hsl)
                :end-color (-> % :end_color rgb->hsl))
        zones (->> (dissoc keyboard :all)
                   (sort-by first)
                   (mapv (comp zone second)))]
    {:name  "Keyboard"
     :type  :kb
     :zones zones}))

(defn memory [{:keys [name free total] :as memory}]
  {:name  name
   :type  :memory
   :used  {:value (- total free)}
   :total {:value total}})

(defn motherboard [{{:keys [name temps]} :mb mem :memory kb :led_keyboard :keys [cpus gpus hdds]}]
  {:name           name
   :type           :mb
   :zone-1         {:name "CPU Thermal Zone"
                    :desc "Located next to the CPU core on Intel boards"
                    :temp (get-sensor temps :zone-1)}
   :zone-2         {:name "North Bridge Thermal Zone"
                    :desc "Located next to the CPU socket on Intel boards"
                    :temp (get-sensor temps :zone-2)}
   :zone-3         {:name "South Bridge Thermal Zone"
                    :desc "Located next to the memory slots on Intel boards"
                    :temp (get-sensor temps :zone-3)}
   :gpu             {:name (-> gpus first :name)}
   :memory         (memory mem)
   :keyboard       (keyboard kb)
   :cpus           (mapv cpu cpus)
   :graphics-cards (into [] (sort-by :integrated? (mapv graphics-card gpus)))
   :hard-drives    (into [] (sort-by (comp :name first :volumes) (mapv hard-drive hdds)))})

(defn device-data [data]
  (prn :device-data data)
  data)

;;; api ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn connect [url]
  (let [conn (cell (js/WebSocket. url))]
    (cell= (set! (.-onclose conn) ~(fn [_] (reset! conn (js/WebSocket. url)))))
    (-> (fn [resolve reject]
          (set! (.-onopen  @conn) #(resolve conn))
          (set! (.-onerror @conn) #(reject %)))
        (js/Promise.))))

(defn- call [tag conn & kwargs]
  (let [data (apply hash-map kwargs)]
    (->> {:tag tag :data data} (clj->js) (.stringify js/JSON) (.send @conn))))

(defn bind-sensors! [conn state error & [poll-freq hist-max]]
  (let [cljs #(js->clj % :keywordize-keys true)
        parse #(-> % .-data js/JSON.parse cljs)]
    (with-let [_ conn]
      (cell= (set! (.-onmessage conn) ~(fn [e] (let [d (parse e)] (when (= (:tag d) "sensors") (reset! state (motherboard (:data d))))))))
      (cell= (set! (.-onerror   conn) ~(fn [e] (reset! error e))))
      (call "get_sensors" conn))))

(defn get-devices [conn]
  (device-data (call "get_devices" conn)))

(defn set-keyboard-zone!
  ([conn zone color]
   (set-keyboard-zone! conn zone :color color nil nil))
  ([conn zone effect beg-color end-color]
   (set-keyboard-zone! conn zone effect nil beg-color end-color))
  ([conn zone effect color beg-color end-color]
   (call "set_keyboard_zones" conn :zone (str zone) :name (-> effect effects second) :color (hsl->rgb color) :beg_color (hsl->rgb beg-color) :end_color (hsl->rgb end-color))))

