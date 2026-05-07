(ns lite-crm.companies.import
  "CSV parsing and import logic for companies."
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [jsonista.core :as json]
            [lite-crm.companies.queries :as queries]))

(def valid-tiers #{"has_plan" "has_need" "no_plan" "abandoned"})

(defn parse-csv-stream
  "Parse a CSV InputStream. Returns {:headers [...] :rows [[...] ...]}."
  [input-stream]
  (with-open [reader (io/reader input-stream :encoding "UTF-8")]
    (let [rows (doall (csv/read-csv reader))]
      {:headers (first rows)
       :rows    (rest rows)})))

(defn map-row
  "Apply column-index mapping to a raw row vector."
  [_headers row {:keys [name-col industry-col tier-col]}]
  (let [idx    #(when % (let [i (if (string? %) (parse-long %) %)]
                          (when (< i (count row)) (nth row i))))
        name-v (str/trim (or (idx name-col) ""))
        tier-v (str/trim (or (idx tier-col) ""))]
    (when (seq name-v)
      {:name     name-v
       :industry (some-> (idx industry-col) str/trim not-empty)
       :tier     (if (valid-tiers tier-v) tier-v "no_plan")})))

(defn rows->company-maps
  "Convert raw CSV rows to company maps using header-index mapping."
  [headers rows mapping]
  (->> rows
       (keep #(map-row headers % mapping))
       (distinct)))

(defn do-import!
  "Insert company-maps, skipping exact name duplicates.
   Returns {:inserted N :skipped M}."
  [db company-maps]
  (reduce (fn [{:keys [inserted skipped]} co]
            (if (queries/get-company-by-name db (:name co))
              {:inserted inserted :skipped (inc skipped)}
              (do (queries/create-company! db co)
                  {:inserted (inc inserted) :skipped skipped})))
          {:inserted 0 :skipped 0}
          company-maps))

(defn rows-json->company-maps
  "Decode a JSON string of [{:name ... :industry ... :tier ...}] maps."
  [rows-json]
  (json/read-value rows-json (json/object-mapper {:decode-key-fn keyword})))
