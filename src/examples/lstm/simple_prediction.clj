(ns examples.lstm.simple-prediction
  (:require
    [clojure.pprint :refer [pprint]]
    [clj-time.local  :as l]
    [prism.nn.lstm :as lstm]
    [matrix.default :refer [default-matrix-kit]]))

(defn train-sgd [model training-list learning-rate]
  (loop [model model,
         training-list training-list,
         n 0,
         acc-loss 0]
    (if-let [training-pair (first training-list)]
      (let [{x-seq :x y-seq :y} training-pair
            forward (lstm/sequential-output model x-seq (map #(if (= :skip %) :skip (keys %)) y-seq))
            {:keys [loss param-loss]} (lstm/bptt model
                                                 forward
                                                 y-seq)
            diff (get (last loss) "prediction") ; last time loss
            loss (* diff diff 0.5)] ; sum-of-squares-error
        (recur (lstm/update-model! model param-loss learning-rate)
               (rest training-list)
               (inc n)
               (+ acc-loss loss)))
      {:loss (/ acc-loss n) :model model})))


(defn train-with-demo-dataset [model training-list & [option]]
  (let [{:keys [optimizer learning-rate epoc loss-interval label label-interval]} option]
    (loop [model model, e 0]
      (if (< e epoc)
        (let [{loss :loss updated-model :model} (train-sgd model (shuffle training-list) learning-rate)]
          (when (= 0 (rem e loss-interval))
            (println (str "["(l/format-local-time (l/local-now) :basic-date-time-no-ms)"] epoc: " e
                          ", optimizer: SGD"
                          ", learning-rate: " learning-rate ", error: " loss)))
          (recur updated-model  (inc e)))
        model))))

;;;; ;;;; ;;;; ;;;; ;;;; ;;;; ;;;;

(defn dataset
  [{:keys [make-vector] :as matrix-kit}]
  [
    {:x (map make-vector [[1]])                 :y [{"prediction" 10}]}
    {:x (map make-vector [[1] [1] [1]])         :y [{"prediction" 10} {"prediction" 20} {"prediction" 30}]}
    {:x (map make-vector [[1] [2]])             :y [{"prediction" 10} {"prediction" 40}]}
    {:x (map make-vector [[1] [1] [1] [1] [1]]) :y [:skip :skip {"prediction" 30} :skip {"prediction" 50}]}
    ])


(defn demo
  "captures demo model with 2 lstm units"
  [matrix-kit]
  (let [mk (or matrix-kit default-matrix-kit)
        {:keys [make-vector]} mk
        model (train-with-demo-dataset (lstm/init-model {:input-items  nil
                                                         :output-items #{"prediction"}
                                                         :input-type :dense
                                                         :input-size 1
                                                         :hidden-size 2
                                                         :output-type :prediction
                                                         :matrix-kit mk})
                                       (dataset mk)
                                       {:loss-interval 200
                                        :epoc 2000
                                        :learning-rate 0.01})
        demo-input1 (map make-vector [[1]])
        demo-input2 (map make-vector [[1] [1] [1]])
        demo-input3 (map make-vector [[1] [1] [1] [1] [1]])]
    (println "*** dataset ***")
    (pprint dataset)
    (println "\n*** demo1 ***")
    (pprint demo-input1)
    (pprint (last (:activation (last (lstm/sequential-output model demo-input1 [#{"prediction"}])))))
    (println "\n*** demo2 ***")
    (pprint demo-input2)
    (pprint (last (:activation (last (lstm/sequential-output model demo-input2 [:skip :skip #{"prediction"}])))))
    (println "\n*** demo3 ***")
    (pprint demo-input3)
    (pprint (last (:activation (last (lstm/sequential-output model demo-input3 [:skip :skip :skip :skip #{"prediction"}])))))
    (println)))


(defn -main []
  (demo nil))
