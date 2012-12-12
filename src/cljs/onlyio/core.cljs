(ns onlyio.core
  (:use-macros [webfui.framework.macros :only [add-dom-watch]])
  (:require [webfui.framework :as fui]
            [clojure.browser.dom :as dom]
            [clojure.browser.repl :as repl]
            [clojure.walk :as walk]
            [jayq.core :as $]))

(repl/connect "http://localhost:9000/repl")

(def tweet-length 140)

(def wordnik-base "http://api.wordnik.com//v4/words.json/search/")

(def google-auto "http://clients5.google.com/complete/search?hl=en&client=chrome&q=")

(def initial-state
  {:tweet ""
   :search {:query "" :completes []}})

(def state (atom initial-state))

(defn starts-with? [s p]
  (= 0 (.indexOf s p)))

(defn res-map [res]
  (walk/keywordize-keys (js->clj res)))

(defn format-completes [res]
  (let [strings (nth res 1)
        types (get-in res [4 :google:suggesttype])
        relevance (get-in res [4 :google:suggestrelevance])]
    (into [] (for [i (range 0 (count strings))]
      {:string (nth strings i)
       :type (nth types i)
       :rel (nth relevance i)}))))

(defn autocomplete [q cb]
  ($/ajax (str google-auto q)
          {:dataType "jsonp"
           :success (fn [res] (cb (format-completes (res-map res))))}))

(defn get-word [q cb]
  ($/ajax (str wordnik-base q ".*?allowRegex=true&api_key=997ea3a64b190c6d8f0040df7b6003393e51bbd224bc5ec8d")
          {:dataType "jsonp"
           :success (fn [res]
                      (cb
                       (when-let [word (get-in (res-map res) [:searchResults 1 :word])]
                         word)))}))

(add-dom-watch :tweet-watch [s new]
               (let [{:keys [value]} (second new)]
                 (when (< (count value) (inc tweet-length))
                   {:tweet value})))

(add-dom-watch :input-watch [s new]
               (let [{:keys [value]} (second new)
                     patch {:search {:query value}}
                     query (-> s :search :query)
                     completes (-> s :search :completes)
                     closest (first completes)]
                 (dom/log closest value)
                 (if (and closest
                          (starts-with? value (:string closest))
                          (< query value))
                   patch
                   (if (empty? value)
                     (assoc-in patch [:search :completes] [])
                     (do
                       (autocomplete value
                         (fn [completes]
                           (when (= value (-> @state :search :query))
                             (swap! state assoc-in [:search :completes] completes))))
                       patch)))))
                 
(defn auto-result [complete]
  [:div.complete (:string complete)])

(defn first-result [search]
  (if-let [complete (first
                     (drop-while
                      #(not (starts-with? (:string %) (:query search)))
                      (:completes search)))]
    (:string complete)
    ""))

(defn render [s]
  (let [{:keys [search]} s]
    [:div#container
     [:div#input-wrapper
      [:input#input {:watch :input-watch
                     :value (:query search)
                     :autofocus true
                     :placeholder "Search..."}]
      [:input#input-back {:value (first-result search)
                          :disabled true}]]
     [:div#results (map auto-result (:completes search))]]))
  
(fui/launch-app state render)