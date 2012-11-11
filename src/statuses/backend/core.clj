(ns statuses.backend.core
  (:require [clj-time.core :as time]))


(defn empty-db []
  ;; creates an empty in-memory DB for usage with the functions in this namespace
  {:next-id 0        ;; the next id that will be used for values with identity
   :posts {}         ;; the actual status updates, keyed by id
   :timeline []      ;; ids of status updates in order of posting
   :conversations {} ;; conversations, each as an ordered vector, keyed by id
   })

(defn get-update
  "returns update with id"
  [db id]
  (get-in db [:posts id]))

(defn- posts
  "Returns a seq of all status updates in order"
  [db]
  (map #(get (:posts db) %) (:timeline db)))

(defn get-latest
  "Retrieve the latest n status updates, optionally restricted to author, ordered by time"
  ([db n] (take n (posts db)))
  ([db n author] (take n (filter #(= author (:author %)) (posts db)))))

(defn- add-conversation
  "Returns db with conversation added. Safe to call with reply-to = nil."
  [db id reply-to]
  (if-let [r (get-update db reply-to)]
    (let [conv-id (or (:conversation r) (:next-id db))
          conv    (or (get-in db [:conversations conv-id]) [reply-to])]
      (-> db
          (assoc :next-id (inc (:next-id db)))
          (assoc-in [:conversations conv-id] (conj conv id))
          (assoc-in [:posts id :conversation] conv-id)
          (assoc-in [:posts id :in-reply-to] reply-to)
          (assoc-in [:posts reply-to :conversation] conv-id)))
    db))

(defn add-update
  "Returns db with new item added. Optionally handles replies & conversations."
  ([db author text]
     (let [id (:next-id db)]
       (-> db
           (assoc-in [:posts id] {:id id
                                  :time (time/now)
                                  :author author
                                  :text text})
           (assoc :timeline (cons id (:timeline db)))
           (assoc :next-id (inc id)))))
  ([db author text reply-to]
     (let [id (:next-id db)]
       (-> db
           (add-update author text)
           (add-conversation id reply-to)))))

(defn get-conversation
  "Returns a list of posts participating in conversation with id."
  [db id]
  (get-in db [:conversations id]))

(defn remove-update
  "Returns db with the update with id removed"
  [db id]
  (assoc db
    :posts (dissoc (:posts db) id)
    :timeline (remove #(= id %) (:timeline db))))

(defn- add-testdata [db n]
  "Create a DB with a set of n test updates"
  (letfn [
    (random [col] (nth col (rand-int (count col))))
    (text [] (random
              ["We know some foreign languages, too, so let's throw in an English text."
               "Gerade ein ganz tolles Programm in Clojure geschrieben, beeindruckend"
               "Hoffentlich sieht das hier nie irgend jemand."
               "Irgendwann wird auch mal jemand verstehen, was Phillip hier meint."
               "Was ist das? Sieht aus, als hätte das ein krankes Pferd geschrieben."
               "Ich mag Lorem Ipsum nicht und bevorzuge selbst gemachten Schwachsinn"
               "Eigentlich sollten das hier 10 sein, aber nach 7 verließ mich die Lust"]))
    (author [] (random ["abc" "xyz" "uiui" "klaus" "hein" "peer"]))]
    (reduce (fn [db [author text]] (add-update db author text))
            db
            (repeatedly n #(list (author) (text))))))

;; A bit of test data
(def dummy-db (atom (add-testdata (empty-db) 100)))
