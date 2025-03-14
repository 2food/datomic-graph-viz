(ns datomic-viz.app
  (:require [replicant.dom :as r]
            ["d3" :as d3]))

(def graph-width 928)
(def graph-height 600)

(defn attrs [thing attr-map]
  (reduce (fn [t [attr val]] (.attr t attr val)) thing attr-map))

(defn container-svg [width height]
  (-> (d3/create "svg")
      (attrs {"width"   width
              "height"  height
              "viewBox" (clj->js [(/ (- width) 2) (/ (- height) 2)
                                  width height])
              "style"   "max-width: 100%; height: auto;"})))

(defn drag [simulation]
  (letfn [(dragstarted [event d]
            (when (= 0 (.-active event))
              (.restart (.alphaTarget simulation 0.3)))
            (set! (.-fx d) (.-x d))
            (set! (.-fy d) (.-y d)))
          (dragged [event d]
            (set! (.-fx d) (.-x event))
            (set! (.-fy d) (.-y event)))
          (dragended [event d]
            (when (= 0 (.-active event))
              (.alphaTarget simulation 0))
            (set! (.-fx d) nil)
            (set! (.-fy d) nil))]
    (-> (d3/drag)
        (.on "start" dragstarted)
        (.on "drag" dragged)
        (.on "end" dragended))))


(defn with-arrow-marker [svg]
  (-> svg
      (.append "defs")
      (.selectAll "marker")
      (.data (clj->js ["end"]))
      (.enter)
      (.append "marker")
      (attrs {"id"           identity
              "viewBox"      "0 0 10 10"
              "refX"         20
              "refY"         5
              "markerWidth"  6
              "markerHeight" 6
              "orient"       "auto"})
      (.append "path")
      (attrs {"fill" "#000"
              "d"    "M 0 0 L 10 5 L 0 10 z"})))

(defn render-graph [nodes links root-id]
  (let [svg        (container-svg graph-width graph-height)
        _          (with-arrow-marker svg)
        #_#__ (js/console.log root)
        simulation (-> (d3/forceSimulation nodes)
                       (.force "link" (-> (d3/forceLink links)
                                          (.id (fn [d] (.-id d)))
                                          (.distance 0)
                                          (.strength 0.1)))
                       (.force "charge" (-> (d3/forceManyBody)
                                            (.strength -50)))
                       (.force "x" (-> (d3/forceX)
                                       (.strength (fn [d] (if (= root-id (.-id d)) 0.1 0.01)))))
                       (.force "y" (-> (d3/forceY)
                                       (.strength (fn [d] (if (= root-id (.-id d)) 0.1 0.01))))))
        link       (-> svg
                       (.append "g")
                       (.attr "stroke", "#999")
                       (.attr "stroke-opacity", 0.6)
                       (.selectAll "line")
                       (.data links)
                       (.join "line")
                       (attrs {"stroke"     "#000"
                               "marker-end" (str "url(" (new js/URL "#end" js/location) ")")}))
        node       (-> svg
                       (.append "g")
                       (.attr "fill" "#fff")
                       (.attr "stroke" "#000")
                       (.attr "stroke-width" 1.5)
                       (.selectAll "circle")
                       (.data nodes)
                       (.join "circle")
                       (.attr "fill" (fn [d] (cond (= root-id (.-id d)) "#d11"
                                                   (not (.-children d)) "#000")))
                       (.attr "stroke" (fn [d] (when-not (.-children d) "#fff")))
                       (.attr "r" 5)
                       (.call (drag simulation)))]
    (-> (.append node "text")
        (.attr "x" 8)
        (.attr "y" "0.31em")
        (.text (fn [d] (.-id d))))
    (.on simulation "tick" (fn []
                             (-> link
                                 (.attr "x1" (fn [d] (.-x (.-source d))))
                                 (.attr "y1" (fn [d] (.-y (.-source d))))
                                 (.attr "x2" (fn [d] (.-x (.-target d))))
                                 (.attr "y2" (fn [d] (.-y (.-target d)))))
                             (-> node
                                 (.attr "cx" (fn [d] (.-x d)))
                                 (.attr "cy" (fn [d] (.-y d))))))
    (.node svg)))

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
     :links   (mapv (fn [[e a v]] {:source e :target v :attribute a})
                    data)
     :root-id (:id (first entities))}))

(defn init-graph [node data]
  (let [{:keys [nodes links root-id]} (datoms->graph-data data)]
    (.replaceChildren node (render-graph (clj->js nodes) (clj->js links) root-id))))

(r/render js/document.body
          [:div
           [:p "hello"]
           [:div#graph-container
            {:replicant/on-mount
             (fn [{:keys [replicant/node]}]
               (init-graph node data))}]])