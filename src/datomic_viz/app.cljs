(ns datomic-viz.app
  (:require [clojure.string :as str]
            [kitchen-async.promise :as p]
            [lambdaisland.fetch :as fetch]
            [replicant.dom :as r]
            [replicant.string :as rs]
            ["d3" :as d3]))

(def graph-width js/window.innerWidth)
(def graph-height (- js/window.innerHeight 40))
(def circle-radius 10)

(defn get-el [id] (js/document.getElementById id))

(defn query-params []
  (update-keys (->> (new js/URLSearchParams js/window.location.search)
                    (.entries)
                    (es6-iterator-seq)
                    (js->clj)
                    (into {}))
               keyword))

(defn set-query-params! [params]
  (let [thing (new js/URLSearchParams js/window.location.search)]
    (doseq [[k v] params]
      (.set thing
            (cond-> k (keyword? k) (name))
            v))
    (set! js/window.location.search (.toString thing))))

(defn get-input-value [^js element]
  (cond
    (= "number" (.-type element)) (when (not-empty (.-value element))
                                    (.-valueAsNumber element))
    :else (.-value element)))

(defn gather-form-params [^js form-el]
  (some-> (.-elements form-el)
          into-array
          (.reduce
           (fn [res ^js el]
             (let [k (some-> el .-name not-empty keyword)]
               (cond-> res
                 k (assoc k (get-input-value el)))))
           {})))

(defn coords->path [[x1 y1] [x2 y2]]
  (str/join " " ["M" x1 y1 "L" x2 y2]))

(defonce state (atom {}))

