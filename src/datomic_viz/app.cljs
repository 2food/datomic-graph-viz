(ns datomic-viz.app
  (:require [clojure.pprint :as pprint]
            [clojure.string :as str]
            [kitchen-async.promise :as p]
            [replicant.dom :as r]
            [lambdaisland.fetch :as fetch]
            ["d3" :as d3]))

(def graph-width js/window.screen.width)
(def graph-height (* js/window.screen.height 0.75))

(defn get-el [id] (js/document.getElementById id))

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
                               (let [dom-node (get-el (.-id node))
                                     [x y] (bounded [(.-x node) (.-y node)])]
                                 (.setAttribute dom-node "cx" x)
                                 (.setAttribute dom-node "cy" y)))
                             (doseq [edge edge-array]
                               (let [dom-node (get-el (.-id edge))
                                     [x1 y1] (bounded [(.-x (.-source edge)) (.-y (.-source edge))])
                                     [x2 y2] (bounded [(.-x (.-target edge)) (.-y (.-target edge))])]
                                 (.setAttribute dom-node "x1" x1)
                                 (.setAttribute dom-node "y1" y1)
                                 (.setAttribute dom-node "x2" x2)
                                 (.setAttribute dom-node "y2" y2)))))
    (swap! state assoc
           :node-array (into {} (map (fn [node] [(.-id node) node]) node-array))
           :simulation simulation)))

(defn mouse-position [event]
  (let [ctm (.getScreenCTM (get-el "svg-graph"))]
    [(/ (- (.-x event) (.-e ctm)) (.-a ctm))
     (/ (- (.-y event) (.-f ctm)) (.-d ctm))]))

(defn drag-start [event]
  (.restart (.alphaTarget (:simulation @state) 0.3))
  (let [dom-node (.-target event)
        node     (get-in @state [:node-array (.-id dom-node)])
        [x y] (mouse-position event)]
    (swap! state assoc :dragging (.-id dom-node))
    (set! (.-fx node) x)
    (set! (.-fy node) y)))

(defn drag [event]
  (when-let [dragging-id (:dragging @state)]
    (let [node (get-in @state [:node-array dragging-id])
          [x y] (mouse-position event)]
      (set! (.-fx node) x)
      (set! (.-fy node) y))))

(defn drag-end [_]
  (.alphaTarget (:simulation @state) 0)
  (when-let [dragging-id (:dragging @state)]
    (let [node (get-in @state [:node-array dragging-id])]
      (swap! state dissoc :dragging)
      (set! (.-fx node) nil)
      (set! (.-fy node) nil))))

(defn hover-start [event]
  (let [this      (.-target event)
        text-node (get-el (str (.-id this) "-text"))
        [x y] (mouse-position event)]
    (set! (.. text-node -style -display) "block")
    (.setAttribute text-node "x" (+ x 20))
    (.setAttribute text-node "y" (+ y 20))
    (.setAttribute this "stroke" "#000")))

(defn hover-end [event]
  (let [this      (.-target event)
        text-node (get-el (str (.-id this) "-text"))]
    (set! (.. text-node -style -display) "none")
    (.setAttribute this "stroke" "#fff")))

(defn force-directed-graph [{:keys [nodes edges root-id]}]
  (let [arrow-id "arrow"]
    [:svg#svg-graph
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
                  :cursor       "pointer"
                  :on           {:mousedown drag-start
                                 :mousemove hover-start
                                 :mouseout  hover-end}}]])
     (for [{:keys [id] :as node} nodes]
       [:foreignObject {:id        (str id "-text")
                        :width     400
                        :height    300
                        :style     {:user-select    "none"
                                    :pointer-events "none"
                                    :display        "none"}
                        :innerHTML (str "<div>" (str/join "<br/>" (sort (map (fn [[k v]] (str k " " v)) (dissoc node :id)))) "</div>")}])]))

(comment
  (pprint/write {:this    :a
                 :that    :b
                 :another :c}
                :pretty true)
  )

(defn render [data]
  (r/render (get-el "app")
            [:div
             [:p "hello"]
             [:div#graph-container (force-directed-graph data)]]))

(add-watch state :render (fn [_ _ _ data] (render data)))

(p/let [{data :body} (fetch/request "/data" {:method :get})]
  (swap! state merge data)
  (init! data))
