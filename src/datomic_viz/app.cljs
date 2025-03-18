(ns datomic-viz.app
  (:require [clojure.string :as str]
            [replicant.dom :as r]
            ["d3" :as d3]))

(def graph-width 928)
(def graph-height 600)

(defn bounded [[x y]]
  [(min (max (- (/ graph-width 2)) x) (/ graph-width 2))
   (min (max (- (/ graph-height 2)) y) (/ graph-height 2))])

(defonce state (atom {}))

(defn init! [{:keys [nodes edges root-id]}]
  (let [edge-array (clj->js edges)
        node-array (clj->js nodes)
        simulation (-> (d3/forceSimulation node-array)
                       (.alphaDecay 0.005)
                       (.force "link" (-> (d3/forceLink edge-array)
                                          (.id (fn [d] (.-id d)))
                                          (.distance 100)
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
                               (let [dom-node (js/document.getElementById (.-id node))
                                     [x y] (bounded [(.-x node) (.-y node)])]
                                 (.setAttribute dom-node "cx" x)
                                 (.setAttribute dom-node "cy" y)))
                             (doseq [edge edge-array]
                               (let [dom-node (js/document.getElementById (.-id edge))
                                     [x1 y1] (bounded [(.-x (.-source edge)) (.-y (.-source edge))])
                                     [x2 y2] (bounded [(.-x (.-target edge)) (.-y (.-target edge))])]
                                 (.setAttribute dom-node "x1" x1)
                                 (.setAttribute dom-node "y1" y1)
                                 (.setAttribute dom-node "x2" x2)
                                 (.setAttribute dom-node "y2" y2)))))
    (swap! state assoc
           :edges (into {} (map (fn [edge] [(.-id edge) edge]) edge-array))
           :nodes (into {} (map (fn [node] [(.-id node) node]) node-array))
           :simulation simulation)))

(def data
  [["Eve" :person/child "Cain"]
   ["Eve" :person/child "Seth"]
   ["Eve" :person/child "Abel"]
   ["Eve" :person/child "Awan"]
   ["Eve" :person/child "Azura"]
   ["Seth" :person/child "Enos"]
   ["Seth" :person/child "Noam"]
   ["Awan" :person/child "Enoch"]])

(defn datoms->graph-data [datoms]
  (let [entities (map (fn [id] {:id id}) (mapcat (juxt first last) datoms))]
    {:nodes   (set (map (fn [id] {:id id}) (mapcat (juxt first last) datoms)))
     :edges   (mapv (fn [[e a v]] {:id (str [e a v]) :source e :target v :attribute a})
                    data)
     :root-id (:id (first entities))}))

(defn mouse-position [event]
  (let [ctm (.getScreenCTM (js/document.getElementById "svg"))]
    [(/ (- (.-x event) (.-e ctm)) (.-a ctm))
     (/ (- (.-y event) (.-f ctm)) (.-d ctm))]))

(defn drag-start [event]
  (-> (:simulation @state)
      (.alphaTarget 0.3)
      (.restart))
  (let [dom-node (.-target event)
        node     (get-in @state [:nodes (.-id dom-node)])
        [x y] (mouse-position event)]
    (swap! state assoc :dragging (.-id dom-node))
    (set! (.-fx node) x)
    (set! (.-fy node) y)))

(defn drag [event]
  (when-let [dragging-id (:dragging @state)]
    (let [node (get-in @state [:nodes dragging-id])
          [x y] (mouse-position event)]
      (set! (.-fx node) x)
      (set! (.-fy node) y))))

(defn drag-end [_]
  (-> (:simulation @state)
      (.alphaTarget 0))
  (when-let [dragging-id (:dragging @state)]
    (let [node (get-in @state [:nodes dragging-id])]
      (swap! state dissoc :dragging)
      (set! (.-fx node) nil)
      (set! (.-fy node) nil))))

(defn hover-start [event]
  (let [this      (.-target event)
        text-node (js/document.getElementById (str (.-id this) "-text"))
        [x y] (mouse-position event)]
    (set! (.-display (.-style text-node)) "inline")
    (.setAttribute text-node "x" x)
    (.setAttribute text-node "y" y)
    (.setAttribute this "stroke" "#000")))

(defn hover-end [event]
  (let [this      (.-target event)
        text-node (js/document.getElementById (str (.-id this) "-text"))]
    (set! (.-display (.-style text-node)) "none")
    (.setAttribute this "stroke" "#fff")))

(defn force-directed-graph [{:keys [nodes edges root-id]}]
  (let [arrow-id "arrow"]
    [:svg#svg
     {:width   graph-width
      :height  graph-height
      :viewBox (str/join " " [(/ (- graph-width) 2) (/ (- graph-height) 2)
                              graph-width graph-height])
      :style   {:max-width "100%" :height "auto"}
      :on      {:mousemove  drag
                :mouseup    drag-end
                :mouseleave drag-end}}
     [:defs
      [:marker {:id           arrow-id
                :viewBox      "-10 -10 20 20"
                :refX         20
                :refY         0
                :markerWidth  20
                :markerHeight 20
                :orient       "auto"}
       [:path {:fill "#070707" :d "M0,-5L10,0L0,5"}]]]
     (for [{:keys [id]} edges]
       [:g {:stroke         "#999"
            :stroke-opacity 0.6}
        [:line {:id         id
                :stroke     "#000"
                :marker-end (str "url(#" arrow-id ")")}]])
     (for [{:keys [id]} nodes]
       [:g
        [:circle {:id           id
                  :stroke-width 1.5
                  :fill         (if (= id root-id)
                                  "#d11"
                                  "#aaa")
                  :stroke       "#fff"
                  :r            10
                  :draggable    true
                  :style        {:cursor "pointer"}
                  :on           {:mousedown drag-start
                                 :mousemove hover-start
                                 :mouseout  hover-end}}]
        [:text {:id    (str id "-text")
                :style {:user-select    "none"
                        :pointer-events "none"
                        :display        "none"}} id]])]))

(r/render js/document.body
          (let [data (datoms->graph-data data)]
            (init! data)
            [:div
             [:p "hello"]
             [:div#graph-container (force-directed-graph data)]]))