(defn init! [{:keys [nodes edges root-id]}]
  (when-let [sim (:simulation @state)]
    (.stop sim))
  (let [edge-array (clj->js (map #(update % :attribute str) edges))
        node-array (clj->js nodes)
        simulation (-> (d3/forceSimulation node-array)
                       (.alphaDecay 0.005)
                       (.force "link" (-> (d3/forceLink edge-array)
                                          (.id (fn [d] (.-id d)))
                                          (.distance (fn [^js d] (+ 30 (* 2 circle-radius) (* 8 (count (.-attribute d))))))
                                          (.strength 0.1)))
                       (.force "charge" (-> (d3/forceManyBody)
                                            (.strength -50)))
                       (.force "collide" (d3/forceCollide 10))
                       (.force "x" (-> (d3/forceX)
                                       (.strength (fn [d] (when (= root-id (.-id d)) 0.01)))))
                       (.force "y" (-> (d3/forceY)
                                       (.strength (fn [d] (when (= root-id (.-id d)) 0.01))))))]
    (.on simulation "tick" (fn []
                             (doseq [node node-array]
                               (let [dom-node (get-el (.-id node))
                                     [x y] [(.-x node) (.-y node)]]

                                 (.setAttribute dom-node "cx" x)
                                 (.setAttribute dom-node "cy" y)))
                             (doseq [edge edge-array]
                               (let [dom-edge       (get-el (.-id edge))
                                     rev-dom-edge   (get-el (str (.-id edge) "-reversed"))
                                     attribute-text (get-el (str (.-id edge) "-attribute"))
                                     [x1 y1] [(.-x (.-source edge)) (.-y (.-source edge))]
                                     [x2 y2] [(.-x (.-target edge)) (.-y (.-target edge))]
                                     reversed?      (< x2 x1)]
                                 (.setAttribute dom-edge "d" (coords->path [x1 y1] [x2 y2]))
                                 (.setAttribute rev-dom-edge "d" (coords->path [x2 y2] [x1 y1]))
                                 (.setAttribute attribute-text "href" (str "#" (cond-> (.-id edge)
                                                                                 reversed? (str "-reversed"))))))))
    (swap! state assoc
           :node-map (into {} (map (fn [node] [(.-id node) node]) node-array))
           :simulation simulation)))

(defn mouse-position [event]
  (let [ctm (.getScreenCTM (get-el "svg-graph"))]
    [(/ (- (.-x event) (.-e ctm)) (.-a ctm))
     (/ (- (.-y event) (.-f ctm)) (.-d ctm))]))

(defn mouse-movement [event]
  [(.-movementX event) (.-movementY event)])

(defn drag-start [event]
  (when-let [sim (:simulation @state)]
    (.restart (.alphaTarget sim 0.3)))
  (let [dom-node (.-target event)
        node     (get-in @state [:node-map (.-id dom-node)])
        [x y] (mouse-position event)]
    (swap! state assoc :dragging (.-id dom-node))
    (set! (.-fx node) x)
    (set! (.-fy node) y)))

(defn drag [event]
  (when-let [dragging-id (:dragging @state)]
    (if (= :all dragging-id)
      (let [[dx dy] (mouse-movement event)]
        (doseq [node (vals (:node-map @state))]
          (set! (.-fx node) (+ (.-fx node) dx))
          (set! (.-fy node) (+ (.-fy node) dy))))
      (let [[x y] (mouse-position event)]
        (let [node (get-in @state [:node-map dragging-id])]
          (set! (.-fx node) x)
          (set! (.-fy node) y))))))

(defn drag-end [_]
  (let [dragging-id (:dragging @state)]
    (when-let [sim (:simulation @state)]
      (.alphaTarget sim 0))
    (if (= :all dragging-id)
      (doseq [node (vals (:node-map @state))]
        (set! (.-fx node) nil)
        (set! (.-fy node) nil))
      (when-let [node (get-in @state [:node-map dragging-id])]
        (set! (.-fx node) nil)
        (set! (.-fy node) nil)))
    (swap! state dissoc :dragging)))

(defn drag-all-start [_]
  (when-not (:dragging @state)
    (let [nodes (vals (:node-map @state))]
      (swap! state assoc :dragging :all)
      (doseq [node nodes]
        (set! (.-fx node) (.-x node))
        (set! (.-fy node) (.-y node))))))

(defn hover-start [event]
  (let [this      (.-target event)
        text-node (get-el (str (.-id this) "-text"))
        [x y] (mouse-position event)]
    (set! (.. text-node -style -display) "block")
    (.setAttribute text-node "x" (+ x 20))
    (.setAttribute text-node "y" (+ y 20))
    (.setAttribute this "stroke" "black")))

(defn hover-end [reset-color event]
  (let [this      (.-target event)
        text-node (get-el (str (.-id this) "-text"))]
    (set! (.. text-node -style -display) "none")
    (.setAttribute this "stroke" reset-color)))

(defn double-click [event]
  (set-query-params! {:eid (.-id (.-target event))}))

(defn node->color [node]
  (str "#" (str/join (take 6 (.toString (abs (hash (keys node))) 16)))))

(defn force-directed-graph [{:keys [nodes edges root-id]}]
  (let [arrow-id "arrow"]
    [:svg#svg-graph
     {:width   graph-width
      :height  graph-height
      :viewBox (str/join " " [(/ (- graph-width) 2) (/ (- graph-height) 2)
                              graph-width graph-height])
      :style   {:max-width "100%" :height "auto"}
      :on      {:mousemove  drag
                :mousedown  drag-all-start
                :mouseup    drag-end
                :mouseleave drag-end}}
     [:defs
      [:marker {:id           arrow-id
                :viewBox      "-10 -10 20 20"
                :refX         17
                :refY         0
                :markerWidth  10
                :markerHeight 10
                :orient       "auto"}
       [:path {:stroke "black" :stroke-width 1.5 :fill "none" :d "M0,-5L10,0L0,5"}]]]
     (for [{:keys [id attribute]} edges]
       [:g
        [:path {:id           id
                :stroke       "black"
                :stroke-width 3
                :marker-end   (str "url(#" arrow-id ")")}]
        [:path {:id (str id "-reversed")}]
        [:text {:dy -5 :style {:user-select "none"
                               :font-size   12}}
         [:textPath {:id (str id "-attribute") :href (str "#" id) :startOffset (+ 20 circle-radius)}
          (str attribute)]]])
     (for [{:keys [id] :as node} nodes]
       (let [base-outline-color (if (= id root-id) "red" "white")]
         [:g
          [:circle {:id           id
                    :stroke-width 1.5
                    :fill         (node->color node)
                    :stroke       base-outline-color
                    :r            circle-radius
                    :draggable    true
                    :cursor       "pointer"
                    :on           {:mousedown drag-start
                                   :mousemove hover-start
                                   :mouseout  (partial hover-end base-outline-color)
                                   :dblclick  double-click}}]]))
     (for [{:keys [id] :as node} nodes]
       [:foreignObject {:id        (str id "-text")
                        :width     graph-width
                        :height    graph-height
                        :style     {:user-select    "none"
                                    :pointer-events "none"
                                    :display        "none"}
                        :innerHTML (rs/render
                                    [:div {:style {:background-color "white"
                                                   :outline          "solid black"
                                                   :width            "fit-content"}}
                                     [:table {:style {:padding 10}}
                                      (->> (dissoc node :id)
                                           (sort)
                                           (map (fn [[k v]] [:tr [:td k] [:td (with-out-str (prn v))]])))]])}])]))

(defn get-data [params]
  (swap! state dissoc :error)
  (p/let [{data :body :keys [status] :as req} (fetch/request "/data" {:method       :get
                                                                      :query-params params})]
    (case status
      500 (swap! state assoc :error data)
      (do
        (swap! state merge data)
        (init! data)))))

(defn render [data]
  (let [{:keys [eid ancestors descendants]} (query-params)]
    (r/render (get-el "app")
              [:div {:style {:font-family "Courier New"}}
               [:div {:style {:display         "flex"
                              :justify-content "space-between"}}
                [:form#form {:on {:submit (fn [e]
                                            (.preventDefault e)
                                            (let [params (gather-form-params (.-target e))]
                                              (set-query-params! params)
                                              (get-data params)))}}
                 [:input {:type          "text"
                          :name          "eid"
                          :style         {:font-family "inherit"}
                          :placeholder   "eid or lookup-ref"
                          :title         "leave empty for random"
                          :autocomplete  "off"
                          :default-value eid}]
                 [:input {:type          "number"
                          :name          "ancestors"
                          :style         {:font-family "inherit"}
                          :title         "number of ancestors"
                          :autocomplete  "off"
                          :min           0
                          :default-value (or ancestors 1)}]
                 [:input {:type          "number"
                          :name          "descendants"
                          :style         {:font-family "inherit"}
                          :title         "number of descendants"
                          :autocomplete  "off"
                          :min           0
                          :default-value (or descendants 1)}]
                 [:button {:type  "submit"
                           :style {:font-family "inherit"}}
                  "Go!"]]
                (when (:nodes data)
                  [:div {:style {:padding 2}}
                   [:span {:style {:padding 10}} (str "Max edges per level: " (:max-edges-per-level data))]
                   [:span {:style {:padding 10}} (str "Nodes: " (count (:nodes data)))]
                   [:span {:style {:padding 10}} (str "Edges: " (count (:edges data)))]])]
               (when (:error data)
                 [:p {:style {:color "red"}} (:error data)])
               [:div#graph-container (force-directed-graph data)]])))

(add-watch state :render (fn [_ _ _ data] (render data)))

(get-data (query-params))

